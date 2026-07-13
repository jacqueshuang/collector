package com.j.soul.yc.captcha.aliyun;

import com.j.soul.yc.captcha.CaptchaProvider;
import com.j.soul.yc.captcha.CaptchaResult;
import com.j.soul.yc.config.YcClientConfig;
import com.j.soul.yc.exception.YcException;
import com.j.soul.yc.exception.YcStep;
import com.j.soul.yc.http.HttpTransport;
import com.j.soul.yc.http.OkHttpTransport;

import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Orchestrates Aliyun captcha with concurrent, isolated solves.
 * Default engine is HtmlUnit ({@link CaptchaHtmlUnitRuntime}); set
 * {@code captchaEngine=graal} for {@link CaptchaJsRuntime}.
 * <p>
 * HtmlUnit/Graal sessions are <strong>not</strong> shared across threads.
 * Each {@link #getCaptchaVerifyParam()} opens a private session (bounded by
 * {@link YcClientConfig#captchaConcurrency()}).
 */
public final class AliyunCaptchaProvider implements CaptchaProvider, AutoCloseable {

    private final YcClientConfig config;
    private final AliyunCaptchaConfig captchaConfig;
    private final HttpTransport transport;
    private final boolean ownTransport;
    private final Semaphore permits;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** Optional test override: if set, used instead of real HtmlUnit/Graal open. */
    private final Supplier<CaptchaSolveResult> solveOverride;

    public AliyunCaptchaProvider(YcClientConfig config, HttpTransport transport) {
        this(config, transport, null);
    }

    /** Package-visible for concurrency tests without launching browsers. */
    AliyunCaptchaProvider(YcClientConfig config, HttpTransport transport, Supplier<CaptchaSolveResult> solveOverride) {
        this.config = Objects.requireNonNull(config, "config");
        this.captchaConfig = AliyunCaptchaConfig.from(config);
        if (transport != null) {
            this.transport = transport;
            this.ownTransport = false;
        } else {
            this.transport = new OkHttpTransport(config);
            this.ownTransport = true;
        }
        this.permits = new Semaphore(Math.max(1, config.captchaConcurrency()), true);
        this.solveOverride = solveOverride;
    }

    public AliyunCaptchaConfig captchaConfig() {
        return captchaConfig;
    }

    public HttpTransport transport() {
        return transport;
    }

    /** Available concurrent captcha slots (approximate). */
    public int availablePermits() {
        return permits.availablePermits();
    }

    @Override
    public CaptchaResult getCaptchaVerifyParam() {
        ensureOpen();
        boolean acquired = false;
        try {
            long timeoutMs = Math.max(0L, config.captchaAcquireTimeout().toMillis());
            acquired = permits.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new YcException(YcStep.CAPTCHA,
                        "captcha concurrency saturated (max=" + config.captchaConcurrency()
                                + ", wait=" + config.captchaAcquireTimeout() + ")");
            }
            ensureOpen();
            CaptchaSolveResult solved = solveOnce();
            return mapSolveResult(solved, captchaConfig.sceneId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new YcException(YcStep.CAPTCHA, "interrupted waiting for captcha slot", e);
        } catch (YcException e) {
            throw e;
        } catch (Exception e) {
            throw new YcException(YcStep.CAPTCHA, "getCaptchaVerifyParam failed: " + e.getMessage(), e);
        } finally {
            if (acquired) {
                permits.release();
            }
        }
    }

    private CaptchaSolveResult solveOnce() {
        if (solveOverride != null) {
            return solveOverride.get();
        }
        if (useGraal()) {
            try (CaptchaJsRuntime runtime = CaptchaJsRuntime.open(config, transport)) {
                return runtime.solveSlider();
            }
        }
        try (CaptchaHtmlUnitRuntime runtime = CaptchaHtmlUnitRuntime.open(config, transport)) {
            return runtime.solveSlider();
        }
    }

    /**
     * Map SDK solve material to business {@link CaptchaResult}.
     * Requires non-blank securityToken; rejects explicit {@code verifyResult=false}.
     */
    static CaptchaResult mapSolveResult(CaptchaSolveResult solve, String sceneId) {
        if (solve == null) {
            throw new YcException(YcStep.CAPTCHA, "captcha solve returned null");
        }
        String certifyId = solve.certifyId();
        String securityToken = solve.securityToken();
        if (certifyId == null || certifyId.isBlank()) {
            throw new YcException(YcStep.CAPTCHA, "captcha solve missing certifyId");
        }
        if (securityToken == null || securityToken.isBlank()) {
            throw new YcException(YcStep.CAPTCHA,
                    "captcha solve missing securityToken (Verify may have failed) certifyId=" + certifyId);
        }
        if (Boolean.FALSE.equals(solve.verifyResult())) {
            throw new YcException(YcStep.CAPTCHA,
                    "captcha VerifyResult=false certifyId=" + certifyId);
        }
        String param = CaptchaVerifyParamBuilder.build(certifyId, sceneId, securityToken);
        return new CaptchaResult(param, sceneId, certifyId, securityToken);
    }

    private boolean useGraal() {
        String engine = config.captchaEngine();
        return engine != null && "graal".equalsIgnoreCase(engine.trim());
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new YcException(YcStep.CAPTCHA, "AliyunCaptchaProvider closed");
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (ownTransport) {
            try {
                transport.close();
            } catch (Exception ignored) {
            }
        }
    }
}
