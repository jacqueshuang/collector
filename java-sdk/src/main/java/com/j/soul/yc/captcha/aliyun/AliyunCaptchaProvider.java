package com.j.soul.yc.captcha.aliyun;

import com.j.soul.yc.captcha.CaptchaProvider;
import com.j.soul.yc.captcha.CaptchaResult;
import com.j.soul.yc.config.YcClientConfig;
import com.j.soul.yc.exception.YcException;
import com.j.soul.yc.exception.YcStep;
import com.j.soul.yc.http.HttpTransport;
import com.j.soul.yc.http.OkHttpTransport;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestrates Aliyun captcha via in-process GraalJS ({@link CaptchaJsRuntime#solveSlider()}).
 * One shared runtime + transport per provider instance (Init → drag → Verify inside JS).
 */
public final class AliyunCaptchaProvider implements CaptchaProvider, AutoCloseable {

    private final YcClientConfig config;
    private final AliyunCaptchaConfig captchaConfig;
    private final HttpTransport transport;
    private final boolean ownTransport;
    private final Object lock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private CaptchaJsRuntime runtime;
    private boolean ownRuntime;

    public AliyunCaptchaProvider(YcClientConfig config, HttpTransport transport) {
        this.config = Objects.requireNonNull(config, "config");
        this.captchaConfig = AliyunCaptchaConfig.from(config);
        if (transport != null) {
            this.transport = transport;
            this.ownTransport = false;
        } else {
            this.transport = new OkHttpTransport(config);
            this.ownTransport = true;
        }
    }

    /** Package-visible for tests: inject shared runtime (caller owns lifecycle). */
    AliyunCaptchaProvider(YcClientConfig config, HttpTransport transport, CaptchaJsRuntime sharedRuntime) {
        this(config, transport);
        this.runtime = sharedRuntime;
        this.ownRuntime = false;
    }

    public AliyunCaptchaConfig captchaConfig() {
        return captchaConfig;
    }

    /** Shared transport used by this provider / runtime HTTP bridge. */
    public HttpTransport transport() {
        return transport;
    }

    @Override
    public CaptchaResult getCaptchaVerifyParam() {
        ensureOpen();
        try {
            CaptchaSolveResult solved;
            synchronized (lock) {
                solved = ensureRuntime().solveSlider();
            }
            return mapSolveResult(solved, captchaConfig.sceneId());
        } catch (YcException e) {
            throw e;
        } catch (Exception e) {
            throw new YcException(YcStep.CAPTCHA, "getCaptchaVerifyParam failed: " + e.getMessage(), e);
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

    private CaptchaJsRuntime ensureRuntime() {
        if (runtime == null) {
            runtime = CaptchaJsRuntime.open(config, transport);
            ownRuntime = true;
        }
        return runtime;
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
        synchronized (lock) {
            if (ownRuntime && runtime != null) {
                try {
                    runtime.close();
                } catch (Exception ignored) {
                }
            }
            runtime = null;
            ownRuntime = false;
        }
        if (ownTransport) {
            try {
                transport.close();
            } catch (Exception ignored) {
            }
        }
    }
}
