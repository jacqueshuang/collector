# python_collector / YC Java SDK

| 目录 | 说明 |
|------|------|
| `java-sdk/` | Perfect99 纯 Java SDK（主交付） |
| Python 源码 | 协议参考；运行时不再依赖 Node 发短信 |

## Java SDK 文档

完整公开 API、Spring Boot 示例、配置与 live 环境变量见：

**[java-sdk/README.md](java-sdk/README.md)**

### 能力速查

| API | 说明 |
|-----|------|
| `checkMobileAvailable` | 检查手机号 |
| `getCaptchaVerifyParam` | 阿里云滑块 |
| `getSmsCode` / `sendSms` / `sendLoginSms` | 发短信（pathType 11 / 5） |
| `loginBySms` | 登录页提交短信验证码 |
| `replaceMobile` | 换绑手机号 |
| `YcSmsBatchExecutor#sendSms` / `#sendLoginSms` | 批量发码 |

```bash
cd java-sdk && mvn -q clean test install
```

## HTTP transports (`java-sdk`)

- `OkHttpTransport` — default (`TransportType.OKHTTP`)
- `Curl4jTransport` — system `curl` via `ProcessBuilder`（需 `PATH` 上有 curl）
