# YC Java SDK Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver Maven library `com.j.soul.yc:yc` under `java-sdk/` so Spring Boot apps can call perfect99 check-mobile / Aliyun captcha / SMS / replace-mobile in pure Java.

**Architecture:** Single-module Maven SDK with layered packages: crypto, GW sign, pluggable `HttpTransport` (OkHttp default + curl4j), Aliyun captcha provider, and facade `YcClient`. Python code remains reference-only; runtime must not spawn Node.

**Tech Stack:** Java 23, Maven, OkHttp 4.x, Jackson 2.x, JUnit 5; curl4j transport selectable at runtime.

**Spec:** `docs/superpowers/specs/2026-07-13-yc-java-sdk-design.md`

## Global Constraints

- Maven coordinate: `com.j.soul.yc:yc:0.1.0`
- Java release: 23
- No Spring dependencies in the library
- No Node/subprocess captcha fallback at runtime
- Default transport: OkHttp; alternate: `TransportType.CURL4J`
- Business base URL default: `https://uc.perfect99.com/api`
- Captcha prefix/scene defaults: `1uu8u2` / `1pn9314j`
- Keep edits scoped to `java-sdk/` + docs/README under it; do not break existing Python service
- Style: minimal, no redundant comments/docs beyond `java-sdk/README.md`

## File Structure (create)

```
java-sdk/
  pom.xml
  README.md
  src/main/java/com/j/soul/yc/
    YcClient.java
    YcClientBuilder.java
    config/YcClientConfig.java
    crypto/CrytoLogin.java
    sign/GwSigner.java
    captcha/CaptchaProvider.java
    captcha/CaptchaResult.java
    captcha/aliyun/AliyunCaptchaConfig.java
    captcha/aliyun/DeviceDataGenerator.java
    captcha/aliyun/AliyunSigner.java
    captcha/aliyun/DeviceTokenProvider.java
    captcha/aliyun/TrajectoryGenerator.java
    captcha/aliyun/CaptchaVerifyParamBuilder.java
    captcha/aliyun/AliyunCaptchaProvider.java
    http/HttpTransport.java
    http/TransportType.java
    http/HttpRequest.java
    http/HttpResponse.java
    http/OkHttpTransport.java
    http/Curl4jTransport.java
    model/ApiResult.java
    model/ReplaceMobileRequest.java
    exception/YcException.java
    exception/YcStep.java
  src/test/java/com/j/soul/yc/
    crypto/CrytoLoginTest.java
    sign/GwSignerTest.java
    captcha/aliyun/DeviceDataGeneratorTest.java
    captcha/aliyun/AliyunSignerTest.java
    captcha/aliyun/CaptchaVerifyParamBuilderTest.java
    http/OkHttpTransportTest.java
    YcClientTest.java
```

---

### Task 1: Maven skeleton + core types

**Files:**
- Create: `java-sdk/pom.xml`
- Create: `java-sdk/src/main/java/com/j/soul/yc/exception/YcStep.java`
- Create: `java-sdk/src/main/java/com/j/soul/yc/exception/YcException.java`
- Create: `java-sdk/src/main/java/com/j/soul/yc/model/ApiResult.java`
- Create: `java-sdk/src/main/java/com/j/soul/yc/model/ReplaceMobileRequest.java`
- Create: `java-sdk/src/main/java/com/j/soul/yc/config/YcClientConfig.java`
- Create: `java-sdk/src/main/java/com/j/soul/yc/http/TransportType.java`
- Create: `java-sdk/src/test/java/com/j/soul/yc/config/YcClientConfigTest.java`

**Interfaces:**
- Consumes: nothing
- Produces: buildable module; `YcClientConfig` builder with defaults from spec; `YcException(YcStep step, String message, Throwable cause)`; `ApiResult` Jackson-friendly `code/msg/data`

