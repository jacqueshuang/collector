# yc — Perfect99 pure-Java SDK

Maven library for perfect99: check-mobile · Aliyun captcha · SMS · SMS login · replace-mobile · batch SMS.  
No Spring dependency; Spring Boot apps construct a manual `@Bean`.

| | |
|---|---|
| Coordinate | `com.j.soul.yc:yc:0.1.0` |
| Java | 23+ (`maven.compiler.release=23`) |
| Captcha runtime | **HtmlUnit** full DOM (default) + recon scripts; optional GraalJS (no Node) |

## Dependency

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

---

## 公开 API 一览

### `YcClient`（主入口）

| 方法 | 上游路径 | 说明 |
|------|----------|------|
| `checkMobileAvailable(mobile)` | `GET /mobile/loginregister/checkMemberMobileAvailable` | 检查会员手机号是否可用 |
| `getCaptchaVerifyParam()` | 阿里云滑块（本地 HtmlUnit/Graal） | 生成 `captchaVerifyParam` |
| `getSmsCode(mobile, captchaVerifyParam)` | `POST /mobile/login/getSmsCodeNewV2` | 发短信，默认 `pathType=11`（重置） |
| `getSmsCode(mobile, captchaVerifyParam, pathType)` | 同上 | 显式 `SmsPathType` |
| `sendSms(mobile)` | check → captcha → getSmsCode(11) | 重置手机号完整发码 |
| `sendLoginSms(mobile)` | captcha → getSmsCode(5) | 登录页完整发码（**不** check） |
| `loginBySms(mobile, smsCode)` | `POST /login` | 登录页提交短信码（`auth_type=sms`） |
| `replaceMobile(ReplaceMobileRequest)` | `POST /mobile/openApi/replaceMobile` | 换绑手机号 |
| `close()` | — | 关闭自有 transport / captcha |
| `builder()` | — | 构造 `YcClientBuilder` |

### `YcSmsBatchExecutor`（批量发码）

| 方法 | 等价单发 | 说明 |
|------|----------|------|
| `sendSms(Collection<String>)` | `YcClient#sendSms` | 重置手机号批量（pathType=11） |
| `sendLoginSms(Collection<String>)` | `YcClient#sendLoginSms` | 登录页批量（pathType=5） |
| `workers()` / `maxBatchSize()` | — | 当前配置 |
| `close()` | — | 关闭自有线程池 |
| `builder()` | — | `.client` / `.workers` / `.maxBatchSize` / `.executor` |

### 模型 / 异常 / 枚举

| 类型 | 用途 |
|------|------|
| `ApiResult` | 业务 JSON：`code` / `msg`（兼容 `message`）/ `data` |
| `CaptchaResult` | `captchaVerifyParam`, `sceneId`, `certifyId`, `securityToken` |
| `ReplaceMobileRequest` | `cardNo`, `mobile`, `certNo`, `smsCode`, `accessToken` |
| `SmsPathType` | `RESET_PHONE(11)` · `LOGIN(5)` |
| `SmsBatchReport` | `results`, `total`, `successCount`, `failureCount`, `elapsedMs`, `failures` |
| `SmsTaskResult` | 单号结果：`mobile`, `success`, `apiResult`, `error`, `elapsedMs` |
| `YcException` | 基础设施失败；`getStep()` → `YcStep` |
| `YcStep` | `CHECK_MOBILE` · `CAPTCHA` · `SMS` · `REPLACE` · `LOGIN` · `HTTP` · `CRYPTO` · `SIGN` |
| `TransportType` | `OKHTTP`（默认）· `CURL4J` |
| `YcClientConfig` | 全部运行时配置（见下文表） |
| `YcClientBuilder` | `.config` / `.transport` / `.captchaProvider` / `.build` |

**错误约定**

- 上游业务拒绝（HTTP 成功、JSON `code != 200`）→ 返回 `ApiResult`，**不抛**
- 加密 / 传输 / 解析 / captcha 基础设施失败 → `YcException(step, message[, cause])`

---

## `YcClient` 接口详解

构造：

```java
try (YcClient client = YcClient.builder()
        .config(YcClientConfig.builder()
                .reconDir("/path/to/recon")   // captcha 必填
                .build())
        .build()) {
    // ...
}
```

