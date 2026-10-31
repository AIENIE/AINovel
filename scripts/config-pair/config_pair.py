"""Literal configuration helpers used by current local verification readers."""

from __future__ import annotations

import re

SECRET = re.compile(r"(?:PASSWORD|SECRET|TOKEN|API_KEY|AES_KEY|ENCRYPTION_KEY|PRIVATE_KEY|SIGNING_KEY|TOTP_KEY|KEYRING|CREDENTIALS|WEBHOOK_URL|AUTHORIZATION|AUTH_HEADER|_JWT$|_ACCESS_KEY$)")

NONSECRET = re.compile(r"(?:_TOKEN_(?:BUDGET|LIMIT|COUNT|TTL|SECONDS|MS|STRICT|URL|ENABLED|EXPIRE|EXPIRATION|ISSUER|AUDIENCE|SOURCE)|_TOKENS$|_KEY_(?:PREFIX|VERSION)|_KEY_VERSION$|_API_KEY_(?:ENCRYPTION_REPAIR|LENGTH)|_PASSWORD_(?:LOGIN|ENABLED|MIN|MAX|LENGTH|MODE)|_ALLOW_CREDENTIALS$|_SECRET_FILE$|_TLS_PRIVATE_KEY$|_PRIVATE_KEY_(?:HOST|PATH|FILE)$|_CREDENTIALS_(?:PATH|FILE)$)")

class ConfigError(ValueError):
    """Messages must contain key names or paths, never configuration values."""

def sensitive(key: str) -> bool:
    return key == "GRPC_AUTH_CALLERS" or bool(SECRET.search(key) and not NONSECRET.search(key))

def placeholders(value: str):
    """Yield top-level Spring placeholders, including nested defaults."""
    i = 0
    while i < len(value):
        start = value.find("${", i)
        if start < 0:
            return
        end, depth = start + 2, 1
        while end < len(value) and depth:
            if value.startswith("${", end):
                depth += 1
                end += 2
            elif value[end] == "{":
                depth += 1
                end += 1
            elif value[end] == "}":
                depth -= 1
                end += 1
            else:
                end += 1
        if depth:
            raise ConfigError("unterminated configuration placeholder")
        inner = value[start + 2:end - 1]
        key, sep, default = inner.partition(":")
        yield start, end, key, default if sep else None
        i = end

def get_property(document, path):
    current = document
    for m in re.finditer(r"\[([0-9]+)\]|[^.\[\]]+", path):
        token = int(m.group(1)) if m.group(1) is not None else m.group(0)
        try:
            current = current[token]
        except (KeyError, IndexError, TypeError):
            return None
    return current
