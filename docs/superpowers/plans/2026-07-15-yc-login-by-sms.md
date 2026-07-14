# YC loginBySms Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `YcClient` 上实现登录页短信验证码提交 `loginBySms(mobile, smsCode)`，对齐前端 `POST /api/login` + `auth_type=sms`。

**Architecture:** 在现有 facade 增加薄方法：crytoLogin 加密明文 payload，局部组装 headers（raw Basic + GW 签名把 Basic 整段当 accessToken），POST `{baseUrl}/login`，解析为 `ApiResult`。不改 `replaceMobile` / batch / captcha。

**Tech Stack:** Java 23, Maven, JUnit 5, 现有 `CrytoLogin` / `GwSigner` / `HttpTransport` / `ApiResult`。

**Spec:** `docs/superpowers/specs/2026-07-15-yc-login-by-sms-design.md`

## Global Constraints

- 仅 `auth_type=sms`；**不带** `cardNo`
- Basic 固定：`Basic cG9ydGFsX2FwcDpwZXJmZWN0X3BvcnRhbA==`（`portal_app:perfect_portal`）
- Authorization **无** Bearer 前缀
- GW 签名 path=`/login`，`accessTokenOrNull` = 完整 Basic 字符串（对齐前端 `ce()`）
- Referer = `https://uc.perfect99.com/loginAndRegistration?isShow=login`
- 业务失败返回 `ApiResult`；infra 失败抛 `YcException(YcStep.LOGIN, ...)`
- 禁止引入 Spring；仅针对性改动
- 工作目录：仓库根下 `java-sdk/` 跑 Maven

## File map

| File | Role |
|------|------|
| `java-sdk/src/main/java/com/j/soul/yc/exception/YcStep.java` | 增加 `LOGIN` |
| `java-sdk/src/main/java/com/j/soul/yc/YcClient.java` | `PATH_LOGIN`、`PORTAL_BASIC_AUTH`、`loginBySms` |
| `java-sdk/src/test/java/com/j/soul/yc/YcClientTest.java` | mock 单测 |
| `java-sdk/src/test/java/com/j/soul/yc/LiveSendSmsIT.java` | 可选 env 门控 live 登录 |
| `java-sdk/README.md` | 使用说明 |

---

### Task 1: `loginBySms` + unit tests

**Files:**
- Modify: `java-sdk/src/main/java/com/j/soul/yc/exception/YcStep.java`
- Modify: `java-sdk/src/main/java/com/j/soul/yc/YcClient.java`
- Modify: `java-sdk/src/test/java/com/j/soul/yc/YcClientTest.java`

**Interfaces:**
- Consumes: `CrytoLogin.encrypt`, `GwSigner.sign`, `HttpTransport.execute`, `ApiResult`, existing `LOGIN_REFERER` / form helpers on `YcClient`
- Produces: `YcStep.LOGIN`；`public ApiResult loginBySms(String mobile, String smsCode)`

- [ ] **Step 1: Write failing unit tests**

在 `YcClientTest.java` 末尾（`assertHasBusinessHeaders` 之前或之后）增加：