可选注入：`.transport(HttpTransport)` · `.captchaProvider(CaptchaProvider)`（注入后 close 时不关外部实例）。

### 1. `checkMobileAvailable(String mobile)`

| | |
|---|---|
| Method / Path | `GET /mobile/loginregister/checkMemberMobileAvailable?mobile=&rnd=` |
| Body | 无 |
| Headers | channel / client / GW 签名 / UA / Origin / Referer |
| 返回 | `ApiResult` |
| 失败 step | `CHECK_MOBILE` |

### 2. `getCaptchaVerifyParam()`

| | |
|---|---|
| 实现 | `CaptchaProvider`（默认 `AliyunCaptchaProvider` + HtmlUnit） |
| 返回 | `CaptchaResult` |
| 失败 step | `CAPTCHA` |
| 耗时 | 通常 40–60s / 次；受 `captchaConcurrency` 槽位限制 |

```java
CaptchaResult c = client.getCaptchaVerifyParam();
String param = c.captchaVerifyParam();
```

### 3. `getSmsCode(...)`

| | |
|---|---|
| Method / Path | `POST /mobile/login/getSmsCodeNewV2` |
| Body | crytoLogin：`mobile`, `pathType`, `captchaVerifyParam`, `sceneId` |
| 重载 | `(mobile, captcha)` → pathType=11；`(mobile, captcha, SmsPathType)` |
| Referer | `LOGIN` → `isShow=login`；`RESET_PHONE` → config.referer |
| 返回 | `ApiResult` |
| 失败 step | `SMS` |

```java
// pathType=11
client.getSmsCode(mobile, captchaVerifyParam);
// pathType=5
client.getSmsCode(mobile, captchaVerifyParam, SmsPathType.LOGIN);
```

### 4. `sendSms(String mobile)` — 重置手机号发码

流程：`checkMobileAvailable` → 若 `code!=200` **直接返回**（不过 captcha）→ `getCaptchaVerifyParam` → `getSmsCode(..., RESET_PHONE)`。

### 5. `sendLoginSms(String mobile)` — 登录页发码

流程：`getCaptchaVerifyParam` → `getSmsCode(..., LOGIN)`。  
**不**调用 `checkMemberMobileAvailable`（对齐前端 `isShow=login`）。

### 6. `loginBySms(String mobile, String smsCode)` — 提交登录验证码

| | |
|---|---|
| Method / Path | `POST /login`（相对 baseUrl，默认 `https://uc.perfect99.com/api/login`） |
| Body | crytoLogin：`username=mobile`, `password=smsCode`, `auth_type=sms`, `grant_type=password` |
| Authorization | raw Basic `portal_app:perfect_portal`（**无** Bearer） |
| GW | path=`/login`；签名 accessToken = 完整 Basic 串（对齐前端 `ce()`） |
| Referer | `.../loginAndRegistration?isShow=login` |
| 返回 | `ApiResult`；成功时 `data` 常含 `access_token` 等；**SDK 不落 cookie** |
| 失败 step | `LOGIN` |
| 不支持 | `cardNo`、其他 `auth_type` |

```java
ApiResult sms = client.sendLoginSms(mobile);
// ... 从短信通道拿到 6 位码 ...
ApiResult login = client.loginBySms(mobile, smsCode);
if (login.getCode() != null && login.getCode() == 200) {
    // login.getData() 含 token 字段
}
```

### 7. `replaceMobile(ReplaceMobileRequest req)` — 换绑手机号

| | |
|---|---|
| Method / Path | `POST /mobile/openApi/replaceMobile` |
| Body | crytoLogin：`cardNo`, `mobile`, `certificatesNo`, `verificationCode` |
| Authorization | 若 `accessToken` 非空 → `Bearer {token}`，并参与 GW 签名 |
| 返回 | `ApiResult` |
| 失败 step | `REPLACE` |

```java
var r = client.replaceMobile(new ReplaceMobileRequest(
        cardNo, mobile, certNo, smsCode, accessToken));
```

---

## `YcSmsBatchExecutor`

在**共享** `YcClient` 上并行发码。并发受 `workers` 与 `captchaConcurrency` 双重限制。

