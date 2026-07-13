package com.j.soul.yc.captcha.aliyun;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapDomTest {

    @Test
    void setAttributeId_registersGetElementById() throws Exception {
        try (Context ctx = Context.newBuilder("js")
                .allowAllAccess(true)
                .option("engine.WarnInterpreterOnly", "false")
                .build()) {
            try (InputStream in = CaptchaJsRuntime.class.getResourceAsStream("/aliyun/js/bootstrap_env.js")) {
                String code = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                ctx.eval("js", code);
            }
            Value found = ctx.eval("js", """
                var el = document.createElement('div');
                el.setAttribute('id', 'aliyunCaptcha-sliding-slider');
                document.body.appendChild(el);
                var a = document.getElementById('aliyunCaptcha-sliding-slider');
                var b = document.querySelector('#aliyunCaptcha-sliding-slider');
                ({ ok: !!(a && b && a === el && b === el), viaProp: (function(){
                    var e2 = document.createElement('div');
                    e2.id = 'aliyunCaptcha-sliding-body';
                    document.body.appendChild(e2);
                    return document.getElementById('aliyunCaptcha-sliding-body') === e2;
                })() })
                """);
            assertTrue(found.getMember("ok").asBoolean());
            assertTrue(found.getMember("viaProp").asBoolean());
        }
    }

    @Test
    void drainTimers_resolvesSleepPromise() throws Exception {
        try (Context ctx = Context.newBuilder("js")
                .allowAllAccess(true)
                .option("engine.WarnInterpreterOnly", "false")
                .build()) {
            try (InputStream in = CaptchaJsRuntime.class.getResourceAsStream("/aliyun/js/bootstrap_env.js")) {
                ctx.eval("js", new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            ctx.eval("js", """
                globalThis.__awaitBox={done:false,val:null,err:null};
                (async function(){
                  await globalThis.__sleep(30);
                  globalThis.__awaitBox.done=true;
                  globalThis.__awaitBox.val='slept';
                })().then(function(){}, function(e){
                  globalThis.__awaitBox.done=true;
                  globalThis.__awaitBox.err=String(e);
                });
                """);
            long deadline = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < deadline) {
                ctx.eval("js", "globalThis.__drainTimers()");
                Value box = ctx.getBindings("js").getMember("__awaitBox");
                if (box.getMember("done").asBoolean()) {
                    assertTrue(box.getMember("err").isNull());
                    assertEquals("slept", box.getMember("val").asString());
                    return;
                }
                Thread.sleep(5);
            }
            assertFalse(true, "sleep promise did not resolve");
        }
    }
}
