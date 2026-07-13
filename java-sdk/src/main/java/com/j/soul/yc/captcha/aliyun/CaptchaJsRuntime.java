package com.j.soul.yc.captcha.aliyun;

import com.j.soul.yc.config.YcClientConfig;
import com.j.soul.yc.exception.YcException;
import com.j.soul.yc.exception.YcStep;
import com.j.soul.yc.http.HttpTransport;
import com.j.soul.yc.http.OkHttpTransport;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-process GraalJS host for Aliyun/FeiLin captcha SDK scripts.
 * No Node binary at runtime; scripts loaded from {@code reconDir} (or classpath fallback).
 */
public final class CaptchaJsRuntime implements AutoCloseable {

    private static final String[] SCRIPT_NAMES = {
            "feilin094.js", "sg029.js", "AliyunCaptcha.js"
    };

    private final Context context;
    private final ExecutorService httpExecutor;
    private final JsHttpBridge httpBridge;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Path reconDir;
    private final String prefix;
    private final String sceneId;
    private final boolean ownTransport;
    private final HttpTransport transport;

    private CaptchaJsRuntime(Context context,
                             ExecutorService httpExecutor,
                             JsHttpBridge httpBridge,
                             Path reconDir,
                             String prefix,
                             String sceneId,
                             HttpTransport transport,
                             boolean ownTransport) {
        this.context = context;
        this.httpExecutor = httpExecutor;
        this.httpBridge = httpBridge;
        this.reconDir = reconDir;
        this.prefix = prefix;
        this.sceneId = sceneId;
        this.transport = transport;
        this.ownTransport = ownTransport;
    }

    public static CaptchaJsRuntime open(Path reconDir, String prefix, String sceneId) {
        return open(YcClientConfig.builder()
                .reconDir(reconDir.toString())
                .captchaPrefix(prefix)
                .sceneId(sceneId)
                .build(), null);
    }

    public static CaptchaJsRuntime open(YcClientConfig config) {
        return open(config, null);
    }

