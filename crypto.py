"""crytoLogin 加密复现：RSA-1024 加密随机 key + AES-ECB-PKCS7 加密参数 JSON。

对应 app.js 中 function q(e) 逻辑：
  t = randomString(16)
  key = RSA_encrypt(t, PUBLIC_KEY)
  data = AES_ECB_PKCS7_encrypt(JSON.stringify(e), t)
"""
from __future__ import annotations

import base64
import json
import random
import string

from Crypto.Cipher import AES
from Crypto.PublicKey import RSA
from Crypto.Cipher import PKCS1_v1_5
from Crypto.Util.Padding import pad, unpad

RSA_PUB_KEY_B64 = (
    "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDf3n7GvYCjevA+JEnMQHfxDX/e"
    "PSviRR2C2tsNSVyuTm6TfaP/HLzNbAO0kK+52nr2HO2LzsSd+a98V4n5npYDWPq"
    "bswXzKLj73kBlBI0P6Uf3uygCAZtfd9qkAn0DkgGpVw1VtCb33svBkaQinOYB55"
    "0OygDM1vemuQYq11E/mQIDAQAB"
)

_CHARS = string.ascii_letters + string.digits


def random_string(n: int = 16) -> str:
    """对应 app.js Z(n)：从字母+数字中随机取 n 位。"""
    return "".join(random.choice(_CHARS) for _ in range(n))


def _build_rsa_key() -> RSA.RsaKey:
    der = base64.b64decode(RSA_PUB_KEY_B64)
    return RSA.import_key(der)


def cryto_login(params: dict) -> dict:
    """对应 app.js function q(e)。

    返回 {"key": rsa加密的base64, "data": aes加密的base64}。
    """
    aes_key = random_string(16)
    rsa_key = _build_rsa_key()
    cipher_rsa = PKCS1_v1_5.new(rsa_key)
    encrypted_key = base64.b64encode(cipher_rsa.encrypt(aes_key.encode())).decode()

    plaintext = json.dumps(params, separators=(",", ":"), ensure_ascii=False)
    cipher_aes = AES.new(aes_key.encode(), AES.MODE_ECB)
    encrypted_data = base64.b64encode(
        cipher_aes.encrypt(pad(plaintext.encode("utf-8"), AES.block_size))
    ).decode()

    return {"key": encrypted_key, "data": encrypted_data}


def cryto_login_decrypt(key_b64: str, data_b64: str) -> dict:
    """解密 crytoLogin 输出（需要 RSA 私钥，此处仅用于验证格式）。

    由于没有 RSA 私钥，此函数仅解密 data 字段（需要已知 aes_key）。
    实际使用中不需要解密，只需对比加密前的明文格式。
    """
    raise NotImplementedError("RSA 私钥不可用，无法解密 key 字段")


def decrypt_with_key(data_b64: str, aes_key: str) -> dict:
    """用已知 AES key 解密 data 字段。"""
    cipher_aes = AES.new(aes_key.encode(), AES.MODE_ECB)
    decrypted = unpad(cipher_aes.decrypt(base64.b64decode(data_b64)), AES.block_size)
    return json.loads(decrypted.decode("utf-8"))


if __name__ == "__main__":
    import sys

    sample = {"mobile": "13800138000", "pathType": 11}
    if len(sys.argv) > 1:
        sample = json.loads(sys.argv[1])
    result = cryto_login(sample)
    print(json.dumps(result, indent=2, ensure_ascii=False))
