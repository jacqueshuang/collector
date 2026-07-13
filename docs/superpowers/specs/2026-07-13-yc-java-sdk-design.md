# YC Java SDK Design

Date: 2026-07-13  
Status: Approved for implementation planning  
Source: existing `python_collector` (perfect99 pure-protocol client)

## 1. Goal

Convert the current Python collector into a **pure Java utility library** that Spring Boot projects can depend on directly via Maven.

Primary entry: `YcClient`  
Maven coordinate: `com.j.soul.yc:yc`  
Location: `java-sdk/` under the current repository (Python remains for reference)

## 2. Decisions

| Topic | Decision |
|------|----------|
| Delivery form | Pure Java SDK (not Spring Boot Starter in phase 1) |
| Layout | New `java-sdk/` subproject in current repo |
| Build | Maven single module |
| Java version | 23 |
| Coordinate | `com.j.soul.yc:yc` (initial version `0.1.0`) |
| HTTP | Pluggable transport: OkHttp (default) + curl4j, runtime selectable |
| Captcha | Must complete Aliyun slider fully in Java until SMS can be sent |
| Spring | No Spring dependency; caller constructs `@Bean` manually |
| Python service | Keep; not replaced in phase 1 |

## 3. Architecture

```
business code
    → YcClient
        ├─ CaptchaProvider (AliyunCaptchaProvider)
        ├─ CrytoLogin
        ├─ GwSigner
        └─ HttpTransport (OkHttp | curl4j | custom)
```

### Package layout

```
java-sdk/
├── pom.xml
└── src/
    ├── main/java/com/j/soul/yc/
    │   ├── YcClient.java
    │   ├── YcClientBuilder.java
    │   ├── config/YcClientConfig.java
    │   ├── crypto/CrytoLogin.java
    │   ├── sign/GwSigner.java
    │   ├── captcha/
    │   │   ├── CaptchaProvider.java
    │   │   ├── CaptchaResult.java
    │   │   └── aliyun/
    │   │       ├── AliyunCaptchaProvider.java
    │   │       ├── AliyunCaptchaConfig.java
    │   │       ├── DeviceDataGenerator.java
    │   │       ├── AliyunSigner.java
    │   │       ├── TrajectoryGenerator.java
    │   │       ├── DeviceTokenProvider.java
    │   │       └── CaptchaVerifyParamBuilder.java
    │   ├── http/
    │   │   ├── HttpTransport.java
    │   │   ├── TransportType.java
    │   │   ├── HttpRequest.java
    │   │   ├── HttpResponse.java
    │   │   ├── OkHttpTransport.java
    │   │   └── Curl4jTransport.java
    │   ├── model/
    │   │   ├── ApiResult.java
    │   │   └── ReplaceMobileRequest.java
    │   └── exception/YcException.java
    └── test/java/com/j/soul/yc/...
```

### Dependency policy

| Dependency | Role | Required |
|-----------|------|----------|
| OkHttp | default HTTP transport | yes (default impl) |
| curl4j (or equivalent libcurl binding) | alternate transport | yes as selectable impl |
| Jackson | JSON | yes |
| JUnit 5 | tests | test |
| Spring | — | **forbidden** in phase 1 |

## 4. Public API

```java
public final class YcClient implements AutoCloseable {
    public ApiResult checkMobileAvailable(String mobile);
    public CaptchaResult getCaptchaVerifyParam();
    public ApiResult getSmsCode(String mobile, String captchaVerifyParam);
    public ApiResult sendSms(String mobile); // check → captcha → sms
    public ApiResult replaceMobile(ReplaceMobileRequest req);
}
```

Builder usage:

```java
YcClient client = YcClient.builder()
    .config(YcClientConfig.builder()
        .baseUrl("https://uc.perfect99.com/api")
        .aliyunKeyId("...")
        .aliyunKeySecret("...")
        .transportType(TransportType.OKHTTP) // or CURL4J
        .build())
    .build();
```

Spring Boot usage (manual bean, no starter):

```java
@Bean
YcClient ycClient(
    @Value("${yc.aliyun.key-id}") String keyId,
    @Value("${yc.aliyun.key-secret}") String keySecret
) {
    return YcClient.builder()
        .config(YcClientConfig.builder()
            .aliyunKeyId(keyId)
            .aliyunKeySecret(keySecret)
            .transportType(TransportType.CURL4J)
            .build())
        .build();
}
```

## 5. Configuration

`YcClientConfig` fields:

| Field | Default | Notes |
|------|---------|-------|
| `baseUrl` | `https://uc.perfect99.com/api` | business API root |
| `userAgent` | Chrome 149 UA | request UA |
| `channel` | `pc` | header |
| `client` | `mall` | header |
| `gwKey` | current production key | GW sign |
| `gwClient` | `mall_pc` | GW header |
| `rsaPublicKey` | current production pubkey | crytoLogin |
| `aliyunKeyId` | required for SMS | captcha |
| `aliyunKeySecret` | required for SMS | captcha |
| `captchaPrefix` | `1uu8u2` | Aliyun host prefix |
| `sceneId` | `1pn9314j` | captcha scene |
| `transportType` | `OKHTTP` | `OKHTTP` \| `CURL4J` |
| `connectTimeout` | 10s | |
| `readTimeout` | 30s | |
| `proxy` | empty | optional host/port |