```java
try (YcSmsBatchExecutor batch = YcSmsBatchExecutor.builder()
        .client(client)
        .workers(50)
        .maxBatchSize(500)          // 超限 → YcException(SMS) 且不做任何 captcha/HTTP
        // .executor(myPool)        // 可选：注入外部线程池（close 时不关）
        .build()) {

    SmsBatchReport reset = batch.sendSms(mobiles);       // pathType=11
    SmsBatchReport login = batch.sendLoginSms(mobiles);  // pathType=5

    System.out.println(reset.successCount() + "/" + reset.total()
            + " ok in " + reset.elapsedMs() + "ms");
    reset.failures().forEach(f ->
            System.out.println(f.mobile() + " -> " + f.error()));
}
```

| `SmsBatchReport` | |
|------------------|--|
| `results()` | 与输入顺序一致的 `List<SmsTaskResult>` |
| `total()` / `successCount()` / `failureCount()` | 计数 |
| `elapsedMs()` | 整批耗时 |
| `failures()` | 失败子集 |

| `SmsTaskResult` | |
|-----------------|--|
| `mobile()` | 号码 |
| `success()` | 是否未抛异常且拿到 `ApiResult` |
| `apiResult()` | 业务结果（失败时可能为 null） |
| `error()` | 异常信息 |
| `elapsedMs()` | 单号耗时 |

---

## 业务场景对照

| 场景 | 推荐调用链 |
|------|------------|
| 重置手机号发短信 | `sendSms(mobile)` 或 `batch.sendSms(...)` |
| 登录页发短信 | `sendLoginSms(mobile)` 或 `batch.sendLoginSms(...)` |
| 登录页提交验证码 | `loginBySms(mobile, smsCode)`（码来自外部短信通道） |
| 换绑手机号 | 先自行发码/收码 → `replaceMobile(...)` |
| 自组发码（已有 captcha） | `getSmsCode(mobile, param, pathType)` |
| 仅检查手机号 | `checkMobileAvailable(mobile)` |
| 仅过滑块 | `getCaptchaVerifyParam()` |

---

## Spring Boot 使用说明

SDK **无 Starter**。`YcClient` / `YcSmsBatchExecutor` **必须单例**，禁止请求内反复 `builder().build()`。

### application.yml

```yaml
yc:
  recon-dir: /data/perfect99/recon   # 必填：含 AliyunCaptcha.js、feilin094.js
  captcha-concurrency: 50
  captcha-acquire-timeout: 2h
  batch-workers: 50
  max-batch-size: 500
  http-max-requests: 256
  http-max-requests-per-host: 128
```

### 配置类

```java
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

### 控制器示例

```java
@RestController
@RequestMapping("/api/sms")
public class SmsController {

    private final YcClient ycClient;
    private final YcSmsBatchExecutor batchExecutor;

    public SmsController(YcClient ycClient, YcSmsBatchExecutor batchExecutor) {
        this.ycClient = ycClient;
        this.batchExecutor = batchExecutor;
    }

    /** 重置手机号发码：check → captcha → pathType=11 */
    @PostMapping("/send")
    public Map<String, Object> send(@RequestBody Map<String, String> body) {
        return toBody(ycClient.sendSms(body.get("mobile")));
    }

    /** 登录页发码：captcha → pathType=5 */
    @PostMapping("/send-login")
    public Map<String, Object> sendLogin(@RequestBody Map<String, String> body) {
        return toBody(ycClient.sendLoginSms(body.get("mobile")));
    }

    /** 登录页提交短信验证码 */
    @PostMapping("/login-sms")
    public Map<String, Object> loginSms(@RequestBody Map<String, String> body) {
        return toBody(ycClient.loginBySms(body.get("mobile"), body.get("smsCode")));
    }

    /** 换绑手机号 */
    @PostMapping("/replace-mobile")
    public Map<String, Object> replace(@RequestBody Map<String, String> body) {
        var r = ycClient.replaceMobile(new ReplaceMobileRequest(
                body.get("cardNo"),
                body.get("mobile"),
                body.get("certNo"),
                body.get("smsCode"),
                body.get("accessToken")));
        return toBody(r);
    }

