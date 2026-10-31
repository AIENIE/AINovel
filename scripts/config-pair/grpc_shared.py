"""Current shared gRPC key format used by the release configuration guard."""
from config_pair import ConfigError


def validate_secret(value):
    if not isinstance(value, str) or not 32 <= len(value.encode()) <= 4096 or len(set(value)) < 8:
        raise ConfigError("invalid environment gRPC shared secret")
    if any(c.isspace() or ord(c) < 32 for c in value) or any(
        token in value.lower() for token in (
            "${", "replace", "change-me", "change_me", "changeme", "placeholder",
            "example", "fixture", "dummy", "password", "sample", "<", ">",
        )
    ):
        raise ConfigError("invalid environment gRPC shared secret")
