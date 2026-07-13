package com.j.soul.yc.captcha.aliyun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.j.soul.yc.config.YcClientConfig;
import com.j.soul.yc.exception.YcException;
import com.j.soul.yc.exception.YcStep;
import com.j.soul.yc.http.HttpTransport;
import org.htmlunit.BrowserVersion;
import org.htmlunit.NicelyResynchronizingAjaxController;
import org.htmlunit.ScriptResult;
import org.htmlunit.WebClient;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.WebConnectionWrapper;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Full JVM browser DOM host (HtmlUnit) for Aliyun/FeiLin captcha.
 * Loads the real perfect99 login page (required for a working JS event loop),
 * evals {@code AliyunCaptcha.js}, mounts embed widget, drags slider, captures Verify fields.
 */
public final class CaptchaHtmlUnitRuntime implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PAGE_URL = "https://uc.perfect99.com/loginAndRegistration";

    private final WebClient webClient;
    private final HtmlPage page;
    private final Path reconDir;
    private final String prefix;
    private final String sceneId;
    private final VerifyCapture capture;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private CaptchaHtmlUnitRuntime(WebClient webClient,
                                   HtmlPage page,
                                   Path reconDir,
                                   String prefix,
                                   String sceneId,
                                   VerifyCapture capture) {
        this.webClient = webClient;
        this.page = page;
        this.reconDir = reconDir;
        this.prefix = prefix;
        this.sceneId = sceneId;
        this.capture = capture;
    }

    public static CaptchaHtmlUnitRuntime open(YcClientConfig config) {
        return open(config, null);
    }

    /**
     * @param transport optional; HtmlUnit uses its own HTTP stack. Kept for API parity with Graal runtime.
     */
    public static CaptchaHtmlUnitRuntime open(YcClientConfig config, HttpTransport transport) {
        Objects.requireNonNull(config, "config");
        Path reconDir = CaptchaJsRuntime.resolveReconDir(config.reconDir())
                .orElseThrow(() -> new YcException(YcStep.CAPTCHA,
                        "reconDir not found; set YcClientConfig.reconDir or YC_RECON_DIR"));

        BrowserVersion browser = new BrowserVersion.BrowserVersionBuilder(BrowserVersion.CHROME)
                .setUserAgent(config.userAgent())
                .build();

        WebClient client = new WebClient(browser);
        client.getOptions().setJavaScriptEnabled(true);
        client.getOptions().setCssEnabled(true);
        client.getOptions().setThrowExceptionOnScriptError(false);
        client.getOptions().setThrowExceptionOnFailingStatusCode(false);
        client.getOptions().setTimeout((int) Math.max(60_000L, config.readTimeout().toMillis()));
        client.getOptions().setDownloadImages(false);
        client.getOptions().setRedirectEnabled(true);
        client.getOptions().setUseInsecureSSL(true);
        client.setAjaxController(new NicelyResynchronizingAjaxController());
        client.getCookieManager().setCookiesEnabled(true);
        client.waitForBackgroundJavaScriptStartingBefore(2_000);

        VerifyCapture capture = new VerifyCapture();
        installCaptureConnection(client, capture);

        try {
            // Real origin page is required: blank StringWebResponse pages do not pump setTimeout/Promise
            // reliably under HtmlUnit, so Aliyun init never finishes mounting #nc children.
            HtmlPage page = client.getPage(PAGE_URL);
            client.waitForBackgroundJavaScript(3_000);

            CaptchaHtmlUnitRuntime runtime = new CaptchaHtmlUnitRuntime(
                    client, page, reconDir, config.captchaPrefix(), config.sceneId(), capture);
            runtime.bootstrap();
            return runtime;
        } catch (YcException e) {
            client.close();
            throw e;
        } catch (Exception e) {
            client.close();
            throw new YcException(YcStep.CAPTCHA, "HtmlUnit open failed: " + e.getMessage(), e);
        }
    }

    private static void installCaptureConnection(WebClient client, VerifyCapture capture) {
        client.setWebConnection(new WebConnectionWrapper(client.getWebConnection()) {
            @Override
            public WebResponse getResponse(WebRequest request) throws IOException {
                String reqUrl = request.getUrl() == null ? "" : request.getUrl().toString();
                String body = request.getRequestBody();
                if (body != null && isVerifyUrl(reqUrl)) {
                    capture.parseRequestBody(body);
                }
                WebResponse response = super.getResponse(request);
                if (isVerifyUrl(reqUrl)) {
                    try {
                        capture.parseResponseBody(response.getContentAsString(StandardCharsets.UTF_8));
                    } catch (Exception ignored) {
                    }
                }
                return response;
            }
        });
    }

    private static boolean isVerifyUrl(String url) {
        if (url == null) {
            return false;
        }
        String u = url.toLowerCase();
        // e.g. https://1uu8u2-verify.captcha-open.aliyuncs.com/
        return u.contains("verify.captcha-open") || u.contains("-verify.") || u.contains("/verify");
    }

    private void bootstrap() {
        evalJsQuiet(
                "if(!document.getElementById('nc')){"
                        + "var d=document.createElement('div');d.id='nc';"
                        + "d.style.width='360px';d.style.height='40px';document.body.appendChild(d);}"
                        + "window.__ALIYUN_PROGRESS=[];"
                        + "window.__logProgress=function(m){try{window.__ALIYUN_PROGRESS.push(Date.now()+' '+m);}catch(e){}};"
                        + "window.__cvp=null;"
                        + "if(!window.requestAnimationFrame){window.requestAnimationFrame=function(cb){return setTimeout(cb,16);};}"
                        + "if(!window.cancelAnimationFrame){window.cancelAnimationFrame=function(id){clearTimeout(id);};}"
        );
        loadCryptoJs();
        // Only AliyunCaptcha.js is required locally; FeiLin + dynamic SG load from CDN after Init.
        Path aliyun = reconDir.resolve("AliyunCaptcha.js");
        if (!Files.isRegularFile(aliyun)) {
            throw new YcException(YcStep.CAPTCHA, "missing SDK script: " + aliyun);
        }
        evalFile(aliyun);
        Object init = page.executeJavaScript("typeof initAliyunCaptcha").getJavaScriptResult();
        if (!"function".equals(String.valueOf(init))) {
            throw new YcException(YcStep.CAPTCHA, "initAliyunCaptcha not available after HtmlUnit script load");
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
            return;
        }
        evalFile(p);
        evalJsQuiet("window.__ALIYUN_CRYPT = window.CryptoJS;");
    }

    private void evalFile(Path p) {
        try {
            evalJsQuiet(Files.readString(p, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new YcException(YcStep.CAPTCHA, "read script failed: " + p, e);
        }
    }

    private void evalJsQuiet(String code) {
        try {
            page.executeJavaScript(code);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("SyntaxError") || msg.contains("ParseError") || msg.contains("syntax error")
                    || msg.contains("missing ;")) {
                // Page scripts / SDK may throw non-fatal parse noise; only hard-fail bootstrap Aliyun load.
                if (code != null && code.length() > 10_000 && code.contains("initAliyunCaptcha")) {
                    throw new YcException(YcStep.CAPTCHA, "HtmlUnit JS syntax error loading AliyunCaptcha: " + msg, e);
                }
            }
        }
    }

    public Path reconDir() {
        return reconDir;
    }

    public boolean hasInit() {
        ensureOpen();
        Object init = page.executeJavaScript("typeof initAliyunCaptcha").getJavaScriptResult();
        return "function".equals(String.valueOf(init));
    }

    public String getDeviceToken() {
        ensureOpen();
        try {
            initCaptchaOnly();
            waitJs(3_000);
            String token = jsString(
                    "(function(){try{if(window.z_um&&window.z_um.getToken)return window.z_um.getToken();}"
                            + "catch(e){}try{if(window.um&&window.um.getToken)return window.um.getToken();}"
                            + "catch(e){}return '';})()"
            );
            if (token == null || token.isBlank()) {
                throw new YcException(YcStep.CAPTCHA, "um/z_um.getToken returned empty (HtmlUnit)");
            }
            return token;
        } catch (YcException e) {
            throw e;
        } catch (Exception e) {
            throw new YcException(YcStep.CAPTCHA, "getDeviceToken failed: " + e.getMessage(), e);
        }
    }

    public CaptchaSolveResult solveSlider() {
        ensureOpen();
        capture.reset();
        try {
            initCaptchaOnly();
            waitForSlider(45_000);
            dragSlider();
            waitForVerifyCapture(60_000);

            String data = capture.data.get();
            String deviceToken = capture.deviceToken.get();
            String certifyId = capture.certifyId.get();
            String securityToken = capture.securityToken.get();
            Boolean verifyResult = capture.verifyResult.get();

            if (deviceToken == null || deviceToken.isBlank()) {
                String z = jsString(
                        "(function(){try{if(window.z_um&&window.z_um.getToken)return window.z_um.getToken();}"
                                + "catch(e){}try{if(window.um&&window.um.getToken)return window.um.getToken();}"
                                + "catch(e){}return '';})()"
                );
                if (z != null && !z.isBlank()) {
                    deviceToken = z;
                }
            }
            if (securityToken == null || securityToken.isBlank()) {
                String fromCvp = jsString(
                        "(function(){try{var p=window.__cvp;if(!p)return '';"
                                + "if(typeof p==='string'){try{p=JSON.parse(atob?atob(p):p);}catch(e){return '';}}"
                                + "return p.securityToken||p.SecurityToken||'';}catch(e){return '';}})()"
                );
                if (fromCvp != null && !fromCvp.isBlank()) {
                    securityToken = fromCvp;
                }
            }
            if (certifyId == null || certifyId.isBlank()) {
                String fromCvp = jsString(
                        "(function(){try{var p=window.__cvp;if(!p)return '';"
                                + "if(typeof p==='string'){try{p=JSON.parse(atob?atob(p):p);}catch(e){return '';}}"
                                + "return p.certifyId||p.CertifyId||'';}catch(e){return '';}})()"
                );
                if (fromCvp != null && !fromCvp.isBlank()) {
                    certifyId = fromCvp;
                }
            }

            if (data == null || data.isBlank() || deviceToken == null || deviceToken.isBlank()) {
                throw new YcException(YcStep.CAPTCHA,
                        "solveSlider incomplete data/deviceToken certifyId=" + certifyId
                                + " progress=" + progressSnapshot()
                                + " capture=" + capture.summary());
            }
            return new CaptchaSolveResult(certifyId, deviceToken, data, securityToken, verifyResult);
        } catch (YcException e) {
            throw e;
        } catch (Exception e) {
            throw new YcException(YcStep.CAPTCHA,
                    "solveSlider failed: " + e.getMessage() + " progress=" + progressSnapshot(), e);
        }
    }

    private void initCaptchaOnly() {
        // Fresh host node for re-solve on shared runtime
        evalJsQuiet(
                "var old=document.getElementById('nc');"
                        + "if(old){old.innerHTML='';}"
                        + "else{var d=document.createElement('div');d.id='nc';"
                        + "d.style.width='360px';d.style.height='40px';document.body.appendChild(d);}"
        );
        String js = ""
                + "(function(prefix,sceneId){"
                + "  window.__cvp=null;"
                + "  window.__initErr=null;"
                + "  window.__initResolved=false;"
                + "  try{"
                + "    var p=initAliyunCaptcha({"
                + "      prefix:prefix, SceneId:sceneId, mode:'embed', element:'#nc',"
                + "      success:function(param){window.__cvp=param;},"
                + "      fail:function(e){window.__initErr=String(e);},"
                + "      getInstance:function(i){window.__captchaInst=i;}"
                + "    });"
                + "    if(p&&typeof p.then==='function'){"
                + "      p.then(function(){window.__initResolved=true;},"
                + "             function(e){window.__initErr=String(e);});"
                + "    }"
                + "  }catch(e){window.__initErr=String(e);}"
                + "})('" + escapeJs(prefix) + "','" + escapeJs(sceneId) + "');";
        evalJsQuiet(js);
        waitJs(2_000);
    }

    private void waitForSlider(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            waitJs(500);
            String id = jsString(
                    "(function(){var el=document.getElementById('aliyunCaptcha-sliding-slider');"
                            + "return el?el.id:'';})()"
            );
            if ("aliyunCaptcha-sliding-slider".equals(id)) {
                return;
            }
            if (capture.certifyId.get() != null && capture.data.get() != null) {
                return;
            }
            String err = jsString("String(window.__initErr||'')");
            if (err != null && !err.isBlank() && !"null".equals(err) && !"undefined".equals(err)) {
                throw new YcException(YcStep.CAPTCHA, "initAliyunCaptcha failed: " + err);
            }
        }
        throw new YcException(YcStep.CAPTCHA,
                "slider #aliyunCaptcha-sliding-slider not mounted within " + timeoutMs + "ms"
                        + " progress=" + progressSnapshot()
                        + " ncChildren=" + jsString(
                        "(function(){var n=document.getElementById('nc');return n?String(n.children.length):'null';})()")
                        + " initErr=" + jsString("String(window.__initErr||'')")
                        + " resolved=" + jsString("String(!!window.__initResolved)"));
    }

    private void dragSlider() {
        int startX = 120;
        int startY = 120;
        int endX = 440;
        int endY = 120;
        int distance = endX - startX;
        int totalSteps = 80 + ThreadLocalRandom.current().nextInt(20);

        StringBuilder pts = new StringBuilder("[");
        for (int i = 1; i <= totalSteps; i++) {
            double t = i / (double) totalSteps;
            double eased = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
            double x = startX + distance * eased + (ThreadLocalRandom.current().nextDouble() - 0.5) * 2;
            double y = startY + (ThreadLocalRandom.current().nextDouble() - 0.5) * 3
                    + Math.sin(t * Math.PI * 2) * 2;
            if (i > 1) {
                pts.append(',');
            }
            pts.append('[').append((int) Math.round(x)).append(',').append((int) Math.round(y)).append(']');
        }
        pts.append(']');

        String dragJs = ""
                + "(function(){"
                + "  var slider=document.getElementById('aliyunCaptcha-sliding-slider');"
                + "  if(!slider){window.__dragErr='no-slider';return;}"
                + "  var startX=" + startX + ", startY=" + startY + ", endX=" + endX + ", endY=" + endY + ";"
                + "  var pts=" + pts + ";"
                + "  function fire(target,type,x,y,buttons){"
                + "    var ev=new MouseEvent(type,{bubbles:true,cancelable:true,view:window,"
                + "      clientX:x,clientY:y,screenX:x,screenY:y,button:0,buttons:buttons});"
                + "    target.dispatchEvent(ev);"
                + "  }"
                + "  fire(slider,'mousedown',startX,startY,1);"
                + "  window.__dragPts=pts; window.__dragIdx=0; window.__dragEnd=[endX,endY];"
                + "  window.__dragDone=false;"
                + "  function step(){"
                + "    if(window.__dragIdx>=window.__dragPts.length){"
                + "      var e=window.__dragEnd;"
                + "      fire(document,'mouseup',e[0],e[1],0);"
                + "      window.__dragDone=true; return;"
                + "    }"
                + "    var p=window.__dragPts[window.__dragIdx++];"
                + "    fire(document,'mousemove',p[0],p[1],1);"
                + "    setTimeout(step, 5+Math.floor(Math.random()*15));"
                + "  }"
                + "  setTimeout(step, 80+Math.floor(Math.random()*120));"
                + "})();";
        evalJsQuiet(dragJs);

        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            waitJs(200);
            if ("true".equals(jsString("String(!!window.__dragDone)"))) {
                return;
            }
            if (capture.securityToken.get() != null || capture.data.get() != null) {
                return;
            }
        }
    }

    private void waitForVerifyCapture(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            waitJs(500);
            if (capture.data.get() != null && capture.deviceToken.get() != null
                    && capture.securityToken.get() != null) {
                return;
            }
            if ("1".equals(jsString("window.__cvp? '1':'0'"))
                    && capture.data.get() != null
                    && capture.deviceToken.get() != null) {
                waitJs(1_000);
                return;
            }
        }
    }

    private void waitJs(long ms) {
        try {
            webClient.waitForBackgroundJavaScript(ms);
        } catch (Exception ignored) {
        }
        try {
            page.getEnclosingWindow().getJobManager().waitForJobsStartingBefore(Math.min(ms, 1_000));
        } catch (Exception ignored) {
        }
        try {
            Thread.sleep(Math.min(ms, 100));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String jsString(String expr) {
        try {
            ScriptResult r = page.executeJavaScript(expr);
            Object v = r.getJavaScriptResult();
            return v == null ? null : String.valueOf(v);
        } catch (Exception e) {
            return null;
        }
    }

    private String progressSnapshot() {
        String p = jsString("(window.__ALIYUN_PROGRESS||[]).slice(-20).join(' | ')");
        return p == null ? "" : p;
    }

    private static String escapeJs(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new YcException(YcStep.CAPTCHA, "CaptchaHtmlUnitRuntime closed");
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            webClient.close();
        } catch (Exception ignored) {
        }
    }

    static final class VerifyCapture {
        final AtomicReference<String> data = new AtomicReference<>();
        final AtomicReference<String> deviceToken = new AtomicReference<>();
        final AtomicReference<String> certifyId = new AtomicReference<>();
        final AtomicReference<String> securityToken = new AtomicReference<>();
        final AtomicReference<Boolean> verifyResult = new AtomicReference<>();

        void reset() {
            data.set(null);
            deviceToken.set(null);
            certifyId.set(null);
            securityToken.set(null);
            verifyResult.set(null);
        }

        void parseRequestBody(String body) {
            try {
                String decoded = body;
                try {
                    decoded = URLDecoder.decode(body, StandardCharsets.UTF_8);
                } catch (Exception ignored) {
                }
                if (decoded.contains("CaptchaVerifyParam=") || decoded.contains("captchaVerifyParam=")) {
                    for (String part : decoded.split("&")) {
                        int idx = part.indexOf('=');
                        if (idx <= 0) {
                            continue;
                        }
                        String key = part.substring(0, idx);
                        String val = part.substring(idx + 1);
                        if ("CaptchaVerifyParam".equalsIgnoreCase(key) || "captchaVerifyParam".equals(key)) {
                            parseCaptchaVerifyParamJson(val);
                        } else if ("CertifyId".equalsIgnoreCase(key) && isBlank(certifyId.get())) {
                            certifyId.set(val);
                        }
                    }
                } else if (decoded.trim().startsWith("{")) {
                    parseCaptchaVerifyParamJson(decoded);
                } else {
                    for (String part : decoded.split("&")) {
                        int idx = part.indexOf('=');
                        if (idx <= 0) {
                            continue;
                        }
                        String key = part.substring(0, idx);
                        String val = part.substring(idx + 1);
                        if ("data".equals(key) && isBlank(data.get())) {
                            data.set(val);
                        } else if ("deviceToken".equals(key) && isBlank(deviceToken.get())) {
                            deviceToken.set(val);
                        } else if ("certifyId".equalsIgnoreCase(key) && isBlank(certifyId.get())) {
                            certifyId.set(val);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        void parseCaptchaVerifyParamJson(String json) {
            try {
                JsonNode n = MAPPER.readTree(json);
                if (n.hasNonNull("data") && isBlank(data.get())) {
                    data.set(n.get("data").asText());
                }
                if (n.hasNonNull("deviceToken") && isBlank(deviceToken.get())) {
                    deviceToken.set(n.get("deviceToken").asText());
                }
                if (n.hasNonNull("certifyId") && isBlank(certifyId.get())) {
                    certifyId.set(n.get("certifyId").asText());
                }
                if (n.hasNonNull("securityToken") && isBlank(securityToken.get())) {
                    securityToken.set(n.get("securityToken").asText());
                }
            } catch (Exception ignored) {
            }
        }

        void parseResponseBody(String text) {
            if (text == null || text.isBlank()) {
                return;
            }
            try {
                JsonNode root = MAPPER.readTree(text);
                JsonNode result = root.has("Result") ? root.get("Result")
                        : root.has("result") ? root.get("result") : root;
                if (result != null && result.isObject()) {
                    if (result.hasNonNull("securityToken")) {
                        securityToken.set(result.get("securityToken").asText());
                    }
                    if (result.hasNonNull("SecurityToken")) {
                        securityToken.set(result.get("SecurityToken").asText());
                    }
                    if (result.has("VerifyResult")) {
                        verifyResult.set(result.get("VerifyResult").asBoolean());
                    } else if (result.has("verifyResult")) {
                        verifyResult.set(result.get("verifyResult").asBoolean());
                    }
                    if (result.hasNonNull("certifyId") && isBlank(certifyId.get())) {
                        certifyId.set(result.get("certifyId").asText());
                    }
                }
            } catch (Exception ignored) {
            }
        }

        String summary() {
            return "data=" + blankHint(data.get())
                    + " deviceToken=" + blankHint(deviceToken.get())
                    + " certifyId=" + blankHint(certifyId.get())
                    + " securityToken=" + blankHint(securityToken.get())
                    + " verifyResult=" + verifyResult.get();
        }

        private static boolean isBlank(String s) {
            return s == null || s.isBlank();
        }

        private static String blankHint(String s) {
            if (s == null) {
                return "null";
            }
            if (s.isBlank()) {
                return "blank";
            }
            return "len=" + s.length();
        }
    }
}