```java
@Test
void loginBySms_postsEncryptedFormWithBasicAuth() {
    RecordingTransport transport = new RecordingTransport(req ->
            json(200, "{\"code\":200,\"msg\":\"login-ok\",\"data\":{\"access_token\":\"tok\"}}"));

    try (YcClient client = YcClient.builder()
            .config(YcClientConfig.builder().baseUrl("https://example.test/api").build())
            .transport(transport)
            .captchaProvider(() -> new CaptchaResult("x", "s", "c", "t"))
            .build()) {
        ApiResult r = client.loginBySms("13900003333", "654321");
        assertEquals(200, r.getCode());
        assertEquals("login-ok", r.getMsg());
        assertNotNull(r.getData());
    }

    assertEquals(1, transport.requests.size());
    HttpRequest post = transport.requests.getFirst();
    assertEquals("POST", post.method());
    assertTrue(post.url().endsWith("/login"), post.url());
    assertEquals(
            "Basic cG9ydGFsX2FwcDpwZXJmZWN0X3BvcnRhbA==",
            post.headers().get("Authorization"));
    assertFalse(post.headers().get("Authorization").startsWith("Bearer"));
    assertTrue(post.headers().get("Referer").contains("isShow=login"));
    assertHasBusinessHeaders(post.headers());
    assertNotNull(post.headers().get("GW-Timestamp"));
    assertNotNull(post.headers().get("GW-Nonce"));
    assertNotNull(post.headers().get("GW-Client"));
    assertTrue(post.headers().get("Content-Type").startsWith("application/x-www-form-urlencoded"));
    String body = new String(post.body(), StandardCharsets.UTF_8);
    assertTrue(body.contains("key="));
    assertTrue(body.contains("data="));
    assertFalse(body.contains("username="));
    assertFalse(body.contains("password="));
    assertFalse(body.contains("auth_type="));
}

@Test
void loginBySms_businessReject_returnsApiResult() {
    RecordingTransport transport = new RecordingTransport(req ->
            json(200, "{\"code\":401,\"msg\":\"bad code\",\"data\":null}"));

    try (YcClient client = YcClient.builder()
            .config(YcClientConfig.builder().baseUrl("https://example.test/api").build())
            .transport(transport)
            .captchaProvider(() -> new CaptchaResult("x", "s", "c", "t"))
            .build()) {
        ApiResult r = client.loginBySms("13900003333", "000000");
        assertEquals(401, r.getCode());
        assertEquals("bad code", r.getMsg());
    }
}

@Test
void loginBySms_transportFailure_usesLoginStep() {
    RecordingTransport transport = new RecordingTransport(req -> {
        throw new RuntimeException("boom");
    });

    try (YcClient client = YcClient.builder()
            .config(YcClientConfig.builder().baseUrl("https://example.test/api").build())
            .transport(transport)
            .captchaProvider(() -> new CaptchaResult("x", "s", "c", "t"))
            .build()) {
        com.j.soul.yc.exception.YcException ex =
                org.junit.jupiter.api.Assertions.assertThrows(
                        com.j.soul.yc.exception.YcException.class,
                        () -> client.loginBySms("13900003333", "654321"));
        assertEquals(com.j.soul.yc.exception.YcStep.LOGIN, ex.getStep());
    }
}
```

需要的 import（若尚未存在）：

```java
import com.j.soul.yc.exception.YcException;
import com.j.soul.yc.exception.YcStep;
import static org.junit.jupiter.api.Assertions.assertThrows;
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd java-sdk && mvn -q -Dtest=YcClientTest#loginBySms_postsEncryptedFormWithBasicAuth,YcClientTest#loginBySms_businessReject_returnsApiResult,YcClientTest#loginBySms_transportFailure_usesLoginStep test
```

Expected: FAIL（`loginBySms` 不存在 或 编译失败）

- [ ] **Step 3: Add `YcStep.LOGIN`**

Modify `java-sdk/src/main/java/com/j/soul/yc/exception/YcStep.java`：

```java
public enum YcStep {
    CHECK_MOBILE,
    CAPTCHA,
    SMS,
    REPLACE,
    LOGIN,
    HTTP,
    CRYPTO,
    SIGN
}
```

- [ ] **Step 4: Implement `loginBySms` on `YcClient`**

在 `YcClient.java` 常量区（`PATH_REPLACE_MOBILE` / `LOGIN_REFERER` 旁）增加：

```java
private static final String PATH_LOGIN = "/login";
/** Frontend OAuth client Basic for portal login (portal_app:perfect_portal). */
private static final String PORTAL_BASIC_AUTH =
        "Basic cG9ydGFsX2FwcDpwZXJmZWN0X3BvcnRhbA==";
```

在 `sendLoginSms` 与 `replaceMobile` 之间增加方法：

