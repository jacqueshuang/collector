package com.j.soul.yc.captcha.aliyun;

import com.j.soul.yc.config.YcClientConfig;
import com.j.soul.yc.exception.YcException;
import com.j.soul.yc.exception.YcStep;
import com.j.soul.yc.http.HttpTransport;

import java.util.Objects;

/**
 * Produces Aliyun captcha {@code deviceToken} via in-process GraalJS (um/z_um.getToken).
 * Optional external {@link CaptchaJsRuntime} can be injected for shared sessions.
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
        boolean close = sharedRuntime == null;
        CaptchaJsRuntime runtime = sharedRuntime;
        try {
            if (runtime == null) {
                runtime = CaptchaJsRuntime.open(config, transport);
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
