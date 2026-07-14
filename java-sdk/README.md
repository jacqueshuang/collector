# yc — Perfect99 纯 Java SDK

面向 perfect99 业务的 Maven 库：检查手机号 · 阿里云滑块 · 发短信 · 短信登录 · 换绑手机号 · 批量发码。  
**无 Spring 依赖**；Spring Boot 项目手动注册 `@Bean` 即可。

| | |
|---|---|
| 坐标 | `com.j.soul.yc:yc:0.1.0` |
| Java | 23+（`maven.compiler.release=23`） |
| 滑块运行时 | 默认 **HtmlUnit** 全 DOM + recon 脚本；可选 GraalJS（无 Node） |

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

业务单接口与组合流程都从这里调用。构造后建议单例复用。

| 方法 | 中文说明 | 上游路径 |
|------|----------|----------|
| `checkMobileAvailable(mobile)` | **检查会员手机号是否可用**（重置流程前置） | `GET .../checkMemberMobileAvailable` |
| `getCaptchaVerifyParam()` | **过阿里云滑块**，拿到发短信用的 captcha 参数 | 本地 HtmlUnit/Graal |
| `getSmsCode(mobile, captcha)` | **只发短信**（默认重置场景 pathType=11；需自备 captcha） | `POST .../getSmsCodeNewV2` |
| `getSmsCode(mobile, captcha, pathType)` | **只发短信**并指定场景（11 重置 / 5 登录） | 同上 |
| `sendSms(mobile)` | **重置手机号一键发码**：检查 → 滑块 → 发短信(11) | 组合流程 |
| `sendLoginSms(mobile)` | **登录页一键发码**：滑块 → 发短信(5)，不检查手机号 | 组合流程 |
| `loginBySms(mobile, smsCode)` | **登录页提交短信验证码**完成短信登录 | `POST /login` |
| `replaceMobile(req)` | **换绑手机号**（卡号+证件+短信码） | `POST .../replaceMobile` |
| `close()` | 关闭本客户端自建的 HTTP / captcha 资源 | — |
| `builder()` | 创建构建器 | — |

### `YcSmsBatchExecutor`（批量发码）

在共享 `YcClient` 上并行发码；**不负责登录提交**（登录码来自外部短信通道）。

| 方法 | 中文说明 | 等价单发 |
|------|----------|----------|
| `sendSms(mobiles)` | 批量重置手机号发码（含 check） | `YcClient#sendSms` |
| `sendLoginSms(mobiles)` | 批量登录页发码（不含 check） | `YcClient#sendLoginSms` |
| `workers()` | 查看工作线程数 | — |
| `maxBatchSize()` | 查看单次批量号码上限 | — |
| `close()` | 关闭自有线程池 | — |
| `builder()` | 构建器：`.client` / `.workers` / `.maxBatchSize` / `.executor` | — |

### 模型 / 异常 / 枚举

| 类型 | 中文说明 |
|------|----------|
| `ApiResult` | 上游业务响应：`code` 状态码、`msg` 提示（兼容 `message`）、`data` 数据 |
| `CaptchaResult` | 滑块结果：发码参数、sceneId、certifyId、securityToken |
| `ReplaceMobileRequest` | 换绑入参：卡号、手机号、证件号、短信码、可选 accessToken |
| `SmsPathType` | 发码场景：`RESET_PHONE(11)` 重置；`LOGIN(5)` 登录页 |
| `SmsBatchReport` | 批量汇总：结果列表、成功/失败数、总耗时 |
| `SmsTaskResult` | 批量中单号结果：是否成功、业务结果、错误信息、耗时 |
| `YcException` | 基础设施异常；`getStep()` 标明失败阶段 |
| `YcStep` | 阶段枚举：检查手机号 / 滑块 / 短信 / 换绑 / 登录 / HTTP / 加密 / 签名 |
| `TransportType` | HTTP 实现：`OKHTTP`（默认）· `CURL4J` |
| `YcClientConfig` | 客户端全部运行时配置 |
| `YcClientBuilder` | 组装 config / 自定义 transport / captchaProvider |

**错误约定**

- 上游业务拒绝（HTTP 成功、JSON `code != 200`）→ 返回 `ApiResult`，**不抛异常**
- 加密 / 传输 / 解析 / 滑块等基础设施失败 → 抛 `YcException(step, message[, cause])`

---

## `YcClient` 接口详解

### 构造