- [ ] **Step 1: Write `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.j.soul.yc</groupId>
  <artifactId>yc</artifactId>
  <version>0.1.0</version>
  <name>yc</name>
  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.release>23</maven.compiler.release>
    <junit.version>5.11.4</junit.version>
    <okhttp.version>4.12.0</okhttp.version>
    <jackson.version>2.18.2</jackson.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>com.squareup.okhttp3</groupId>
      <artifactId>okhttp</artifactId>
      <version>${okhttp.version}</version>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
      <version>${jackson.version}</version>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>${junit.version}</version>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.13.0</version>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.5.2</version>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: Add enums/types + config defaults**

Implement:
- `YcStep`: `CHECK_MOBILE, CAPTCHA, SMS, REPLACE, HTTP, CRYPTO, SIGN`
- `YcException` with `getStep()`
- `ApiResult` fields: `Integer code`, `String msg`, `JsonNode data` (or `Object data`)
- `ReplaceMobileRequest` record: `cardNo, mobile, certNo, smsCode, accessToken`
- `TransportType`: `OKHTTP, CURL4J`
- `YcClientConfig` immutable + builder; defaults:

```java
baseUrl = "https://uc.perfect99.com/api"
userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"
channel = "pc"
client = "mall"
referer = "https://uc.perfect99.com/loginAndRegistration?isShow=resetPhone&channel=memberCardLogin"
origin = "https://uc.perfect99.com"
gwKey = "8294e640299aae744184b3a529cd1e2f"
gwClient = "mall_pc"
rsaPublicKeyBase64 = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDf3n7GvYCjevA+JEnMQHfxDX/ePSviRR2C2tsNSVyuTm6TfaP/HLzNbAO0kK+52nr2HO2LzsSd+a98V4n5npYDWPqbswXzKLj73kBlBI0P6Uf3uygCAZtfd9qkAn0DkgGpVw1VtCb33svBkaQinOYB550OygDM1vemuQYq11E/mQIDAQAB"
captchaPrefix = "1uu8u2"
sceneId = "1pn9314j"
transportType = TransportType.OKHTTP
connectTimeout = Duration.ofSeconds(10)
readTimeout = Duration.ofSeconds(30)
```

- [ ] **Step 3: Write failing/passing config test**

```java
@Test
void defaults_matchProductionPython() {
  YcClientConfig c = YcClientConfig.builder().build();
  assertEquals("https://uc.perfect99.com/api", c.baseUrl());
  assertEquals("8294e640299aae744184b3a529cd1e2f", c.gwKey());
  assertEquals(TransportType.OKHTTP, c.transportType());
  assertEquals("1pn9314j", c.sceneId());
}
```

- [ ] **Step 4: Run tests**

Run: `cd java-sdk && mvn -q test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add java-sdk/pom.xml java-sdk/src
git commit -m "feat(yc): scaffold Maven module and core config types"
```

---

### Task 2: CrytoLogin (RSA + AES)

**Files:**
- Create: `java-sdk/src/main/java/com/j/soul/yc/crypto/CrytoLogin.java`
- Create: `java-sdk/src/test/java/com/j/soul/yc/crypto/CrytoLoginTest.java`

**Interfaces:**
- Consumes: `YcClientConfig.rsaPublicKeyBase64`
- Produces: `Map<String,String> CrytoLogin.encrypt(Map<String,?> params, String rsaPublicKeyBase64)` → keys `key`,`data`

- [ ] **Step 1: Write failing tests**

```java
@Test
void encrypt_returnsBase64KeyAndData() {
  var out = CrytoLogin.encrypt(Map.of("mobile", "13800138000", "pathType", 11), RSA);
  assertTrue(out.get("key").matches("^[A-Za-z0-9+/=]+$"));
  assertTrue(out.get("data").matches("^[A-Za-z0-9+/=]+$"));
}

@Test
void encrypt_dataDecryptsWithKnownAesKey_whenInjected() {
  // expose package-private overload encrypt(params, rsa, aesKeySupplier) for tests
  String aes = "abcdefghijklmnop"; // 16 chars
  var out = CrytoLogin.encrypt(Map.of("a", 1), RSA, () -> aes);
  // decrypt data with AES/ECB/PKCS5Padding and aes; assert JSON equals {"a":1}
}
```

- [ ] **Step 2: Run test — expect FAIL (class missing)**

Run: `cd java-sdk && mvn -q -Dtest=CrytoLoginTest test`

- [ ] **Step 3: Implement `CrytoLogin`**

Algorithm (mirror `crypto.py`):
1. `aesKey = 16` random from `[A-Za-z0-9]`
2. RSA PKCS#1 v1.5 encrypt `aesKey` UTF-8 bytes with X509 public key from Base64 DER
3. JSON with `ObjectMapper` + `JsonGenerator.Feature` / writer settings equivalent to compact separators and non-ASCII unescaped (`JsonWriteFeature.ESCAPE_NON_ASCII` disabled)
4. AES/ECB/PKCS5Padding encrypt → Base64

Use only JDK crypto (`javax.crypto`, `java.security`).

- [ ] **Step 4: Run tests — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add java-sdk/src/main/java/com/j/soul/yc/crypto java-sdk/src/test/java/com/j/soul/yc/crypto
git commit -m "feat(yc): port crytoLogin RSA+AES encrypt"
```