## 6. Crypto and signing

### crytoLogin (from `crypto.py`)

1. `aesKey = randomString(16)` from `[A-Za-z0-9]`
2. `key = Base64(RSA_PKCS1_v1_5_Encrypt(pub, aesKey))`
3. compact JSON body (`separators=(',', ':')`, unicode preserved)
4. `data = Base64(AES_ECB_PKCS7(aesKey, jsonBytes))`
5. submit as `application/x-www-form-urlencoded` fields `key` + `data`

### GW sign (from `gw_sign.py`)

1. normalize path: ensure leading `/`, strip query
2. `nonce = UUID v4`, `ts = epochMillis`
3. string:
   - without token: `path={p}&method={M}&nonce={n}&timestamp={ts}&key={k}`
   - with token (len > 7): append `accessToken` before `key`
4. `GW-Signature = MD5(hex)`
5. headers: `GW-Timestamp`, `GW-Nonce`, `GW-Signature`, `GW-Client`
6. `replaceMobile` may also send `Authorization: Bearer {token}`

## 7. HTTP transport

```java
public interface HttpTransport extends AutoCloseable {
    HttpResponse execute(HttpRequest request);
}
```

- `OkHttpTransport`: default
- `Curl4jTransport`: selectable for closer-to-browser / curl behavior
- Builder may inject a custom `HttpTransport`
- Business API and Aliyun captcha API share one transport instance
- Transport only does IO; client owns URL building, form encoding, JSON parse

## 8. Aliyun captcha (hard requirement)

Default `sendSms` path **must** run captcha inside Java. No silent Node/subprocess fallback.

Pipeline:

1. `DeviceDataGenerator` → DeviceData (AES-CBC, known key/iv)
2. `AliyunSigner` + `InitCaptchaV3` → `certifyId`
3. `DeviceTokenProvider` → `deviceToken`
4. `TrajectoryGenerator` → encrypted trajectory `data`
5. `VerifyCaptchaV3` → `securityToken` (`VerifyResult=true`)
6. `CaptchaVerifyParamBuilder` → Base64 JSON `{certifyId, sceneId, isSign, securityToken}`

Implementation order:

1. Port pure-protocol pieces already proven in Python: DeviceData, Init sign, Verify request, param assembly
2. Reverse/port `deviceToken` using node_helper/recon assets as algorithm reference only
3. Reverse/port trajectory `data` (JSVMP-backed today) until SMS succeeds
4. Field-level compare against Python/Node outputs where deterministic; end-to-end accept on business success

Reference assets (read-only, not runtime deps):

- `aliyun_captcha.py`
- `../node_helper/feilin_*.js`
- recon / hook / probe artifacts under sibling dirs

Failure model: any captcha step throws `YcException(step=CAPTCHA, ...)`.

## 9. Error model

- Infrastructure / protocol failures → `YcException` with `step`:
  - `CHECK_MOBILE` | `CAPTCHA` | `SMS` | `REPLACE` | `HTTP` | `CRYPTO` | `SIGN`
- Upstream business JSON still mapped to `ApiResult` (`code` / `msg` / `data`) when HTTP succeeds

## 10. Testing and acceptance

### Tests

| Level | Coverage |
|------|----------|
| Unit | CrytoLogin output shape, GwSigner fixed vectors, AliyunSigner vectors, DeviceData vectors |
| Integration (env-gated) | real Init/Verify and `sendSms` when Aliyun keys present |

Randomized fields (AES key, nonce, trajectory noise) assert format / verifiability, not byte equality.

### Phase-1 Done criteria

1. `mvn -q test` passes
2. With valid Aliyun keys, `YcClient.sendSms(mobile)` completes in pure Java (business `code==200` or clear business rejection; not transport/captcha infra failure)
3. Spring Boot app can depend on the jar and construct `YcClient` without Spring types from this library
4. Transport switches between `OKHTTP` and `CURL4J`
5. `java-sdk/README.md` documents coordinate, minimal sample, config, and Python capability mapping

## 11. Out of scope (phase 1)

- Spring Boot Starter / auto-configuration
- Publishing to Maven Central (local `mvn install` / private repo later)
- Removing or replacing the Python FastAPI service
- Proxy pools, multi-account orchestration, rate-limit framework

## 12. Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| JSVMP trajectory / deviceToken not fully reduced | blocks SMS | prioritize reverse of existing helpers; gate release on real SMS path |
| Java TLS fingerprint differs from curl_cffi | upstream rejects | provide curl4j transport switch |
| curl4j artifact/API variance | build friction | pin known-good artifact in pom; wrap behind `HttpTransport` |
| Secrets in env/config | leak risk | keys only via config/env; never hardcode secrets in repo |

## 13. Mapping from Python

| Python | Java |
|--------|------|
| `Perfect99Client` | `YcClient` |
| `crypto.cryto_login` | `CrytoLogin` |
| `gw_sign.gw_sign` | `GwSigner` |
| `AliyunCaptchaV3` | `AliyunCaptchaProvider` |
| `curl_cffi` session | `HttpTransport` (`OkHttp` / `curl4j`) |
| `server.py` FastAPI | not ported (callers use SDK) |