```java
try (YcClient client = YcClient.builder()
        .config(YcClientConfig.builder()
                .reconDir("/path/to/recon")   // 过滑块必填
                .build())
        .build()) {
    // ...
}
```

可选注入：`.transport(HttpTransport)` · `.captchaProvider(CaptchaProvider)`  
（注入外部实现时，`close()` **不会**关闭它们。）

---

### 1. `checkMobileAvailable(String mobile)` — 检查手机号

**中文说明**：查询该手机号在会员体系中是否可用。重置手机号发码前会自动调用；登录页发码**不需要**。

| 项 | 内容 |
|----|------|
| 上游 | `GET /mobile/loginregister/checkMemberMobileAvailable?mobile=&rnd=` |
| 请求体 | 无 |
| 请求头 | channel / client / GW 签名 / UA / Origin / Referer |
| 返回 | `ApiResult`；`code==200` 表示可用 |
| 失败 step | `CHECK_MOBILE` |

---

### 2. `getCaptchaVerifyParam()` — 过滑块

**中文说明**：在本地完成阿里云滑块，生成后续发短信必须携带的 `captchaVerifyParam`。  
单次通常 **40–60 秒**，并行数受 `captchaConcurrency` 限制。

| 项 | 内容 |
|----|------|
| 实现 | 默认 HtmlUnit 全 DOM（可选 Graal） |
| 返回 | `CaptchaResult`（业务发码用 `captchaVerifyParam()`） |
| 失败 step | `CAPTCHA` |

```java
CaptchaResult c = client.getCaptchaVerifyParam();
String param = c.captchaVerifyParam();
```

---

### 3. `getSmsCode(...)` — 只发短信

**中文说明**：在**已有**滑块参数的前提下，单独请求短信验证码。  
不负责过滑块；完整发码请用 `sendSms` / `sendLoginSms`。

| 项 | 内容 |
|----|------|
| 上游 | `POST /mobile/login/getSmsCodeNewV2` |
| 请求体 | crytoLogin 加密：`mobile` · `pathType` · `captchaVerifyParam` · `sceneId` |
| 重载 | `(mobile, captcha)` → 默认 pathType=11；三参数可指定 `SmsPathType` |
| Referer | 登录场景用 `isShow=login`；重置场景用配置中的 referer |
| 返回 | `ApiResult` |
| 失败 step | `SMS` |

```java
// 重置手机号 pathType=11
client.getSmsCode(mobile, captchaVerifyParam);
// 登录页 pathType=5
client.getSmsCode(mobile, captchaVerifyParam, SmsPathType.LOGIN);
```

| `SmsPathType` | 值 | 中文含义 |
|---------------|----|----------|
| `RESET_PHONE` | 11 | 重置手机号 / 会员卡相关发码 |
| `LOGIN` | 5 | 登录页发码 |

---

### 4. `sendSms(String mobile)` — 重置手机号一键发码

**中文说明**：重置手机号场景的**完整发码链路**：先检查手机号 → 过滑块 → 发短信（pathType=11）。  
若检查结果 `code != 200`，**直接返回检查结果**，不再过滑块、不发短信。

```
checkMobileAvailable → (code!=200 则返回) → getCaptchaVerifyParam → getSmsCode(pathType=11)
```

---

### 5. `sendLoginSms(String mobile)` — 登录页一键发码

**中文说明**：登录页场景的**完整发码链路**：过滑块 → 发短信（pathType=5）。  
对齐前端 `isShow=login`，**不**调用检查手机号。  
用户收到短信后，用 `loginBySms` 提交验证码登录。

```
getCaptchaVerifyParam → getSmsCode(pathType=5)
```

---

### 6. `loginBySms(String mobile, String smsCode)` — 提交登录验证码

**中文说明**：把登录页收到的 6 位短信码提交给门户，完成**短信登录**。  
验证码必须由外部短信通道提供（SDK 无法代收短信）。成功时 `data` 里常有 `access_token` 等字段；**SDK 不写 cookie、不缓存 token**。

| 项 | 内容 |
|----|------|
| 上游 | `POST /login`（默认完整 URL：`https://uc.perfect99.com/api/login`） |
| 明文业务字段 | `username=手机号` · `password=短信码` · `auth_type=sms` · `grant_type=password`（**无 cardNo**） |
| 请求体 | 上述字段经 crytoLogin 加密为 `key` + `data` 表单 |
| Authorization | 门户 raw Basic（`portal_app:perfect_portal`），**不加** Bearer |
| GW 签名 | path=`/login`；accessToken 使用完整 Basic 串（对齐前端） |
| Referer | `.../loginAndRegistration?isShow=login` |
| 返回 | `ApiResult` |
| 失败 step | `LOGIN` |
| 不支持 | 会员卡密码登录、bindingmobile、cardNo 等其他 auth_type |