---

### Task 3: GwSigner

**Files:**
- Create: `java-sdk/src/main/java/com/j/soul/yc/sign/GwSigner.java`
- Create: `java-sdk/src/test/java/com/j/soul/yc/sign/GwSignerTest.java`

**Interfaces:**
- Produces: `Map<String,String> GwSigner.sign(String path, String method, String gwKey, String gwClient, String accessTokenOrNull, Long fixedTimestampMsOrNull, String fixedNonceOrNull)`

- [ ] **Step 1: Write failing fixed-vector test**

```java
@Test
void sign_matchesPythonVector() {
  var h = GwSigner.sign(
      "/mobile/loginregister/checkMemberMobileAvailable",
      "GET",
      "8294e640299aae744184b3a529cd1e2f",
      "mall_pc",
      null,
      1700000000000L,
      "11111111-1111-1111-1111-111111111111");
  assertEquals("1700000000000", h.get("GW-Timestamp"));
  assertEquals("11111111-1111-1111-1111-111111111111", h.get("GW-Nonce"));
  assertEquals("8d7581e40587f588141f79aa34ffbad9", h.get("GW-Signature"));
  assertEquals("mall_pc", h.get("GW-Client"));
}

@Test
void sign_stripsQueryAndAddsSlash() {
  var h = GwSigner.sign("mobile/x?y=1", "post", "k", "mall_pc", null, 1L, "n");
  // path used must be /mobile/x ; method uppercased POST
}
```

- [ ] **Step 2: Implement MD5 signer** (mirror `gw_sign.py`; accessToken only if non-null and length > 7)

- [ ] **Step 3: `mvn -q -Dtest=GwSignerTest test` → PASS**

- [ ] **Step 4: Commit**

```bash
git commit -am "feat(yc): port GW gateway MD5 signer"
```

---

### Task 4: HttpTransport + OkHttp + Curl4j

**Files:**
- Create: `java-sdk/src/main/java/com/j/soul/yc/http/HttpRequest.java`
- Create: `java-sdk/src/main/java/com/j/soul/yc/http/HttpResponse.java`
- Create: `java-sdk/src/main/java/com/j/soul/yc/http/HttpTransport.java`
- Create: `java-sdk/src/main/java/com/j/soul/yc/http/OkHttpTransport.java`
- Create: `java-sdk/src/main/java/com/j/soul/yc/http/Curl4jTransport.java`
- Create: `java-sdk/src/test/java/com/j/soul/yc/http/OkHttpTransportTest.java`

**Interfaces:**
- Produces:
  - `HttpRequest` record: `String method, String url, Map<String,String> headers, byte[] body, String contentType`
  - `HttpResponse` record: `int status, Map<String,String> headers, byte[] body`
  - `HttpTransport.execute(HttpRequest)`; `AutoCloseable`
  - `OkHttpTransport(YcClientConfig)`
  - `Curl4jTransport(YcClientConfig)` — use system `curl` CLI via `ProcessBuilder` as the phase-1 curl4j backend (document in README). If a stable Maven libcurl binding is already on the machine, prefer it but keep the same class name/API.

- [ ] **Step 1: Write transport contract test with OkHttp mock web server OR local JDK HttpServer**

```java
@Test
void okHttp_postsBodyAndHeaders() throws Exception {
  // start com.sun.net.httpserver.HttpServer on ephemeral port
  // assert method POST, header X-Test=1, body hello
  try (var t = new OkHttpTransport(cfg)) {
    var resp = t.execute(new HttpRequest("POST", url, Map.of("X-Test","1"), "hello".getBytes(UTF_8), "text/plain"));
    assertEquals(200, resp.status());
  }
}
```

