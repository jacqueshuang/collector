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

## Spring Boot 使用说明

SDK **无 Spring Boot Starter**，在业务项目中手动注册 `@Bean`。  
**`YcClient` / `YcSmsBatchExecutor` 必须单例**，不要在请求里反复 `builder().build()`。

### application.yml

```yaml
yc:
  recon-dir: /data/perfect99/recon   # 必填：含 AliyunCaptcha.js、feilin094.js
  captcha-concurrency: 50            # 同时过滑块的路数（受内存限制，每路约 100–300MB）
  captcha-acquire-timeout: 2h        # 抢不到滑块槽位的最长等待
  batch-workers: 50                  # 批量发送线程数，建议 ≥ captcha-concurrency
  max-batch-size: 500                # 单次批量号码上限，超过直接报错
  http-max-requests: 256
  http-max-requests-per-host: 128
```

### 配置类

```java
package com.example.config;

import com.j.soul.yc.YcClient;
import com.j.soul.yc.batch.YcSmsBatchExecutor;
import com.j.soul.yc.config.YcClientConfig;
import com.j.soul.yc.http.TransportType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class YcConfig {

    @Bean(destroyMethod = "close")
    public YcClient ycClient(
            @Value("${yc.recon-dir}") String reconDir,
            @Value("${yc.captcha-concurrency:50}") int captchaConcurrency,
            @Value("${yc.captcha-acquire-timeout:2h}") Duration captchaAcquireTimeout,
            @Value("${yc.http-max-requests:256}") int httpMaxRequests,
            @Value("${yc.http-max-requests-per-host:128}") int httpMaxPerHost
    ) {
        return YcClient.builder()
                .config(YcClientConfig.builder()
                        .reconDir(reconDir)
                        .captchaEngine("htmlunit")
                        .transportType(TransportType.OKHTTP)
                        .captchaConcurrency(captchaConcurrency)
                        .captchaAcquireTimeout(captchaAcquireTimeout)
                        .httpMaxRequests(httpMaxRequests)
                        .httpMaxRequestsPerHost(httpMaxPerHost)
                        .httpMaxIdleConnections(httpMaxPerHost)
                        .readTimeout(Duration.ofSeconds(90))
                        .build())
                .build();
    }

    @Bean(destroyMethod = "close")
    public YcSmsBatchExecutor ycSmsBatchExecutor(
            YcClient ycClient,
            @Value("${yc.batch-workers:50}") int workers,
            @Value("${yc.max-batch-size:500}") int maxBatchSize
    ) {
        return YcSmsBatchExecutor.builder()
                .client(ycClient)
                .workers(workers)
                .maxBatchSize(maxBatchSize)
                .build();
    }
}
```

### 单发短信

```java
@RestController
@RequestMapping("/api/sms")
public class SmsController {

    private final YcClient ycClient;

    public SmsController(YcClient ycClient) {
        this.ycClient = ycClient;
    }

    @PostMapping("/send")
    public Map<String, Object> send(@RequestBody Map<String, String> body) {
        // check → captcha → getSmsCode；单次约 40–60s，注意网关超时
        var r = ycClient.sendSms(body.get("mobile"));
        return Map.of(
                "code", r.getCode(),
                "msg", r.getMsg() == null ? "" : r.getMsg(),
                "data", r.getData() == null ? "" : r.getData()
        );
    }
}
```

其他单接口：`checkMobileAvailable` · `getCaptchaVerifyParam` · `getSmsCode` · `replaceMobile`。

### 批量发送（多号码并行）

```java
@RestController
@RequestMapping("/api/sms")
public class SmsBatchController {

    private final YcSmsBatchExecutor batchExecutor;

    public SmsBatchController(YcSmsBatchExecutor batchExecutor) {
        this.batchExecutor = batchExecutor;
    }

    @PostMapping("/batch")
    public Map<String, Object> batch(@RequestBody List<String> mobiles) {
        // 超过 maxBatchSize → YcException，不会进入滑块/HTTP
        var report = batchExecutor.sendSms(mobiles);
        return Map.of(
                "total", report.total(),
                "success", report.successCount(),
                "failure", report.failureCount(),
                "elapsedMs", report.elapsedMs(),
                "failures", report.failures().stream()
                        .map(f -> Map.of(
                                "mobile", f.mobile(),
                                "error", f.error() == null ? "" : f.error(),
                                "code", f.apiResult() == null ? "" : String.valueOf(f.apiResult().getCode())
                        ))
                        .toList()
        );
    }
}
```

### 统一异常处理

```java
@RestControllerAdvice
public class YcExceptionHandler {

    @ExceptionHandler(YcException.class)
    public ResponseEntity<Map<String, Object>> handle(YcException e) {
        // 例如：batch size 超过 maxBatchSize、captcha saturated、HTTP 失败
        return ResponseEntity.badRequest().body(Map.of(
                "step", e.getStep().name(),
                "message", e.getMessage()
        ));
    }
}
```

### 参数对照（Spring 侧）

| yml / 配置 | 含义 |
|------------|------|
| `yc.recon-dir` | 验证码 JS 目录（只读，多实例可共用） |
| `yc.captcha-concurrency` | **同时**过滑块的浏览器会话数 |
| `yc.batch-workers` | 提交 `sendSms` 的线程数，建议 ≥ concurrency |
| `yc.max-batch-size` | **一次批量接口**最多几个号，超限直接报错 |
| `yc.captcha-acquire-timeout` | 等滑块槽位的最长时间 |

例：`max-batch-size=500` 且 `captcha-concurrency=50` → 一次最多接 500 号，但同一时刻约 50 路在过滑块，其余排队。

### Spring Boot 注意点

1. **单例 Bean**：`YcClient` / `YcSmsBatchExecutor` 各一个；禁止 per-request 新建。
2. **超时**：单次 `sendSms` 常 40–60s；同步 HTTP 接口需调大网关/Tomcat 超时，大批量建议改异步任务。
3. **内存**：`captcha-concurrency` 按机器调；不要轻易上 500 路单机 HtmlUnit。
4. **部署**：容器/机器上必须挂载可读的 `recon-dir`。
5. **同 reconDir 多线程/多实例**：安全（只读脚本）。

### Minimal non-Spring usage

```java
try (YcClient client = YcClient.builder()
        .config(YcClientConfig.builder()
                .reconDir("/path/to/recon")
                .build())
        .build()) {
    ApiResult r = client.sendSms("1xxxxxxxxxx");
}
```

Public API: `checkMobileAvailable` · `getCaptchaVerifyParam` · `getSmsCode` · `sendSms` · `replaceMobile` · 批量 `YcSmsBatchExecutor#sendSms`.

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
        .maxBatchSize(500)                         // hard cap: >500 numbers → throw immediately
        .build()) {
    SmsBatchReport report = batch.sendSms(mobileList); // e.g. 1000 numbers
    System.out.println(report.successCount() + "/" + report.total()
            + " ok in " + report.elapsedMs() + "ms");
    report.failures().forEach(f ->
            System.out.println(f.mobile() + " -> " + f.error() + " / " + f.apiResult()));
}
```

`maxBatchSize` (default **1000**): if non-blank mobile count exceeds it, `sendSms` throws `YcException(SMS, "batch size N exceeds maxBatchSize M")` **before** any captcha/HTTP work.

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
