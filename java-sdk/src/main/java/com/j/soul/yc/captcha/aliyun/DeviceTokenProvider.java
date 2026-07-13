package com.j.soul.yc.captcha.aliyun;

import com.j.soul.yc.config.YcClientConfig;
import com.j.soul.yc.exception.YcException;
import com.j.soul.yc.exception.YcStep;
import com.j.soul.yc.http.HttpTransport;

import java.util.Objects;

/**
 * Produces Aliyun captcha {@code deviceToken} via in-process GraalJS (um/z_um.getToken).
 * Runtime resolution: constructor shared → {@link AliyunSession#jsRuntime()} → open new
 * with arg/session {@link HttpTransport}. Prefer one shared {@link CaptchaJsRuntime}
 * with {@link TrajectoryGenerator}.
 */
public final class DeviceTokenProvider {

    private final YcClientConfig config;
    private final CaptchaJsRuntime sharedRuntime;

    public DeviceTokenProvider() {
        this(YcClientConfig.builder().build(), null);
    }

    public DeviceTokenProvider(YcClientConfig config) {
        this(config, null);
    }

    /** Use a shared runtime (not closed by this provider). */
    public DeviceTokenProvider(CaptchaJsRuntime sharedRuntime) {
        this(null, Objects.requireNonNull(sharedRuntime, "sharedRuntime"));
    }

    public DeviceTokenProvider(YcClientConfig config, CaptchaJsRuntime sharedRuntime) {
        this.config = config != null ? config : YcClientConfig.builder().build();
        this.sharedRuntime = sharedRuntime;
    }

    public String obtain(HttpTransport transport, AliyunSession session) {
        Objects.requireNonNull(session, "session");
        CaptchaJsRuntime runtime = sharedRuntime != null ? sharedRuntime : session.jsRuntime();
        boolean close = false;
        try {
            if (runtime == null) {
                HttpTransport tx = transport != null ? transport : session.httpTransport();
                runtime = CaptchaJsRuntime.open(config, tx);
                close = true;
            }
            String token = runtime.getDeviceToken();
            session.setDeviceToken(token);
            return token;
        } catch (YcException e) {
            throw e;
        } catch (Exception e) {
            throw new YcException(YcStep.CAPTCHA, "deviceToken obtain failed: " + e.getMessage(), e);
        } finally {
            if (close && runtime != null) {
                runtime.close();
            }
        }
    }
}