- [ ] **Step 2: Implement OkHttpTransport** with connect/read timeouts + optional proxy from config

- [ ] **Step 3: Implement Curl4jTransport**

Minimum working behavior:
- Build `curl -sS -X METHOD -H ... --data-binary @- URL` 
- Capture stdout as body, parse HTTP code via `curl -w '%{http_code}' -o bodyfile` pattern
- Map failures to `YcException(YcStep.HTTP, ...)`
- Honor proxy if set (`-x host:port`)

- [ ] **Step 4: Factory helper on builder later; for now unit-test OkHttp only**

Run: `mvn -q -Dtest=OkHttpTransportTest test`

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(yc): add OkHttp and curl4j HTTP transports"
```

---

### Task 5: Aliyun DeviceData + Signer + Param builder

**Files:**
- Create: `java-sdk/src/main/java/com/j/soul/yc/captcha/aliyun/DeviceDataGenerator.java`
- Create: `java-sdk/src/main/java/com/j/soul/yc/captcha/aliyun/AliyunSigner.java`
- Create: `java-sdk/src/main/java/com/j/soul/yc/captcha/aliyun/CaptchaVerifyParamBuilder.java`
- Create: `java-sdk/src/main/java/com/j/soul/yc/captcha/CaptchaResult.java`
- Create: corresponding tests

**Interfaces:**
- `DeviceDataGenerator.generate(prefix, sceneId)` → Base64 string
- `AliyunSigner.sign(Map<String,String> params, String keySecret, String method)` → Base64 HMAC-SHA1
- `CaptchaVerifyParamBuilder.build(certifyId, sceneId, securityToken)` → Base64 JSON

Constants from `aliyun_captcha.py`:
- AES key `c175a358550d02e2`, IV `0123456789ABCDEF`
- `app_key=ab034ec0643f91399eb33e062dc7fae1`, `DEVICE_TYPE=10001`, `APP_VERSION=W20220202`, `PLATFORM=W`, `APP_NAME=saf-captcha`, `REGION=cn`

- [ ] **Step 1: Failing tests with Python vectors**

```java
@Test
void deviceData_matchesPython() {
  assertEquals(
    "SKUnL2cTRSYG+LLXVRj3dQn6QqS1M57myLuJJfd/djB/JQwzBD4lv8jdraKIolFcRyBBMc0kPS+Zb2objaY0SbUOCSOIOZFNchkFi4BjyIhcq+N4oFzQHCFjX5r1BvGVrYyehCz0c/H+q4S89SQgdAIeNZaCYNhSOPgRT1s/a5k=",
    DeviceDataGenerator.generate("1uu8u2", "1pn9314j"));
}

@Test
void aliyunSign_matchesPython() {
  Map<String,String> params = new TreeMap<>();
  // fixed map from plan generation with secret testsecret → signature paitr/LIFUsnzBW8m7TnWP9JwQQ=
  assertEquals("paitr/LIFUsnzBW8m7TnWP9JwQQ=", AliyunSigner.sign(params, "testsecret", "POST"));
}