    @PostMapping("/batch")
    public Map<String, Object> batch(@RequestBody List<String> mobiles) {
        return toBatchBody(batchExecutor.sendSms(mobiles));
    }

    @PostMapping("/batch-login")
    public Map<String, Object> batchLogin(@RequestBody List<String> mobiles) {
        return toBatchBody(batchExecutor.sendLoginSms(mobiles));
    }

    private static Map<String, Object> toBody(ApiResult r) {
        return Map.of(
                "code", r.getCode(),
                "msg", r.getMsg() == null ? "" : r.getMsg(),
                "data", r.getData() == null ? "" : r.getData());
    }

    private static Map<String, Object> toBatchBody(SmsBatchReport report) {
        return Map.of(
                "total", report.total(),
                "success", report.successCount(),
                "failure", report.failureCount(),
                "elapsedMs", report.elapsedMs(),
                "failures", report.failures().stream()
                        .map(f -> Map.of(
                                "mobile", f.mobile(),
                                "error", f.error() == null ? "" : f.error(),
                                "code", f.apiResult() == null ? "" : String.valueOf(f.apiResult().getCode())))
                        .toList());
    }
}
```

### 统一异常处理

```java
@RestControllerAdvice
public class YcExceptionHandler {

    @ExceptionHandler(YcException.class)
    public ResponseEntity<Map<String, Object>> handle(YcException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "step", e.getStep().name(),
                "message", e.getMessage()));
    }
}
```

### Spring 注意点

1. **单例 Bean**：`YcClient` / `YcSmsBatchExecutor` 各一个。
2. **超时**：单次发码常 40–60s；同步 HTTP 需调大网关超时；大批量建议异步任务。
3. **内存**：`captcha-concurrency` 按机器调（每路 HtmlUnit 约 100–300MB）。
4. **部署**：必须挂载可读 `recon-dir`。
5. 同 `reconDir` 多线程 / 多实例安全（只读脚本）。

---

## Config (`YcClientConfig`)

| Field | Default | Notes |
|-------|---------|-------|
| `baseUrl` | `https://uc.perfect99.com/api` | 业务 API 根 |
| `userAgent` | Chrome 149 UA | |
| `channel` | `pc` | header |
| `client` | `mall` | header |
| `referer` | loginAndRegistration（resetPhone） | 默认业务 Referer |
| `origin` | `https://uc.perfect99.com` | |
| `gwKey` | production key | GW 签名 |
| `gwClient` | `mall_pc` | GW header |
| `rsaPublicKeyBase64` | production RSA pubkey | crytoLogin |
| `captchaPrefix` | `1uu8u2` | 阿里云 host 前缀 |
| `sceneId` | `1pn9314j` | captcha scene |
| `transportType` | `OKHTTP` | `OKHTTP` \| `CURL4J` |
| `connectTimeout` | 10s | |
| `readTimeout` | 30s | 发码建议调到 90s+ |
| `proxyHost` / `proxyPort` | empty | 可选 HTTP 代理 |
| `reconDir` | null → auto | captcha 脚本目录（**过滑块必填**） |
| `captchaEngine` | `htmlunit` | `htmlunit` \| `graal` |
| `captchaConcurrency` | CPU 2..8 | 并行滑块会话数 |
| `captchaAcquireTimeout` | 120s | 抢槽位最长等待 |
| `httpMaxRequests` | 64 | OkHttp 全局并发 |
| `httpMaxRequestsPerHost` | 32 | OkHttp 单 host 并发 |
| `httpMaxIdleConnections` | 32 | OkHttp 空闲连接 |

`reconDir` 需含 `feilin094.js`（并期望 `sg029.js`、`AliyunCaptcha.js`）。  
未设置时解析顺序：`YC_RECON_DIR` → `../recon`、`../../recon`、`recon`。

**无** `aliyunKeyId` / `aliyunKeySecret` — captcha 走前端 FeiLin/Aliyun JS，不是 OpenAPI 密钥。

---

## Transport

```java
YcClientConfig.builder().transportType(TransportType.OKHTTP).build(); // 默认
YcClientConfig.builder().transportType(TransportType.CURL4J).build();  // 系统 curl CLI
// 或
YcClient.builder().transport(myHttpTransport).config(cfg).build();
```