```java
ApiResult sms = client.sendLoginSms(mobile);
// ... 从短信通道拿到 6 位码 ...
ApiResult login = client.loginBySms(mobile, smsCode);
if (login.getCode() != null && login.getCode() == 200) {
    // login.getData() 含 token 等字段
}
```

---

### 7. `replaceMobile(ReplaceMobileRequest req)` — 换绑手机号

**中文说明**：用会员卡号 + 证件号 + 短信验证码，把账号绑定到新手机号。  
短信码需业务侧自行发码/收码后填入；若已登录可带 `accessToken`。

| 项 | 内容 |
|----|------|
| 上游 | `POST /mobile/openApi/replaceMobile` |
| 请求体 | crytoLogin：`cardNo` · `mobile` · `certificatesNo` · `verificationCode` |
| Authorization | `accessToken` 非空时：`Bearer {token}`，并参与 GW 签名 |
| 返回 | `ApiResult` |
| 失败 step | `REPLACE` |

| `ReplaceMobileRequest` 字段 | 中文含义 |
|-----------------------------|----------|
| `cardNo` | 会员卡号 |
| `mobile` | 新手机号 |
| `certNo` | 证件号（上游名 certificatesNo） |
| `smsCode` | 短信验证码（上游名 verificationCode） |
| `accessToken` | 可选登录 token |

```java
var r = client.replaceMobile(new ReplaceMobileRequest(
        cardNo, mobile, certNo, smsCode, accessToken));
```

---

## `YcSmsBatchExecutor` — 批量发码

**中文说明**：把多个手机号丢进线程池，每个号走单发完整链路。  
并发同时受 `workers`（本类）和 `captchaConcurrency`（滑块）限制。  
只负责**发码**，不负责 `loginBySms`。

```java
try (YcSmsBatchExecutor batch = YcSmsBatchExecutor.builder()
        .client(client)
        .workers(50)
        .maxBatchSize(500)          // 超限 → YcException(SMS)，且不做任何 captcha/HTTP
        // .executor(myPool)        // 可选：注入外部线程池（close 时不关）
        .build()) {

    SmsBatchReport reset = batch.sendSms(mobiles);       // 重置 pathType=11
    SmsBatchReport login = batch.sendLoginSms(mobiles);  // 登录 pathType=5

    System.out.println(reset.successCount() + "/" + reset.total()
            + " ok in " + reset.elapsedMs() + "ms");
    reset.failures().forEach(f ->
            System.out.println(f.mobile() + " -> " + f.error()));
}
```

| `SmsBatchReport` 方法 | 中文说明 |
|-----------------------|----------|
| `results()` | 与输入非空号码**同序**的单号结果列表 |
| `total()` | 处理的号码总数 |
| `successCount()` | 成功数 |
| `failureCount()` | 失败数 |
| `elapsedMs()` | 整批耗时（毫秒） |
| `failures()` | 仅失败项 |

| `SmsTaskResult` 方法 | 中文说明 |
|----------------------|----------|
| `mobile()` | 手机号 |
| `success()` | 是否业务成功（拿到 `ApiResult` 且 `code==200`） |
| `apiResult()` | 业务结果；异常失败时可能为 null |
| `error()` | 异常信息 |
| `elapsedMs()` | 单号耗时 |

---

## 业务场景对照

| 业务场景 | 中文说明 | 推荐调用 |
|----------|----------|----------|
| 重置手机号发短信 | 改绑前验证新号，需先检查手机号 | `sendSms(mobile)` 或 `batch.sendSms(...)` |
| 登录页发短信 | 快捷登录/非会员登录发码 | `sendLoginSms(mobile)` 或 `batch.sendLoginSms(...)` |
| 登录页提交验证码 | 用户输入短信码完成登录 | `loginBySms(mobile, smsCode)` |
| 换绑手机号 | 卡号+证件+短信码换绑 | 自行收码后 `replaceMobile(...)` |
| 自组发码 | 已有 captcha，只想发短信 | `getSmsCode(mobile, param, pathType)` |
| 仅检查手机号 | 不做滑块、不发短信 | `checkMobileAvailable(mobile)` |
| 仅过滑块 | 调试/复用 captcha 参数 | `getCaptchaVerifyParam()` |

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
