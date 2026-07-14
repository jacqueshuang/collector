# YC Java SDK — Login by SMS Design

Date: 2026-07-15  
Status: Approved for implementation planning  
Depends on: existing `YcClient` + `sendLoginSms` (pathType=5)

## 1. Goal

在 `java-sdk` 的 `YcClient` 上增加**登录页短信验证码提交**能力，对齐前端 recon：

`sendLoginSms(mobile)` 发码后 → 调用方拿到短信码 → `loginBySms(mobile, smsCode)` 提交登录。

换绑手机号提交已由 `replaceMobile` 覆盖，**本变更不改**。

## 2. Decisions

| Topic | Decision |
|------|----------|
| Scope | 仅短信登录 `auth_type=sms` |
| cardNo | **不带** |
| API shape | `YcClient.loginBySms(String mobile, String smsCode)` → `ApiResult` |
| 一键收码+登录 | **不做**（码来自外部短信通道） |
| Basic 凭证 | 固定前端值 `portal_app:perfect_portal`，阶段内不可配置 |
| 实现风格 | 方案 A：facade 薄方法，局部手写 headers（不复用 Bearer 分支） |
| Token 持久化 | SDK **不**写 cookie / 不缓存 token；原样返回 `ApiResult` |

## 3. Protocol (frontend recon)

| Item | Value |
|------|--------|
| Method / path | `POST /login`（相对 baseURL `/api` → 默认 `https://uc.perfect99.com/api/login`） |
| Content-Type | `application/x-www-form-urlencoded; charset=UTF-8` |
| Body | crytoLogin 输出的 `key` + `data` |
| Plain JSON | `{"username":"<mobile>","password":"<smsCode>","auth_type":"sms","grant_type":"password"}` |
| Authorization | `Basic cG9ydGFsX2FwcDpwZXJmZWN0X3BvcnRhbA==`（raw Basic，**无** Bearer 前缀） |
| GW path | `/login`（前端 `url: "login"` 归一化为 leading `/`） |
| GW accessToken | 前端 `ce()`：`Authorization` 长度 > 7 时把**整段** `Authorization` 值拼进签名串的 `accessToken=`。因此签名时 `accessTokenOrNull = "Basic cG9y..."` |
| Referer | `https://uc.perfect99.com/loginAndRegistration?isShow=login` |
| channel / client | 现有 `YcClientConfig`（默认 `pc` / `mall`） |
| Origin / UA | 现有 config |

成功：外层 `code == 200`，`data` 通常含 `access_token`、`token_type` 等。  
业务失败：仍解析为 `ApiResult`，不抛异常。  
基础设施失败（加密 / HTTP / 解析）：抛 `YcException(YcStep.LOGIN, ...)`。

## 4. Public API

```java
public final class YcClient implements AutoCloseable {
    // existing ...
    /** 短信登录：POST /login，auth_type=sms。不带 cardNo。 */
    public ApiResult loginBySms(String mobile, String smsCode);
}
```

配套调用顺序（文档说明，非 SDK 强制）：

1. `sendLoginSms(mobile)` 或 `getSmsCode(mobile, captcha, SmsPathType.LOGIN)`
2. 用户/短信通道取得 6 位码
3. `loginBySms(mobile, smsCode)`

## 5. Implementation points

### 5.1 Files

| File | Change |
|------|--------|
| `java-sdk/.../YcClient.java` | `PATH_LOGIN`、`PORTAL_BASIC_AUTH`、`loginBySms` |
| `java-sdk/.../exception/YcStep.java` | 增加 `LOGIN` |
| `java-sdk/.../YcClientTest.java` | mock 单测 |
| `java-sdk/README.md` | 登录页流程补 `loginBySms` 示例 |
| `LiveSendSmsIT` | 可选：仅当 env `YC_SMS_CODE` 存在时调用（默认不跑真实登录） |

### 5.2 `loginBySms` flow

