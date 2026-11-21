#!/usr/bin/env python3
"""生成固定发布签名（PKCS12），让每次 CI 构建出的包签名一致，避免覆盖安装冲突。

用法：python3 tools/make_keystore.py [输出路径]
默认输出 keystore/zhiwei-release.jks（内容为 PKCS12，Gradle 侧通过 storeType 指定）。
"""
import datetime
import os
import sys

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID

STORE_PASSWORD = "zhiwei2026"
KEY_ALIAS = "zhiwei"


def main() -> int:
    out = sys.argv[1] if len(sys.argv) > 1 else "keystore/zhiwei-release.jks"
    os.makedirs(os.path.dirname(out) or ".", exist_ok=True)

    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    subject = issuer = x509.Name([
        x509.NameAttribute(NameOID.COUNTRY_NAME, "CN"),
        x509.NameAttribute(NameOID.STATE_OR_PROVINCE_NAME, "Beijing"),
        x509.NameAttribute(NameOID.LOCALITY_NAME, "Beijing"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "Zhiwei"),
        x509.NameAttribute(NameOID.ORGANIZATIONAL_UNIT_NAME, "Zhiwei Store"),
        x509.NameAttribute(NameOID.COMMON_NAME, "Zhiwei Store Release"),
    ])
    now = datetime.datetime.now(datetime.timezone.utc)
    cert = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(issuer)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - datetime.timedelta(days=1))
        .not_valid_after(now + datetime.timedelta(days=365 * 30))
        .add_extension(x509.BasicConstraints(ca=True, path_length=None), critical=True)
        .sign(key, hashes.SHA256())
    )

    blob = pkcs12.serialize_key_and_certificates(
        name=KEY_ALIAS.encode(),
        key=key,
        cert=cert,
        cas=None,
        encryption_algorithm=serialization.BestAvailableEncryption(STORE_PASSWORD.encode()),
    )
    with open(out, "wb") as f:
        f.write(blob)

    fp = cert.fingerprint(hashes.SHA256()).hex().upper()
    print(f"written: {out} ({len(blob)} bytes)")
    print(f"alias: {KEY_ALIAS}  storePassword/keyPassword: {STORE_PASSWORD}")
    print("SHA-256:", ":".join(fp[i:i + 2] for i in range(0, len(fp), 2)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())