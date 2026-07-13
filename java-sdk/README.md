# yc — Perfect99 pure-Java SDK

Maven library for perfect99 check-mobile / Aliyun captcha / SMS / replace-mobile.
No Spring dependency; Spring Boot apps construct a manual `@Bean`.

| | |
|---|---|
| Coordinate | `com.j.soul.yc:yc:0.1.0` |
| Java | 23+ (`maven.compiler.release=23`) |
| Captcha runtime | **HtmlUnit** full DOM (default) + recon scripts; optional GraalJS (no Node) |

## Dependency

Local install first:

```bash
cd java-sdk && mvn -q clean test install
```

```xml
<dependency>
  <groupId>com.j.soul.yc</groupId>
  <artifactId>yc</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Spring Boot (manual bean)

```java
@Bean(destroyMethod = "close")
YcClient ycClient(@Value("${yc.recon-dir}") String reconDir) {
    return YcClient.builder()
            .config(YcClientConfig.builder()
                    .reconDir(reconDir)                 // required for local Aliyun/FeiLin SDK scripts
                    .captchaEngine("htmlunit")          // default; or "graal"
                    .transportType(TransportType.OKHTTP) // or TransportType.CURL4J
                    .build())
            .build();
}
```

Minimal non-Spring usage:

```java
try (YcClient client = YcClient.builder()
        .config(YcClientConfig.builder()
                .reconDir("/path/to/recon")
                .build())
        .build()) {
    ApiResult r = client.sendSms("1xxxxxxxxxx");
}
```

Public API: `checkMobileAvailable` · `getCaptchaVerifyParam` · `getSmsCode` · `sendSms` · `replaceMobile`.

## Config (`YcClientConfig`)

| Field | Default | Notes |
|-------|---------|-------|
| `baseUrl` | `https://uc.perfect99.com/api` | business API root |
| `userAgent` | Chrome 149 UA | request UA |
| `channel` | `pc` | header |
| `client` | `mall` | header |
| `referer` | loginAndRegistration URL | header |
| `origin` | `https://uc.perfect99.com` | header |
| `gwKey` | production key | GW sign |
| `gwClient` | `mall_pc` | GW header |
| `rsaPublicKeyBase64` | production RSA pubkey | crytoLogin |
| `captchaPrefix` | `1uu8u2` | Aliyun host prefix |
| `sceneId` | `1pn9314j` | captcha scene |
| `transportType` | `OKHTTP` | `OKHTTP` \| `CURL4J` |
| `connectTimeout` | 10s | |
| `readTimeout` | 30s | |
| `proxyHost` / `proxyPort` | empty | optional HTTP proxy |
| `reconDir` | null → auto | Aliyun/FeiLin SDK scripts dir (**required for captcha**) |
| `captchaEngine` | `htmlunit` | `htmlunit` (full DOM) \| `graal` (minimal bootstrap) |
| `captchaConcurrency` | CPU 2..8 | max parallel captcha solves (each = isolated browser) |
| `captchaAcquireTimeout` | 120s | wait for free captcha slot before failing |
| `httpMaxRequests` | 64 | OkHttp dispatcher global concurrency |
| `httpMaxRequestsPerHost` | 32 | OkHttp per-host concurrency |
| `httpMaxIdleConnections` | 32 | OkHttp connection pool idle size |

`reconDir` must contain `feilin094.js` (also expects `sg029.js`, `AliyunCaptcha.js`).  
Resolution order when unset: `YC_RECON_DIR` → relative `../recon`, `../../recon`, `recon`.  
HtmlUnit may also load remote dynamic SG scripts over the network; local recon is still the primary SDK source.

There is **no** `aliyunKeyId` / `aliyunKeySecret` on config — captcha is client-side FeiLin/Aliyun JS, not OpenAPI keys.

## Transport switch

```java
YcClientConfig.builder().transportType(TransportType.OKHTTP).build(); // default
YcClientConfig.builder().transportType(TransportType.CURL4J).build();  // system curl CLI
```

- **OKHTTP** — OkHttp 4.x (default), connection-pooled; tune `httpMaxRequests*` for multi-thread.
- **CURL4J** — phase-1 backend spawns system `curl` via `ProcessBuilder` (not a Maven libcurl binding). Requires `curl` on `PATH`. Prefer OKHTTP under concurrency.

Custom transport: `YcClient.builder().transport(myHttpTransport)`.

## High concurrency (target: 500 simultaneous)

Captcha is the bottleneck (~40–60s / solve, ~100–300MB RAM per HtmlUnit session).

### What the SDK does

1. **One singleton `YcClient`** — share OkHttp pool; never create a client per request.
2. **Captcha concurrency** — fair semaphore `captchaConcurrency` (default 2..8, **can set to 500**).
3. **Isolated sessions** — each solve opens/closes its own HtmlUnit `WebClient`.
4. **Batch helper** — `YcSmsBatchExecutor` fans out many numbers onto a worker pool.

### Honest capacity