```
ensureOpen(); require non-null mobile, smsCode
payload = LinkedHashMap:
  username -> mobile
  password -> smsCode
  auth_type -> "sms"
  grant_type -> "password"
encrypted = CrytoLogin.encrypt(payload, config.rsaPublicKeyBase64())
body = formEncode(encrypted) as UTF-8 bytes

headers = {
  User-Agent, Origin, Referer=LOGIN_REFERER, channel, client,
  Content-Type=FORM,
  Authorization=PORTAL_BASIC_AUTH,          // raw Basic, no "Bearer "
  + GwSigner.sign("/login", "POST", gwKey, gwClient, PORTAL_BASIC_AUTH, null, null)
}
// Do NOT use businessHeaders() + Bearer branch (replaceMobile path).

POST joinUrl(baseUrl, "/login")
return parseApiResult(resp, YcStep.LOGIN)
```

**Why not reuse `businessHeaders` for Authorization?**  
`replaceMobile` 在 token 非空时写 `Authorization: Bearer {token}`。Login 必须写 raw Basic。为避免误伤，login 路径**局部组装** headers，仅复用 `GwSigner` + 公共 form/URL 工具。

### 5.3 Constants

```java
private static final String PATH_LOGIN = "/login";
private static final String PORTAL_BASIC_AUTH =
        "Basic cG9ydGFsX2FwcDpwZXJmZWN0X3BvcnRhbA==";
// LOGIN_REFERER already exists from sendLoginSms work
```

## 6. Error model

| Case | Behavior |
|------|----------|
| null mobile/smsCode | NPE（与现有 facade 一致） |
| client closed | `YcException(YcStep.HTTP, "YcClient is closed")` |
| crypto / transport / parse fail | `YcException(YcStep.LOGIN, ...)` |
| business JSON code ≠ 200 | return `ApiResult` |

## 7. Testing

### Unit (`YcClientTest`)

| Case | Assert |
|------|--------|
| happy path | POST URL ends with `/login`；body 含 `key=` & `data=`；`Authorization` **精确等于** Basic 常量（无 Bearer）；Referer 含 `isShow=login`；存在 `GW-Signature` / `GW-Timestamp` / `GW-Nonce` / `GW-Client`；返回 code 200 |
| null args | NPE |
| transport throws | `YcException` with `YcStep.LOGIN` |
| business reject | `ApiResult.code != 200`，不抛 |

可选：固定 RSA 解密不可行时，不解密 body；只断言 form 形态 + headers（与现有 SMS 测试同级）。

### Live (optional, env-gated)

- 已有发短信 IT；登录仅当 `YC_SMS_CODE`（及手机号 env）齐全时额外调用 `loginBySms`。
- 未设置则 skip，不作为默认 CI 门槛。

### Acceptance

1. `cd java-sdk && mvn -q -Dtest=YcClientTest test` PASS  
2. README 描述 `sendLoginSms` → `loginBySms` 链路  
3. 无 Spring 依赖引入；`replaceMobile` / batch / captcha 行为不变  

## 8. Out of scope

- `auth_type` 其他值（卡密、bindingmobile、third、qrcode）
- `cardNo` 字段
- 可配置 OAuth client id/secret
- token 存储 / refresh / 自动带 Bearer 的后续业务调用封装
- 修改 `replaceMobile` 或 pathType 体系

## 9. Risks

| Risk | Mitigation |
|------|------------|
| GW 签名是否应带 Basic 作为 accessToken | 严格对齐前端 `ce()`：`Authorization.length > 7` → 带；单测至少锁 header 与 path |
| Basic 凭证轮换 | 阶段内写死；若线上变更再做成 config |
| `ApiResult` 字段别名 message/msg | 沿用现有 Jackson 映射（已有 message alias） |

## 10. Usage sketch (README)

```java
try (YcClient client = YcClient.builder().config(cfg).build()) {
    ApiResult sms = client.sendLoginSms("13800138000");
    // ... obtain code from SMS channel ...
    ApiResult login = client.loginBySms("13800138000", smsCode);
    // login.getData() may contain access_token when code==200
}
```
