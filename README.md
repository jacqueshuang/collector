# python_collector / YC Java SDK

| 目录 | 说明 |
|------|------|
| `java-sdk/` | Perfect99 **纯 Java SDK**（主交付） |
| Python 源码 | 协议参考；运行时发短信**不再**依赖 Node |

## Java SDK 文档

完整接口中文说明、Spring Boot 示例、配置与 live 环境变量：

**[java-sdk/README.md](java-sdk/README.md)**

### 能力速查（中文）

| API | 中文说明 |
|-----|----------|
| `checkMobileAvailable` | 检查会员手机号是否可用 |
| `getCaptchaVerifyParam` | 过阿里云滑块，生成发码参数 |
| `getSmsCode` | 只发短信（需自备 captcha；可指定 pathType） |
| `sendSms` | 重置手机号一键发码（检查 → 滑块 → pathType=11） |
| `sendLoginSms` | 登录页一键发码（滑块 → pathType=5） |
| `loginBySms` | 登录页提交短信验证码，完成短信登录 |
| `replaceMobile` | 换绑手机号（卡号 + 证件 + 短信码） |
| `YcSmsBatchExecutor#sendSms` | 批量重置发码 |
| `YcSmsBatchExecutor#sendLoginSms` | 批量登录页发码 |

```bash
cd java-sdk && mvn -q clean test install
```

## HTTP 传输（`java-sdk`）

- `OkHttpTransport` — 默认（`TransportType.OKHTTP`）
- `Curl4jTransport` — 通过 `ProcessBuilder` 调用系统 `curl`（需在 `PATH`）