@Test
void captchaParam_isBase64Json() {
  String p = CaptchaVerifyParamBuilder.build("cid", "1pn9314j", "tok");
  String json = new String(Base64.getDecoder().decode(p), UTF_8);
  assertTrue(json.contains("\"isSign\":true"));
  assertTrue(json.contains("\"certifyId\":\"cid\""));
}
```

- [ ] **Step 2: Implement generators/signer** (Aliyun special URL encode: `+→%20`, `*→%2A`, `~` preserved)

- [ ] **Step 3: `mvn -q -Dtest=DeviceDataGeneratorTest,AliyunSignerTest,CaptchaVerifyParamBuilderTest test` → PASS**

- [ ] **Step 4: Commit**

```bash
git commit -am "feat(yc): port Aliyun DeviceData, signer, param builder"
```

---

### Task 6: Reverse + implement deviceToken and trajectory `data` in pure Java

**Files:**
- Create: `java-sdk/src/main/java/com/j/soul/yc/captcha/aliyun/DeviceTokenProvider.java`
- Create: `java-sdk/src/main/java/com/j/soul/yc/captcha/aliyun/TrajectoryGenerator.java`
- Create: `java-sdk/src/test/java/com/j/soul/yc/captcha/aliyun/TrajectoryGeneratorTest.java`
- Optional research notes only if needed under `java-sdk/docs/` (avoid unless necessary)

**Interfaces:**
- `String DeviceTokenProvider.obtain(HttpTransport transport, AliyunSession session)`
- `String TrajectoryGenerator.generate(AliyunSession session)` where session holds certifyId/deviceConfig from Init

**Reference (read-only):**
- `../node_helper/feilin_data_helper.js`
- `../node_helper/feilin_device_token.js`
- `../node_helper/verify_keystream.js`
- `../node_helper/extract_keys.js` / `extract_secrets.js`
- `../recon/sg029.js`, `feilin094.js`, `AliyunCaptcha.js`

- [ ] **Step 1: Offline reverse pass (no Node at runtime)**

Working procedure for the implementer:
1. From captured Verify bodies in `../node_helper/captured_requests.json` (if present), document plaintext fields inside `data` / `deviceToken`
2. Use existing probe scripts **only as human research tools** to identify:
   - whether keystream is deterministic for fixed trajectory (`verify_keystream.js` hypothesis)
   - encryption primitive (AES/custom XOR) and key source
3. Port the **minimal** algorithm to Java; do not embed Node, do not `ProcessBuilder("node", ...)`
4. If full JSVMP reduction is incomplete after focused reverse, implement a pure-Java **embedded script engine path is forbidden** unless it is still 100% in-process Java without external node binary — prefer finishing algorithm port. Release is blocked until SMS works.

- [ ] **Step 2: Unit tests for any pure functions extracted** (fixed trajectory → stable ciphertext if deterministic; otherwise schema/base64 validity tests)

- [ ] **Step 3: Implement providers used by Task 7**

- [ ] **Step 4: Commit research-backed implementation**

```bash
git commit -am "feat(yc): pure Java deviceToken and captcha trajectory data"
```

---

### Task 7: AliyunCaptchaProvider orchestration

**Files:**
- Create: `java-sdk/src/main/java/com/j/soul/yc/captcha/CaptchaProvider.java`
- Create: `java-sdk/src/main/java/com/j/soul/yc/captcha/aliyun/AliyunCaptchaConfig.java`
- Create: `java-sdk/src/main/java/com/j/soul/yc/captcha/aliyun/AliyunCaptchaProvider.java`
- Create: `java-sdk/src/test/java/com/j/soul/yc/captcha/aliyun/AliyunCaptchaProviderTest.java`

**Interfaces:**
- `CaptchaProvider { CaptchaResult getCaptchaVerifyParam(); }`
- `CaptchaResult` record: `String captchaVerifyParam, String sceneId, String certifyId, String securityToken`
- `AliyunCaptchaProvider(YcClientConfig config, HttpTransport transport)`

Pipeline:
1. Build DeviceData
2. POST InitCaptchaV3 to `https://{prefix}.captcha-open.aliyuncs.com/`
3. Obtain deviceToken
4. Generate data
5. POST VerifyCaptchaV3 to `https://{prefix}-verify.captcha-open.aliyuncs.com/`
6. Require `Code=Success` and `Result.VerifyResult=true`
7. Build final captchaVerifyParam Base64

- [ ] **Step 1: Write unit test with mocked `HttpTransport`** returning canned Init/Verify JSON

- [ ] **Step 2: Implement provider; map failures to `YcException(CAPTCHA, ...)`**

- [ ] **Step 3: Optional env-gated IT**

```java
@EnabledIfEnvironmentVariable(named = "ALIYUN_CAPTCHA_KEY_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "ALIYUN_CAPTCHA_KEY_SECRET", matches = ".+")
@Test
void live_initVerify_succeeds() { ... }
```