```java
/**
 * Login-page SMS submit: POST {@code /login} with crytoLogin body.
 * Payload: username=mobile, password=smsCode, auth_type=sms, grant_type=password.
 * Authorization is raw Basic (no Bearer). GW sign uses the Basic value as accessToken
 * (frontend {@code ce()} treats Authorization length &gt; 7 the same way).
 */
public ApiResult loginBySms(String mobile, String smsCode) {
    ensureOpen();
    Objects.requireNonNull(mobile, "mobile");
    Objects.requireNonNull(smsCode, "smsCode");
    try {
        Map<String, Object> payload = new LinkedHashMap<>(4);
        payload.put("username", mobile);
        payload.put("password", smsCode);
        payload.put("auth_type", "sms");
        payload.put("grant_type", "password");

        Map<String, String> encrypted = CrytoLogin.encrypt(payload, config.rsaPublicKeyBase64());
        byte[] body = formEncode(encrypted).getBytes(StandardCharsets.UTF_8);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", config.userAgent());
        headers.put("Origin", config.origin());
        headers.put("Referer", LOGIN_REFERER);
        headers.put("channel", config.channel());
        headers.put("client", config.client());
        headers.put("Content-Type", FORM_CONTENT_TYPE);
        headers.put("Authorization", PORTAL_BASIC_AUTH);
        headers.putAll(GwSigner.sign(
                PATH_LOGIN,
                "POST",
                config.gwKey(),
                config.gwClient(),
                PORTAL_BASIC_AUTH,
                null,
                null));

        String url = joinUrl(config.baseUrl(), PATH_LOGIN);
        HttpResponse resp = transport.execute(new HttpRequest("POST", url, headers, body, FORM_CONTENT_TYPE));
        return parseApiResult(resp, YcStep.LOGIN);
    } catch (YcException e) {
        throw e;
    } catch (Exception e) {
        throw new YcException(YcStep.LOGIN, "loginBySms failed: " + e.getMessage(), e);
    }
}
```

注意：
- **不要** 调用会写 `Bearer` 的 `businessHeaders` 分支
- 签名 accessToken 参数传 `PORTAL_BASIC_AUTH` 整串
- `Authorization` header 原样 Basic

- [ ] **Step 5: Run unit tests**

Run:

```bash
cd java-sdk && mvn -q -Dtest=YcClientTest test
```

Expected: PASS（全部 `YcClientTest` 绿，含既有 sendSms / sendLoginSms / replaceMobile）

- [ ] **Step 6: Commit**

```bash
git add \
  java-sdk/src/main/java/com/j/soul/yc/exception/YcStep.java \
  java-sdk/src/main/java/com/j/soul/yc/YcClient.java \
  java-sdk/src/test/java/com/j/soul/yc/YcClientTest.java
git commit -m "$(cat <<'EOF'
feat(yc): add loginBySms for SMS code submit

POST /login with crytoLogin auth_type=sms, raw Basic auth, and GW
sign using Basic as accessToken per frontend ce().
EOF
)"
```

---

### Task 2: README + optional live IT

**Files:**
- Modify: `java-sdk/README.md`
- Modify: `java-sdk/src/test/java/com/j/soul/yc/LiveSendSmsIT.java`

**Interfaces:**
- Consumes: `YcClient.loginBySms(String, String)` from Task 1
- Produces: 文档示例；env 门控 live 测试（默认 skip）

- [ ] **Step 1: Update README**

在「单发短信」表格后、`SmsController` 示例附近，补充登录提交说明。将：

```markdown
其他单接口：`checkMobileAvailable` · `getCaptchaVerifyParam` · `getSmsCode` · `replaceMobile`。
```

改为：