| Setup | Concurrent captcha | Rough RAM | Notes |
|-------|--------------------|-----------|--------|
| Default single process | 2–8 | few GB | safe laptop/server |
| Beefy single process | 50–100 | 10–30GB | possible with large heap |
| **500 true concurrent** | 500 | **50–150GB class** | usually **multi-machine** |
| Cluster 10 × 50 | 500 | 10× mid boxes | **recommended** |

`captchaConcurrency(500)` is allowed by config, but one JVM with 500 HtmlUnit browsers will usually OOM or thrash unless the machine is huge.

### Single-process batch API

```java
YcClient client = YcClient.builder()
    .config(YcClientConfig.builder()
        .reconDir(reconDir)
        .captchaConcurrency(50)                    // true parallel captcha slots
        .captchaAcquireTimeout(Duration.ofHours(2))
        .httpMaxRequests(256)
        .httpMaxRequestsPerHost(128)
        .httpMaxIdleConnections(128)
        .readTimeout(Duration.ofSeconds(90))
        .build())
    .build();

try (YcSmsBatchExecutor batch = YcSmsBatchExecutor.builder()
        .client(client)
        .workers(50)                               // keep captcha slots busy
        .build()) {
    SmsBatchReport report = batch.sendSms(mobileList); // e.g. 1000 numbers
    System.out.println(report.successCount() + "/" + report.total()
            + " ok in " + report.elapsedMs() + "ms");
    report.failures().forEach(f ->
            System.out.println(f.mobile() + " -> " + f.error() + " / " + f.apiResult()));
}
```

### Reach 500 concurrent (recommended)

Run **N instances** (K8s / 多机 / 多进程), each with part of the number list:

```text
instance-1: captchaConcurrency=50, workers=50, numbers[0..99]
instance-2: captchaConcurrency=50, workers=50, numbers[100..199]
...
instance-10: ...  → 10 × 50 = 500 concurrent captcha solves
```

Same `reconDir` path (or same mounted volume) on every instance — read-only, safe to share.

### Single host “set 500” (only if RAM allows)

```java
.captchaConcurrency(500)
// JVM: -Xmx128g or higher; expect severe load on CPU + network
.workers(500)
```

## Captcha / HtmlUnit (default)

Captcha is **not** pure table-crypto alone. Default path: `AliyunCaptchaProvider` → `CaptchaHtmlUnitRuntime` (HtmlUnit Chrome-emulated full DOM):

1. Load the **real** page `https://uc.perfect99.com/loginAndRegistration` (blank data-HTML does not pump timers reliably in HtmlUnit)
2. Ensure host node `#nc`, eval recon `AliyunCaptcha.js` (FeiLin + dynamic SG load from CDN after Init)
3. `initAliyunCaptcha({mode:'embed', element:'#nc', ...})`
4. Wait for slider `#aliyunCaptcha-sliding-slider`, simulate mouse drag
5. Capture Verify request/response fields (`data` / `deviceToken` / `certifyId` / `securityToken`)

No Node process at runtime. Optional engine switch:

```java
YcClientConfig.builder().captchaEngine("htmlunit").build(); // default full DOM
YcClientConfig.builder().captchaEngine("graal").build();    // legacy minimal DOM + GraalJS
```

Graal notes (only when `captchaEngine=graal`):

- Context sets `engine.WarnInterpreterOnly=false` (avoids interpreter-only spam on stock JDK).
- On newer JDKs, native access warnings for Polyglot/Truffle may still appear; if needed:

```bash
java --enable-native-access=ALL-UNNAMED -jar app.jar
# or Maven surefire:
# MAVEN_OPTS / argLine: --enable-native-access=ALL-UNNAMED
```

## Env vars (tests / live)

| Variable | Used by | Purpose |
|----------|---------|---------|
| `YC_RECON_DIR` | runtime + tests | absolute path to recon scripts (`feilin094.js` present) |
| `YC_LIVE_CAPTCHA=1` | tests only | enables live Init/Verify / network captcha tests |
| `ALIYUN_CAPTCHA_KEY_ID` | **not used** | early plan only; not read by current SDK |
| `ALIYUN_CAPTCHA_KEY_SECRET` | **not used** | early plan only; not read by current SDK |

## Python mapping

| Python | Java |
|--------|------|
| `Perfect99Client` | `YcClient` |
| `crypto.cryto_login` | `CrytoLogin` |
| `gw_sign.gw_sign` | `GwSigner` |
| `AliyunCaptchaV3` / Node `feilin_helper*` | `AliyunCaptchaProvider` + HtmlUnit (`CaptchaHtmlUnitRuntime`, default) / Graal (`CaptchaJsRuntime`) |
| `curl_cffi` session | `HttpTransport` (`OkHttpTransport` / `Curl4jTransport`) |
| `server.py` FastAPI | not ported (callers embed SDK) |

## Build

```bash
cd java-sdk
mvn -q clean test install
# artifact: ~/.m2/repository/com/j/soul/yc/yc/0.1.0/
```
