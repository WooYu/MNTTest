#!/usr/bin/python
# -*- coding: utf-8 -*-
# @File  : get_token.py
# @Author: liwang
# @Time  : 2025-02-26 10:23

import json
import urllib
import requests
import hashlib
from Crypto.PublicKey import RSA
from Crypto.Cipher import PKCS1_v1_5
from Crypto.Random import get_random_bytes
import base64
from loguru import logger
KEY_SIZE = 1024
MAX_ENCRYPT_SIZE = 86  # For RSA key size 1024, the max size for PKCS1_v1_5 is 117 bytes, considering padding
MAX_DECRYPT_SIZE = 128
DEFAULT_CHARSET = "utf-8"
PUBLIC_KEY = """MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCOn9RRfPGnOd7psFdHS5w0+JlV+URq899c3h8w35o6r2KkBN0FdhAn3Q3dMOaCG1vWLMc2iGgNe/xnul2d9W7GrdTgG4KWsGgaIo8+ESlf+QFEutXPsG7u6SziOYu07DaGk7Lriqof2ZEY2GAspSbjXVXr7xTI0Ej16RQmW0ox/QIDAQAB"""  # 使用实际的公钥内容
URL = "https://open.feishu.cn/open-apis/bot/v2/hook/3ea9eb08-bc02-42d9-b678-68874513078e"

envs = {
    "testcn": "https://autel-cloud-gateway-test.auteltech.cn",
    "testus": "https://autel-cloud-gateway-testus.autel.com",
    "pre": "https://autel-cloud-gateway-pre.autel.com",
    "prodcn": "https://autel-cloud-gateway-prodcn.auteltech.cn",
    "produs": "https://gateway.autel.com",
    "prodeu": "https://gateway-prodeu.autel.com",
}

def get_public_key_by_sn(sn):
    if sn.startswith("CXK") or sn.startswith("CFJV") or sn.startswith("CBJ") or sn.startswith("CGJM"):
        return """MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDBXz2gGb8on23in4ZldrTo6AROnU/olS1C6Os5yEXb8U2gMMD1USxp4tRMEq2uWxMhRbBpy5OqCkFtUoR6jrfxigBWaR6z48qIwcJPWs07hHWzyA23b/HXNvOMXTpA5YSfb9H5UY0iIRXWGXfA7V7VAnaLSGv9IPSHqoiKkYmT8QIDAQAB"""
    else:
        return """MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCOn9RRfPGnOd7psFdHS5w0+JlV+URq899c3h8w35o6r2KkBN0FdhAn3Q3dMOaCG1vWLMc2iGgNe/xnul2d9W7GrdTgG4KWsGgaIo8+ESlf+QFEutXPsG7u6SziOYu07DaGk7Lriqof2ZEY2GAspSbjXVXr7xTI0Ej16RQmW0ox/QIDAQAB"""


def get_md5_value(string):
    # 计算 MD5 值
    return hashlib.md5(string.encode('utf-8')).hexdigest()

# 获取临时key/api/device/product/auth/valid-key
def get_tmp_key(domain):
    result = requests.get(domain+"/api/device/product/auth/valid-key")
    tmp_key = result.json()["data"]
    # print(tmp_key)
    return tmp_key

# 拼接字符串
def get_encrypt_str(tmp_key, sn, pwd, mac):
    sb = []
    sb.append("type=1&sn=")
    sb.append(sn)
    sb.append("&pwd=")
    sb.append(pwd)
    sb.append("&mac1=")
    sb.append("&mac2=")
    sb.append(mac)
    sb.append("&mac3=")
    sb.append("&salt=")
    # 无实际用处
    sb.append("3c28b87f8a4b342843847bfeb9e3f3f6")
    sign = get_md5_value("".join(sb))
    sb.clear()

    sb.append("type=1&valid=")
    sb.append(tmp_key)
    sb.append("&sn=")
    sb.append(sn)
    sb.append("&pwd=")
    sb.append(pwd)
    sb.append("&mac1=")
    sb.append("&mac2=")
    sb.append(mac)
    sb.append("&mac3=")
    sb.append("&sign=")
    sb.append(sign)
    # print("".join(sb))
    return "".join(sb)

# 加密字符串
def encrypt_by_public_key(data, key):
    try:
        public_key_bytes = base64.b64decode(key.encode(DEFAULT_CHARSET))
        public_key = RSA.import_key(public_key_bytes)

        cipher = PKCS1_v1_5.new(public_key)
        encrypted_data = encoding(data.encode(DEFAULT_CHARSET), cipher.encrypt, MAX_ENCRYPT_SIZE)
        return base64.b64encode(encrypted_data).decode(DEFAULT_CHARSET)
    except Exception as e:
        logger.error(f"An error occurred during encryption: {e}")
        return None

def encoding(bytes_data, cipher_func, max_size):
    length = len(bytes_data)
    offset = 0
    result = b""

    while length - offset > 0:
        if length - offset > max_size:
            cache = cipher_func(bytes_data[offset:offset + max_size])
            offset += max_size
        else:
            cache = cipher_func(bytes_data[offset:offset + length - offset])
            offset = length

        result += cache
    return result

# 获取token/api/base-uc-app/portal/v2/device-login
def get_token(*args):
    data = get_encrypt_str(get_tmp_key(envs[args[0]]), args[1], args[2], args[3])
    encrypt = encrypt_by_public_key(data, get_public_key_by_sn(args[1]))
    params = {
        "encrypt": urllib.parse.quote(encrypt, encoding="utf-8"),
        "password": args[2],
        "serialNo": args[1]
    }
    result = requests.post(envs[args[0]] + "/api/base-uc-app/portal/v2/device-login", json=params, headers={"content-type": "application/json", "accept-language": "zh-CN"}).json()
    if result['code'] == 200:
        return result['data']['token']
    else:
        logger.error(result['message'])
        return ""

if __name__ == '__main__':
    print(get_token('testcn', 'CBJ2TEST1001', '', ''))

