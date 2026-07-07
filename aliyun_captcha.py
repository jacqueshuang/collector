"""阿里云验证码 V3 纯协议实现。

逆向自 AliyunCaptcha.js / sg029.js，实现：
  - InitCaptchaV3 请求签名（HMAC-SHA1）
  - DeviceData 生成（AES-CBC）
  - DeviceToken 获取（z_um/um token）
  - VerifyCaptchaV3 请求构造
  - CaptchaVerifyParam 生成

轨迹 data 字段因 JSVMP 保护，使用 Node.js 辅助生成。
"""
from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import subprocess
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import quote, urlencode

import requests
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad

from dotenv import load_dotenv

load_dotenv()

# 阿里云验证码常量（从 SDK 提取）
KEY_ID = os.getenv("ALIYUN_CAPTCHA_KEY_ID")
KEY_SECRET = os.getenv("ALIYUN_CAPTCHA_KEY_SECRET")
if not KEY_ID or not KEY_SECRET:
    raise RuntimeError(
        "Missing ALIYUN_CAPTCHA_KEY_ID / ALIYUN_CAPTCHA_KEY_SECRET environment variables"
    )

AES_KEY = b"c175a358550d02e2"  # DeviceData AES key
AES_IV = b"0123456789ABCDEF"  # DeviceData AES IV
PREFIX = "1uu8u2"
SCENE_ID = "1pn9314j"
REGION = "cn"
APP_VERSION = "W20220202"
PLATFORM = "W"
DEVICE_TYPE = "10001"
APP_NAME = "saf-captcha"

# Node 助手路径（用于 data 字段和 deviceToken 生成，JSVMP 保护）
NODE_DATA_HELPER = Path(__file__).resolve().parent.parent / "node_helper" / "feilin_data_helper.js"
NODE_DEVICE_TOKEN_HELPER = Path(__file__).resolve().parent.parent / "node_helper" / "feilin_device_token.js"

UA = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"
)


def _timestamp() -> str:
    """ISO 8601 UTC 时间戳。"""
    now = datetime.now(timezone.utc)
    return now.strftime("%Y-%m-%dT%H:%M:%SZ")


def _signature_nonce() -> str:
    """UUID 格式的 SignatureNonce。"""
    return str(uuid.uuid4())


def _aes_encrypt(key: bytes, iv: bytes, data: str) -> str:
    """AES-CBC 加密，返回 base64。"""
    cipher = AES.new(key, AES.MODE_CBC, iv)
    padded = pad(data.encode("utf-8"), AES.block_size)
    encrypted = cipher.encrypt(padded)
    return base64.b64encode(encrypted).decode("utf-8")


def _aliyun_encode(s: str) -> str:
    """阿里云特殊 URL 编码：+ → %20, * → %2A, ~ → %7E。"""
    return quote(s, safe="~").replace("+", "%20").replace("*", "%2A").replace("%7E", "~")


def _sign(params: dict, key_secret: str, method: str = "POST") -> str:
    """阿里云 HMAC-SHA1 签名。

    1. 删除 Signature 参数
    2. 参数按 key 排序
    3. 用 & 连接 key=value（URL 编码）
    4. 构造 string_to_sign: METHOD&%2F&<urlencoded(params)>
    5. HMAC-SHA1(key_secret + "&", string_to_sign)
    6. base64 编码
    """
    params.pop("Signature", None)
    sorted_items = sorted(params.items())
    canonicalized = "&".join(
        f"{_aliyun_encode(k)}={_aliyun_encode(str(v))}" for k, v in sorted_items
    )
    string_to_sign = f"{method}&%2F&{_aliyun_encode(canonicalized)}"
    hmac_key = (key_secret + "&").encode("utf-8")
    digest = hmac.new(hmac_key, string_to_sign.encode("utf-8"), hashlib.sha1).digest()
    return base64.b64encode(digest).decode("utf-8")


def _generate_device_data() -> str:
    """生成 DeviceData。

    DeviceData = AES_encrypt(key, iv, appKey#DEVICE_TYPE#inner#APP_VERSION#CLOUD#)
    inner = AES_encrypt(key, iv, PLATFORM#APP_NAME#sceneId#captcha-normal#prefix#region)
    """
    inner_str = f"{PLATFORM}#{APP_NAME}#{SCENE_ID}#captcha-normal#{PREFIX}#{REGION}"
    inner_encrypted = _aes_encrypt(AES_KEY, AES_IV, inner_str)

    app_key = "ab034ec0643f91399eb33e062dc7fae1"
    outer_str = f"{app_key}#{DEVICE_TYPE}#{inner_encrypted}#{APP_VERSION}#CLOUD#"
    return _aes_encrypt(AES_KEY, AES_IV, outer_str)