- [ ] **Step 4: `mvn -q test` → PASS (live test skipped without env)**

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(yc): AliyunCaptchaProvider full Init/Verify pipeline"
```

---

### Task 8: YcClient facade + builder

**Files:**
- Create: `java-sdk/src/main/java/com/j/soul/yc/YcClient.java`
- Create: `java-sdk/src/main/java/com/j/soul/yc/YcClientBuilder.java`
- Create: `java-sdk/src/test/java/com/j/soul/yc/YcClientTest.java`

**Interfaces:**
- Methods from spec §4
- Builder: `.config(YcClientConfig)`, `.transport(HttpTransport)`, `.captchaProvider(CaptchaProvider)`, `.build()`
- Default wiring: transport from `transportType`; captcha = `AliyunCaptchaProvider`

Behavior:
- `checkMobileAvailable`: GET `/mobile/loginregister/checkMemberMobileAvailable?mobile=&rnd=`
- `getSmsCode`: crytoLogin body + POST `/mobile/login/getSmsCodeNewV2` with form fields; payload fields `mobile,pathType=11,captchaVerifyParam,sceneId`
- `sendSms`: check → captcha → getSmsCode; if check `code!=200` return that result without captcha
- `replaceMobile`: crytoLogin `{cardNo,mobile,certificatesNo,verificationCode}` POST `/mobile/openApi/replaceMobile`
- Always attach channel/client/UA/Origin/Referer + GW headers

- [ ] **Step 1: Write tests with mocked transport + captcha**

```java
@Test
void sendSms_stopsOnCheckFailure() { ... }

@Test
void sendSms_happyPath_callsCaptchaAndSms() { ... }

@Test
void replaceMobile_sendsEncryptedForm() { ... }
```

- [ ] **Step 2: Implement client**

- [ ] **Step 3: `mvn -q test` → PASS**

- [ ] **Step 4: Commit**

```bash
git commit -am "feat(yc): YcClient facade for sms and replace-mobile"
```

---

### Task 9: README + install verification

**Files:**
- Create: `java-sdk/README.md`

- [ ] **Step 1: Write minimal README** (coordinate, Java 23, builder sample, config table, Python mapping, env vars for live captcha, transport switch)

- [ ] **Step 2: `cd java-sdk && mvn -q clean test install`**

Expected: BUILD SUCCESS; artifact in local m2 `com/j/soul/yc/yc/0.1.0/`

- [ ] **Step 3: Commit**

```bash
git add java-sdk/README.md
git commit -m "docs(yc): README for Spring Boot dependency usage"
```

---

### Task 10: Live acceptance (manual/env-gated)

**Files:**
- Create: `java-sdk/src/test/java/com/j/soul/yc/LiveSendSmsIT.java` (JUnit, env-gated)

- [ ] **Step 1: Implement IT calling `sendSms` with env:**
  - `ALIYUN_CAPTCHA_KEY_ID`
  - `ALIYUN_CAPTCHA_KEY_SECRET`
  - `YC_TEST_MOBILE` (optional default)

- [ ] **Step 2: Run when keys available**

```bash
cd java-sdk
export ALIYUN_CAPTCHA_KEY_ID=...
export ALIYUN_CAPTCHA_KEY_SECRET=...
export YC_TEST_MOBILE=1xxxxxxxxxx
mvn -q -Dtest=LiveSendSmsIT test
```

Expected: no `YcException` from CAPTCHA/HTTP infra; print business JSON (`code` may be business-level reject, but captcha+request path succeeded)

- [ ] **Step 3: If captcha fails, return to Task 6 with concrete failing field dumps; do not mark project done**

- [ ] **Step 4: Commit IT**

```bash
git commit -am "test(yc): env-gated live sendSms acceptance"
```

---

## Self-Review (plan vs spec)

| Spec requirement | Task |
|------------------|------|
| `java-sdk/` Maven `com.j.soul.yc:yc` Java 23 | Task 1 |
| CrytoLogin | Task 2 |
| GwSigner | Task 3 |
| OkHttp + curl4j transports | Task 4 |
| DeviceData / Aliyun sign / param | Task 5 |
| deviceToken + trajectory pure Java | Task 6 |
| Full captcha provider | Task 7 |
| YcClient API surface | Task 8 |
| README + install | Task 9 |
| sendSms pure Java acceptance | Task 10 |
| No Spring Starter / keep Python | honored (out of scope) |

Placeholder scan: none intentional.  
Type names aligned across tasks: `YcClient`, `YcClientConfig`, `HttpTransport`, `CaptchaProvider`, `CaptchaResult`, `ApiResult`, `YcException`, `TransportType`.

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-13-yc-java-sdk.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  
2. **Inline Execution** — execute tasks in this session with checkpoints  

Which approach?
