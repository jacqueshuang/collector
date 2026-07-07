"""perfect99 重置手机号短信验证码纯协议采集器。

流程：
  1. GET /mobile/loginregister/checkMemberMobileAvailable  检查手机号
  2. Node 助手生成 captchaVerifyParam（FeiLin 滑块验证）
  3. POST /mobile/login/getSmsCodeNewV2  获取短信验证码

依赖：
  - curl_cffi（Chrome TLS 指纹）
  - pycryptodome（crytoLogin RSA+AES）
  - 本地 Node 助手（case/perfect99/node_helper/feilin_helper.js）
"""
from __future__ import annotations

import json
import subprocess
import time
from pathlib import Path
from urllib.parse import urlencode

from curl_cffi import requests

from crypto import cryto_login
from gw_sign import gw_sign

BASE_URL = "https://uc.perfect99.com/api"
REFERER = "https://uc.perfect99.com/loginAndRegistration?isShow=resetPhone&channel=memberCardLogin"
UA = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"
)
NODE_HELPER = Path(__file__).resolve().parent.parent / "node_helper" / "feilin_helper_pure.js"

# 阿里云验证码配置（来自 app.js awsc 组件）
ALIYUN_CAPTCHA = {
    "prefix": "1uu8u2",
    "SceneId": "1pn9314j",
    "mode": "embed",
}


class Perfect99Client:
    def __init__(self) -> None:
        self.session = requests.Session(impersonate="chrome")
        self.session.headers.update(
            {
                "User-Agent": UA,
                "Origin": "https://uc.perfect99.com",
                "Referer": REFERER,
                "channel": "pc",
                "client": "mall",
            }
        )

    def _gw_headers(self, path: str, method: str, access_token: str | None = None) -> dict:
        headers = gw_sign(path, method, access_token)
        headers["channel"] = "pc"
        headers["client"] = "mall"
        return headers

    def check_mobile_available(self, mobile: str) -> dict:
        """检查手机号是否可用。"""
        path = "/mobile/loginregister/checkMemberMobileAvailable"
        params = {"mobile": mobile, "rnd": str(int(time.time() * 1000))}
        resp = self.session.get(
            BASE_URL + path,
            params=params,
            headers=self._gw_headers(path, "GET"),
        )
        return resp.json()

    def get_captcha_verify_param(self, use_pure_python: bool = False) -> dict:
        """生成 FeiLin captchaVerifyParam。

        use_pure_python=False（默认）：使用 jsdom Node 助手（feilin_helper_pure.js）
        use_pure_python=True：使用 Python 签名 + Node data 辅助（aliyun_captcha.py）

        返回 {"captchaVerifyParam": "...", "sceneId": "1pn9314j"}。
        """
        if use_pure_python:
            from aliyun_captcha import AliyunCaptchaV3
            captcha = AliyunCaptchaV3()
            param = captcha.get_captcha_verify_param()
            return {"captchaVerifyParam": param, "sceneId": "1pn9314j"}

        cmd = ["node", str(NODE_HELPER)]
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=120,
        )
        if result.returncode != 0:
            raise RuntimeError(
                f"Node helper failed: {result.stderr or result.stdout}"
            )
        data = json.loads(result.stdout.strip())
        if not data.get("success"):
            raise RuntimeError(f"Captcha failed: {data.get('error')}")
        return {
            "captchaVerifyParam": data["captchaVerifyParam"],
            "sceneId": "1pn9314j",
        }

    def get_sms_code(self, mobile: str, captcha: dict) -> dict:
        """请求发送短信验证码。

        captcha: {"captchaVerifyParam": "...", "sceneId": "..."}
        """
        path = "/mobile/login/getSmsCodeNewV2"
        payload = {
            "mobile": mobile,
            "pathType": 11,
            "captchaVerifyParam": captcha["captchaVerifyParam"],
            "sceneId": captcha["sceneId"],
        }
        encrypted = cryto_login(payload)
        body = urlencode(encrypted)

        resp = self.session.post(
            BASE_URL + path,
            data=body,
            headers={
                **self._gw_headers(path, "POST"),
                "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            },
        )
        return resp.json()

    def replace_mobile(
        self,
        card_no: str,
        mobile: str,
        cert_no: str,
        sms_code: str,
        access_token: str | None = None,
    ) -> dict:
        """请求重置手机号（/mobile/openApi/replaceMobile）。"""
        path = "/mobile/openApi/replaceMobile"
        payload = {
            "cardNo": card_no,
            "mobile": mobile,
            "certificatesNo": cert_no,
            "verificationCode": sms_code,
        }
        encrypted = cryto_login(payload)
        body = urlencode(encrypted)

        headers = {
            **self._gw_headers(path, "POST", access_token),
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        }
        if access_token:
            headers["Authorization"] = f"Bearer {access_token}"

        resp = self.session.post(BASE_URL + path, data=body, headers=headers)
        return resp.json()

    def send_sms(self, mobile: str) -> dict:
        """完整流程：检查手机号 → 滑块验证 → 发送短信。"""
        print(f"[1/3] 检查手机号 {mobile} ...")
        check = self.check_mobile_available(mobile)
        print(f"      -> {check}")
        if check.get("code") != 200:
            return check

        print("[2/3] 生成 FeiLin captchaVerifyParam ...")
        captcha = self.get_captcha_verify_param()
        print(f"      -> captchaVerifyParam 长度 {len(captcha.get('captchaVerifyParam', ''))}")

        print("[3/3] 请求发送短信验证码 ...")
        result = self.get_sms_code(mobile, captcha)
        print(f"      -> {result}")
        return result


if __name__ == "__main__":
    import sys

    mobile = sys.argv[1] if len(sys.argv) > 1 else "13800138000"
    client = Perfect99Client()
    result = client.send_sms(mobile)
    print(json.dumps(result, ensure_ascii=False, indent=2))