class AliyunCaptchaV3:
    """阿里云验证码 V3 纯协议客户端。"""

    def __init__(self) -> None:
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": UA,
            "Origin": "https://uc.perfect99.com",
            "Referer": "https://uc.perfect99.com/loginAndRegistration",
            "Content-Type": "application/x-www-form-urlencoded",
        })
        self.certify_id: str | None = None
        self.device_token: str | None = None
        self.security_token: str | None = None

    def _init_captcha(self) -> dict:
        """调用 InitCaptchaV3 获取 CertifyId 和 DeviceConfig。"""
        params = {
            "AccessKeyId": KEY_ID,
            "SignatureMethod": "HMAC-SHA1",
            "SignatureVersion": "1.0",
            "Format": "JSON",
            "Timestamp": _timestamp(),
            "Version": "2023-03-05",
            "Action": "InitCaptchaV3",
            "SceneId": SCENE_ID,
            "Language": "cn",
            "Mode": "embed",
            "DeviceData": _generate_device_data(),
        }
        params["SignatureNonce"] = _signature_nonce()
        params["Signature"] = _sign(params.copy(), KEY_SECRET)

        url = f"https://{PREFIX}.captcha-open.aliyuncs.com/"
        resp = self.session.post(url, data=urlencode(params))
        result = resp.json()
        if result.get("Code") != "Success":
            raise RuntimeError(f"InitCaptchaV3 failed: {result}")
        self.certify_id = result["CertifyId"]
        return result

    def _get_data_and_device_token(self, max_retries: int = 3) -> dict:
        """通过 Node.js 辅助生成 data 字段、deviceToken 和 certifyId。

        因 data 字段加密被 JSVMP 保护，使用 jsdom 加载完整 SDK 生成。
        Node helper 执行完整流程（Init → 拖动 → Verify），返回：
        {data, deviceToken, certifyId, securityToken, verifyResult}
        """
        if not NODE_DATA_HELPER.exists():
            raise RuntimeError(f"Node helper not found: {NODE_DATA_HELPER}")

        last_error = ""
        for attempt in range(1, max_retries + 1):
            result = subprocess.run(
                ["node", str(NODE_DATA_HELPER)],
                capture_output=True, text=True, timeout=120,
            )
            if result.returncode == 0:
                return json.loads(result.stdout.strip())

            last_error = result.stderr.strip() or result.stdout.strip() or "unknown error"
            print(f"[captcha] Node helper attempt {attempt}/{max_retries} failed: {last_error}")

        raise RuntimeError(f"Data helper failed after {max_retries} retries: {last_error}")

    def _verify_captcha(self, data: str) -> dict:
        """调用 VerifyCaptchaV3 验证滑块。"""
        captcha_verify_param = json.dumps({
            "sceneId": SCENE_ID,
            "certifyId": self.certify_id,
            "deviceToken": self.device_token,
            "data": data,
        })

        params = {
            "AccessKeyId": KEY_ID,
            "SignatureMethod": "HMAC-SHA1",
            "SignatureVersion": "1.0",
            "Format": "JSON",
            "Timestamp": _timestamp(),
            "Version": "2023-03-05",
            "Action": "VerifyCaptchaV3",
            "SceneId": SCENE_ID,
            "CertifyId": self.certify_id,
            "CaptchaVerifyParam": captcha_verify_param,
        }
        params["SignatureNonce"] = _signature_nonce()
        params["Signature"] = _sign(params.copy(), KEY_SECRET)

        url = f"https://{PREFIX}-verify.captcha-open.aliyuncs.com/"
        resp = self.session.post(url, data=urlencode(params))
        result = resp.json()
        if result.get("Code") == "Success" and result.get("Result", {}).get("VerifyResult"):
            self.security_token = result["Result"]["securityToken"]
        return result

    def get_captcha_verify_param(self) -> str:
        """完整流程：Init → 生成轨迹 → Verify → 返回 captchaVerifyParam。

        返回 base64 编码的 JSON：{certifyId, sceneId, isSign, securityToken}

        注意：data 字段和 deviceToken 因 JSVMP 保护，使用 Node.js 辅助生成。
        """
        # 1. Init（纯 Python 签名，验证签名算法正确性）
        init_result = self._init_captcha()

        # 2. Node helper 执行完整流程（Init → 拖动 → Verify），返回所有字段
        helper_result = self._get_data_and_device_token()
        self.certify_id = helper_result["certifyId"]
        self.device_token = helper_result["deviceToken"]
        self.security_token = helper_result.get("securityToken")

        # 3. 构造 captchaVerifyParam
        param = json.dumps({
            "certifyId": self.certify_id,
            "sceneId": SCENE_ID,
            "isSign": True,
            "securityToken": self.security_token,
        })
        return base64.b64encode(param.encode("utf-8")).decode("utf-8")


if __name__ == "__main__":
    captcha = AliyunCaptchaV3()
    param = captcha.get_captcha_verify_param()
    print(f"captchaVerifyParam: {param}")
    decoded = json.loads(base64.b64decode(param))
    print(f"Decoded: {json.dumps(decoded, indent=2)}")