| 实现 | 说明 |
|------|------|
| `OkHttpTransport` | 默认，连接池；高并发调 `httpMaxRequests*` |
| `Curl4jTransport` | `ProcessBuilder` 调系统 `curl`（需在 `PATH`）；并发场景优先 OkHttp |

---

## 高并发（批量）

Captcha 是瓶颈（~40–60s / 次，~100–300MB / HtmlUnit 会话）。

| Setup | Concurrent captcha | 粗算内存 | 说明 |
|-------|--------------------|----------|------|
| 默认单进程 | 2–8 | 数 GB | 本机安全默认 |
| 单机加强 | 50–100 | 10–30GB | 大堆可行 |
| 真 500 并发 | 500 | 50–150GB 级 | 通常多机 |
| 集群 10×50 | 500 | 10 台中型 | **推荐** |

```java
YcClient client = YcClient.builder()
    .config(YcClientConfig.builder()
        .reconDir(reconDir)
        .captchaConcurrency(50)
        .captchaAcquireTimeout(Duration.ofHours(2))
        .httpMaxRequests(256)
        .httpMaxRequestsPerHost(128)
        .httpMaxIdleConnections(128)
        .readTimeout(Duration.ofSeconds(90))
        .build())
    .build();

try (YcSmsBatchExecutor batch = YcSmsBatchExecutor.builder()
        .client(client)
        .workers(50)
        .maxBatchSize(500)
        .build()) {
    SmsBatchReport report = batch.sendSms(mobileList);
}
```

`maxBatchSize` 默认 **1000**：非空号码数超限时立即 `YcException(SMS, "batch size N exceeds maxBatchSize M")`，不做 captcha/HTTP。

多机：每实例 `captchaConcurrency=50`、`workers=50`，切分号码列表；`reconDir` 只读可共享。

---

## Captcha / HtmlUnit

默认：`AliyunCaptchaProvider` → `CaptchaHtmlUnitRuntime`：

1. 打开真实页 `https://uc.perfect99.com/loginAndRegistration`
2. 注入 recon `AliyunCaptcha.js`，Init 后拉动态 SG
3. `initAliyunCaptcha({mode:'embed', ...})`
4. 模拟拖动滑块
5. 捕获 Verify 字段 → 组装 `captchaVerifyParam`

运行时**不**起 Node。引擎切换：

```java
.captchaEngine("htmlunit") // 默认
.captchaEngine("graal")    // 最小 DOM + GraalJS
```

Graal 时如需：

```bash
java --enable-native-access=ALL-UNNAMED -jar app.jar
```

---

## Env vars（测试 / live）

| Variable | 用途 |
|----------|------|
| `YC_RECON_DIR` | recon 脚本绝对路径 |
| `YC_LIVE_CAPTCHA=1` | 启用 live captcha 测试 |
| `YC_LIVE_SMS=1` | live `sendSms`（pathType=11）；需 `YC_TEST_MOBILE` |
| `YC_LIVE_LOGIN_SMS=1` | live `sendLoginSms`（pathType=5）；需 `YC_TEST_MOBILE` |
| `YC_LIVE_LOGIN=1` | live `loginBySms`；需 `YC_TEST_MOBILE` + `YC_SMS_CODE` |
| `YC_SMS_CODE` | live 登录验证码 |
| `YC_TEST_MOBILE` | live 测试手机号 |
| `ALIYUN_CAPTCHA_KEY_ID` / `_SECRET` | **未使用**（早期方案遗留） |

---

## Python mapping

| Python | Java |
|--------|------|
| `Perfect99Client` | `YcClient` |
| `crypto.cryto_login` | `CrytoLogin` |
| `gw_sign.gw_sign` | `GwSigner` |
| Aliyun / Node helper | `AliyunCaptchaProvider` + HtmlUnit / Graal |
| `curl_cffi` session | `HttpTransport`（OkHttp / Curl4j） |
| FastAPI `server.py` | 未移植（业务直接嵌 SDK） |

---

## Build

```bash
cd java-sdk
mvn -q clean test install
# artifact: ~/.m2/repository/com/j/soul/yc/yc/0.1.0/
```
