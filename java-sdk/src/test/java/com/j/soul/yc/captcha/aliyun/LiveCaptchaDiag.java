package com.j.soul.yc.captcha.aliyun;

import com.j.soul.yc.config.YcClientConfig;
import org.graalvm.polyglot.Value;

import java.nio.file.Path;

public final class LiveCaptchaDiag {
    public static void main(String[] args) throws Exception {
        Path recon = Path.of(System.getenv().getOrDefault("YC_RECON_DIR", "../recon"))
                .toAbsolutePath().normalize();
        System.out.println("recon=" + recon);
        YcClientConfig cfg = YcClientConfig.builder().reconDir(recon.toString()).build();
        try (CaptchaJsRuntime rt = CaptchaJsRuntime.open(cfg)) {
            System.out.println("hasInit=" + rt.hasInit());
            var ctx = rt.contextForDiag();
            Value fn = ctx.getBindings("js").getMember("__solveCaptcha");
            fn.execute(cfg.captchaPrefix(), cfg.sceneId());
            long end = System.currentTimeMillis() + 70_000;
            int i = 0;
            while (System.currentTimeMillis() < end) {
                rt.httpBridgeForDiag().drainCallbacks();
                ctx.eval("js", "if (typeof globalThis.__drainTimers==='function') globalThis.__drainTimers();");
                Value box = ctx.getBindings("js").getMember("__solveBox");
                if (i % 20 == 0) {
                    Value progress = ctx.eval("js", "(globalThis.__progress||[]).slice(-8).join(' || ')");
                    Value timers = ctx.eval("js", "(globalThis.__timers||[]).length");
                    Value ids = ctx.eval("js", "Object.keys(globalThis.document.__byId||{}).join(',')");
                    System.out.println("t=" + (i / 10) + "s done="
                            + (box != null && box.hasMember("done") && box.getMember("done").asBoolean())
                            + " timers=" + timers
                            + " ids=" + ids
                            + " progress=" + progress);
                }
                if (box != null && box.hasMember("done") && box.getMember("done").asBoolean()) {
                    Value val = box.getMember("val");
                    System.out.println("DONE");
                    if (val != null && !val.isNull()) {
                        System.out.println("data=" + brief(val, "data"));
                        System.out.println("token=" + brief(val, "deviceToken"));
                        System.out.println("certifyId=" + str(val, "certifyId"));
                        System.out.println("securityToken=" + brief(val, "securityToken"));
                        Value full = ctx.eval("js", "(globalThis.__progress||[]).join('\\n')");
                        System.out.println("FULL_PROGRESS_BEGIN");
                        System.out.println(full.isString() ? full.asString() : String.valueOf(full));
                        System.out.println("FULL_PROGRESS_END");
                    }
                    return;
                }
                Thread.sleep(100);
                i++;
            }
            Value full = ctx.eval("js", "(globalThis.__progress||[]).join('\\n')");
            System.out.println("TIMEOUT");
            System.out.println("FULL_PROGRESS_BEGIN");
            System.out.println(full.isString() ? full.asString() : String.valueOf(full));
            System.out.println("FULL_PROGRESS_END");
        }
    }

    private static String str(Value obj, String name) {
        if (obj == null || !obj.hasMember(name) || obj.getMember(name).isNull()) return "null";
        Value v = obj.getMember(name);
        return v.isString() ? v.asString() : String.valueOf(v);
    }

    private static String brief(Value obj, String name) {
        String s = str(obj, name);
        if ("null".equals(s)) return s;
        return "len=" + s.length() + " head=" + s.substring(0, Math.min(64, s.length()));
    }
}
