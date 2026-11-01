"""Validate the effective YAML/env pair consumed by the active release guard."""
import json
import yaml
from config_pair import ConfigError, get_property, parse_env, placeholders, sensitive, walk_nodes
from grpc_shared import validate_secret
from read_configuration import read


def configuration(root, envfile):
    application = envfile.with_name("application.yml")
    companion = envfile.with_name(envfile.name + ".application.yml")
    if companion.is_file():
        application = companion
    if not application.is_file() or application.is_symlink():
        raise ConfigError("missing regular application.yml")
    text = application.read_text(encoding="utf-8")
    nodes = list(walk_nodes(yaml.compose(text)))
    values = parse_env(envfile.read_text(encoding="utf-8"))
    if any(not sensitive(key) for key in values):
        raise ConfigError("env.txt may contain only sensitive keys")
    values.update(read(root, "", application))
    mapping = json.loads((root / "scripts/config-pair/mapping.json").read_text(encoding="utf-8"))["bindings"]
    if "GRPC_SHARED_SECRET" in values or "GRPC_SHARED_SECRET" in mapping:
        validate_secret(values.get("GRPC_SHARED_SECRET"))
    document = yaml.safe_load(text)

    def resolve(value, seen=()):
        if not isinstance(value, str):
            return value
        result, cursor = [], 0
        for start, end, key, default in placeholders(value):
            if key in seen:
                raise ConfigError("cyclic configuration reference")
            replacement = values.get(key)
            if replacement is None:
                replacement = get_property(document, key)
            if replacement is None:
                replacement = default
            if replacement is None:
                raise ConfigError("missing required secret: " + key)
            result.extend([value[cursor:start], str(resolve(replacement, (*seen, key)))])
            cursor = end
        return "".join(result) + value[cursor:]

    for key, paths in mapping.items():
        if not sensitive(key):
            continue
        for path in paths:
            raw = get_property(document, path)
            if raw is not None:
                values[key] = resolve(raw)
                break
    env, mode = values.get("ENV"), values.get("AUTH_MODE")
    if env not in ("local", "test", "production") or mode not in ("password", "totp") or (env != "local" and mode != "totp"):
        raise ConfigError("invalid effective ENV/AUTH_MODE")
    if "DB_PASSWORD" in mapping and not str(values.get("DB_PASSWORD", "")).strip():
        raise ConfigError("missing required database credential: DB_PASSWORD")
    if "ADMIN_USERNAME" in mapping:
        if not str(values.get("ADMIN_USERNAME", "")).strip():
            raise ConfigError("missing administrator identity")
        if not str(values.get("ADMIN_PASSWORD_HASH", "")).strip() and not str(values.get("ADMIN_PASSWORD", "")).strip():
            raise ConfigError("missing administrator credential")
    if mode == "totp" and "ADMIN_TOTP_ENCRYPTION_KEYS" in mapping and not str(values.get("ADMIN_TOTP_ENCRYPTION_KEYS", "")).strip():
        raise ConfigError("missing required TOTP credential: ADMIN_TOTP_ENCRYPTION_KEYS")
    for _, node in nodes:
        if not isinstance(node.value, str):
            continue
        for _, _, key, default in placeholders(node.value):
            if sensitive(key) and default is None and not values.get(key):
                raise ConfigError("missing required secret: " + key)
    return values