    public static CaptchaJsRuntime open(YcClientConfig config, HttpTransport transport) {
        Objects.requireNonNull(config, "config");
        Path reconDir = CaptchaJsRuntime.resolveReconDir(config.reconDir())
                .orElseThrow(() -> new YcException(YcStep.CAPTCHA,
                        "reconDir not found; set YcClientConfig.reconDir or YC_RECON_DIR"));
        String prefix = config.captchaPrefix();
        String sceneId = config.sceneId();

        boolean own = transport == null;
        HttpTransport tx = transport != null ? transport : new OkHttpTransport(config);
        String ua = config.userAgent();
        String org = config.origin();
        String ref = config.referer();

        ExecutorService exec = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "yc-captcha-js-http");
            t.setDaemon(true);
            return t;
        });
        JsHttpBridge bridge = new JsHttpBridge(tx, exec, ua, org, ref);

        Context ctx = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(s -> true)
                .allowIO(true)
                .allowExperimentalOptions(true)
                .option("js.ecmascript-version", "2022")
                .option("engine.WarnInterpreterOnly", "false")
                .build();

        CaptchaJsRuntime runtime = new CaptchaJsRuntime(ctx, exec, bridge, reconDir, prefix, sceneId, tx, own);
        try {
            runtime.install();
        } catch (RuntimeException e) {
            runtime.close();
            throw e;
        }
        return runtime;
    }

    private void install() {
        Value bindings = context.getBindings("js");
        bindings.putMember("__javaHttp", httpBridge);

        evalResource("/aliyun/js/bootstrap_env.js", "bootstrap_env.js");

        // Optional CryptoJS for window.__ALIYUN_CRYPT
        loadCryptoJs();

        for (String name : SCRIPT_NAMES) {
            Path p = reconDir.resolve(name);
            if (!Files.isRegularFile(p)) {
                throw new YcException(YcStep.CAPTCHA, "missing SDK script: " + p);
            }
            try {
                String code = Files.readString(p, StandardCharsets.UTF_8);
                // Prefer local dynamic SG when remote script tag loads; patch common remote loader
                // by preloading sg_dynamic.js as fallback later if present.
                evalSource(code, name);
            } catch (IOException e) {
                throw new YcException(YcStep.CAPTCHA, "read script failed: " + p, e);
            }
        }

        // If a pinned dynamic SG exists, evaluate it so transform/token helpers are present
        // even when network script injection is delayed.
        Path dyn = firstExisting(
                reconDir.resolve("sg_dynamic.js"),
                reconDir.resolve("sg_dynamic_latest.js"));
        if (dyn != null) {
            try {
                evalSource(Files.readString(dyn, StandardCharsets.UTF_8), dyn.getFileName().toString());
            } catch (IOException e) {
                throw new YcException(YcStep.CAPTCHA, "read dynamic SG failed: " + dyn, e);
            }
        }

        Value init = bindings.getMember("initAliyunCaptcha");
        if (init == null || !init.canExecute()) {
            throw new YcException(YcStep.CAPTCHA, "initAliyunCaptcha not available after script load");
        }
    }

    private void loadCryptoJs() {
        Path fromRecon = reconDir.resolveSibling("node_helper")
                .resolve("node_modules").resolve("crypto-js").resolve("crypto-js.js");
        Path alt = reconDir.getParent() == null ? null
                : reconDir.getParent().resolve("node_helper/node_modules/crypto-js/crypto-js.js");
        Path p = Files.isRegularFile(fromRecon) ? fromRecon
                : (alt != null && Files.isRegularFile(alt) ? alt : null);
        if (p == null) {
            // CryptoJS may be optional if AliyunCaptcha embeds crypto; continue.
            return;
        }
        try {
            evalSource(Files.readString(p, StandardCharsets.UTF_8), "crypto-js.js");
            context.eval("js", "globalThis.__ALIYUN_CRYPT = globalThis.CryptoJS;");
        } catch (IOException e) {
            // ignore optional crypto
        }
    }

    private void evalResource(String classpath, String name) {
        try (InputStream in = CaptchaJsRuntime.class.getResourceAsStream(classpath)) {
            if (in == null) {
                throw new YcException(YcStep.CAPTCHA, "missing classpath resource " + classpath);
            }
            String code = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            evalSource(code, name);
        } catch (IOException e) {
            throw new YcException(YcStep.CAPTCHA, "load resource failed: " + classpath, e);
        }
    }

    private void evalSource(String code, String name) {
        try {
            context.eval(Source.newBuilder("js", code, name).build());
        } catch (IOException e) {
            throw new YcException(YcStep.CAPTCHA, "eval failed: " + name, e);
        } catch (Exception e) {
            // Some SDK scripts throw during top-level env probes; tolerate non-fatal load issues
            // only when init function still becomes available later.
            if (name.startsWith("feilin") || name.startsWith("sg") || name.startsWith("Aliyun")) {
                // rethrow if completely broken syntax
                String msg = e.getMessage() == null ? "" : e.getMessage();
                if (msg.contains("SyntaxError") || msg.contains("Parse")) {
                    throw new YcException(YcStep.CAPTCHA, "eval syntax error in " + name + ": " + msg, e);
                }
                // swallow runtime env errors during load (mirrors node helpers try/catch)
                return;
            }
            throw new YcException(YcStep.CAPTCHA, "eval failed: " + name + ": " + e.getMessage(), e);
        }
    }

    public Path reconDir() {
        return reconDir;
    }

    /** Shared transport used by this runtime's JS HTTP bridge. */
    public HttpTransport transport() {
        return transport;
    }

    /**
     * Obtain deviceToken via um/z_um.getToken after captcha SDK init (network).
     */
    public String getDeviceToken() {
        ensureOpen();
        try {
            Value fn = context.getBindings("js").getMember("__initAndToken");
            if (fn == null || !fn.canExecute()) {
                throw new YcException(YcStep.CAPTCHA, "__initAndToken missing");
            }
            Value promise = fn.execute(prefix, sceneId);
            String token = awaitString(promise, Duration.ofSeconds(45));
            if (token == null || token.isBlank()) {
                throw new YcException(YcStep.CAPTCHA, "um/z_um.getToken returned empty");
            }
            return token;
        } catch (YcException e) {
            throw e;
        } catch (Exception e) {
            throw new YcException(YcStep.CAPTCHA, "getDeviceToken failed: " + e.getMessage(), e);
        }
    }

    /**
     * Full slider simulation: Init + drag + capture Verify fields (network).
     */
    public CaptchaSolveResult solveSlider() {
        ensureOpen();
        try {
            Value fn = context.getBindings("js").getMember("__solveCaptcha");
            if (fn == null || !fn.canExecute()) {
                throw new YcException(YcStep.CAPTCHA, "__solveCaptcha missing");
            }
            Value promise = fn.execute(prefix, sceneId);
            // init sleep 8s + drag sleeps + up to 15s verify wait; allow headroom
            Value result = awaitValue(promise, Duration.ofSeconds(180));
            if (result == null || result.isNull()) {
                throw new YcException(YcStep.CAPTCHA, "solveSlider returned null");
            }
            String data = strMember(result, "data");
            String deviceToken = strMember(result, "deviceToken");
            String certifyId = strMember(result, "certifyId");
            String securityToken = strMember(result, "securityToken");
            Boolean verifyResult = null;
            if (result.hasMember("verifyResult") && !result.getMember("verifyResult").isNull()) {
                Value vr = result.getMember("verifyResult");
                if (vr.isBoolean()) {
                    verifyResult = vr.asBoolean();
                }
            }
            if (data == null || data.isBlank() || deviceToken == null || deviceToken.isBlank()) {
                throw new YcException(YcStep.CAPTCHA,
                        "solveSlider incomplete data/deviceToken certifyId=" + certifyId);
            }
            return new CaptchaSolveResult(certifyId, deviceToken, data, securityToken, verifyResult);
        } catch (YcException e) {
            throw e;
        } catch (Exception e) {
            throw new YcException(YcStep.CAPTCHA, "solveSlider failed: " + e.getMessage(), e);
        }
    }

    /**
     * Hybrid helper: when SDK exposes a global transform callable via prior solve hooks, use it.
     * Currently uses full solve path for trajectory ciphertext freshness.
     */
    public String generateTrajectoryData() {
        return solveSlider().data();
    }

    public boolean hasInit() {
        ensureOpen();
        Value init = context.getBindings("js").getMember("initAliyunCaptcha");
        return init != null && init.canExecute();
    }

    private String awaitString(Value promise, Duration timeout) throws Exception {
        Value v = awaitValue(promise, timeout);
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isString()) {
            return v.asString();
        }
        return v.toString();
    }

    private Value awaitValue(Value promise, Duration timeout) throws Exception {
        if (promise == null || promise.isNull()) {
            return null;
        }
        // GraalJS thenable: resolve on this thread; HTTP completions drained here only.
        if (promise.canInvokeMember("then")) {
            context.getBindings("js").putMember("__p", promise);
            context.eval("js",
                    "globalThis.__awaitBox={done:false,val:null,err:null};" +
                            "globalThis.__p.then(function(v){globalThis.__awaitBox.done=true;globalThis.__awaitBox.val=v;}," +
                            "function(e){globalThis.__awaitBox.done=true;globalThis.__awaitBox.err=e;});");
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                // Drain HTTP→JS callbacks on context owner thread (never on worker).
                httpBridge.drainCallbacks();
                // Pump polyfilled setTimeout/setInterval used by __sleep / SDK.
                try {
                    context.eval("js",
                            "if (typeof globalThis.__drainTimers==='function') globalThis.__drainTimers();");
                } catch (Exception ignored) {
                }
                Value boxState = context.getBindings("js").getMember("__awaitBox");
                if (boxState != null && boxState.hasMember("done") && boxState.getMember("done").asBoolean()) {
                    Value errV = boxState.getMember("err");
                    if (errV != null && !errV.isNull()) {
                        throw new YcException(YcStep.CAPTCHA, "js promise rejected: " + errV);
                    }
                    return boxState.getMember("val");
                }
                Thread.sleep(10);
            }
            throw new YcException(YcStep.CAPTCHA, "js promise timeout after " + timeout);
        }
        return promise;
    }

    private static String strMember(Value obj, String name) {
        if (obj == null || !obj.hasMember(name)) {
            return null;
        }
        Value v = obj.getMember(name);
        if (v == null || v.isNull()) {
            return null;
        }
        return v.isString() ? v.asString() : v.toString();
    }

    private static Path firstExisting(Path... paths) {
        for (Path p : paths) {
            if (p != null && Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new YcException(YcStep.CAPTCHA, "CaptchaJsRuntime closed");
        }
    }

    /**
     * Resolve recon dir from config path, env, or common relative locations.
     * If {@code configured} is non-blank, only that path is considered (no silent fallback).
     */
    public static Optional<Path> resolveReconDir(String configured) {
        if (configured != null && !configured.isBlank()) {
            Path p = Path.of(configured).toAbsolutePath().normalize();
            if (Files.isDirectory(p) && Files.isRegularFile(p.resolve("feilin094.js"))) {
                return Optional.of(p);
            }
            return Optional.empty();
        }
        String env = System.getenv("YC_RECON_DIR");
        if (env != null && !env.isBlank()) {
            Path p = Path.of(env).toAbsolutePath().normalize();
            if (Files.isDirectory(p) && Files.isRegularFile(p.resolve("feilin094.js"))) {
                return Optional.of(p);
            }
            return Optional.empty();
        }
        Path cwd = Path.of("").toAbsolutePath();
        Path[] candidates = {
                cwd.resolve("../recon"),
                cwd.resolve("../../recon"),
                cwd.resolve("recon"),
                cwd.getParent() != null ? cwd.getParent().resolve("recon") : null
        };
        for (Path c : candidates) {
            if (c != null) {
                Path n = c.normalize();
                if (Files.isDirectory(n) && Files.isRegularFile(n.resolve("feilin094.js"))) {
                    return Optional.of(n);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            context.close(true);
        } catch (Exception ignored) {
        }
        httpExecutor.shutdownNow();
        try {
            httpExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (ownTransport) {
            try {
                transport.close();
            } catch (Exception ignored) {
            }
        }
    }
}