```markdown
其他单接口：`checkMobileAvailable` · `getCaptchaVerifyParam` · `getSmsCode` · `replaceMobile` · `loginBySms`。

### 登录页：发码 + 提交验证码

```java
// 1) 发登录短信 pathType=5
ApiResult sms = ycClient.sendLoginSms(mobile);
// 2) 从短信通道拿到 6 位码后提交
ApiResult login = ycClient.loginBySms(mobile, smsCode);
// login.getCode()==200 时 data 通常含 access_token 等字段；SDK 不落 cookie
```

Spring 控制器示例（可选片段，可紧接 send-login）：

```java
/** 登录页提交短信验证码 */
@PostMapping("/login-sms")
public Map<String, Object> loginSms(@RequestBody Map<String, String> body) {
    var r = ycClient.loginBySms(body.get("mobile"), body.get("smsCode"));
    return Map.of(
            "code", r.getCode(),
            "msg", r.getMsg() == null ? "" : r.getMsg(),
            "data", r.getData() == null ? "" : r.getData()
    );
}
```
```

（确保 Markdown 围栏闭合正确；实现时直接编辑现有 README，不要破坏原有表格。）

- [ ] **Step 2: Optional live IT**

在 `LiveSendSmsIT.java` 的 class javadoc 环境变量列表增加：

```
 *   <li>{@code YC_LIVE_LOGIN=1} + {@code YC_TEST_MOBILE} + {@code YC_SMS_CODE} — loginBySms</li>
```

新增测试方法：

```java
/**
 * SMS login submit. Enable with {@code YC_LIVE_LOGIN=1} and provide
 * {@code YC_TEST_MOBILE} + {@code YC_SMS_CODE}.
 */
@Test
@EnabledIfEnvironmentVariable(named = "YC_LIVE_LOGIN", matches = "1")
void live_loginBySms() {
    String mobile = System.getenv("YC_TEST_MOBILE");
    String code = System.getenv("YC_SMS_CODE");
    assumeTrue(mobile != null && !mobile.isBlank(), "set YC_TEST_MOBILE");
    assumeTrue(code != null && !code.isBlank(), "set YC_SMS_CODE");
    Path recon = requireRecon();
    YcClientConfig cfg = YcClientConfig.builder().reconDir(recon.toString()).build();
    try (YcClient client = YcClient.builder().config(cfg).build()) {
        ApiResult r = client.loginBySms(mobile.trim(), code.trim());
        assertNotNull(r, "ApiResult must parse");
        assertNotNull(r.getCode(), "business code present");
        System.out.println("live loginBySms mobile=" + mobile.trim()
                + " code=" + r.getCode()
                + " msg=" + r.getMsg()
                + " data=" + r.getData());
    }
}
```

- [ ] **Step 3: Verify default tests still pass**

Run:

```bash
cd java-sdk && mvn -q -Dtest=YcClientTest,LiveSendSmsIT test
```

Expected: `YcClientTest` PASS；`LiveSendSmsIT` 因无 env 被 skip / 不失败。

- [ ] **Step 4: Commit**

```bash
git add java-sdk/README.md java-sdk/src/test/java/com/j/soul/yc/LiveSendSmsIT.java
git commit -m "$(cat <<'EOF'
docs(yc): document loginBySms and optional live IT

README flow for sendLoginSms → loginBySms; env-gated live login test.
EOF
)"
```

---

## Spec coverage checklist

| Spec item | Task |
|-----------|------|
| `loginBySms(mobile, smsCode)` | Task 1 |
| payload username/password/auth_type/grant_type | Task 1 |
| no cardNo | Task 1（payload 仅 4 字段） |
| raw Basic Authorization | Task 1 |
| GW sign with Basic as accessToken | Task 1 |
| Referer isShow=login | Task 1 |
| `YcStep.LOGIN` | Task 1 |
| business reject → ApiResult | Task 1 |
| replaceMobile unchanged | Task 1 回归测试 |
| README usage | Task 2 |
| optional live IT | Task 2 |
| no Spring / no batch change | 全任务不碰 |

## Self-review

- 无 TBD/TODO 占位
- 方法签名与 spec 一致：`ApiResult loginBySms(String mobile, String smsCode)`
- Basic 常量与 recon 一致
- TDD 顺序：先测后实现
- 任务边界：实现可测交付 vs 文档/live
