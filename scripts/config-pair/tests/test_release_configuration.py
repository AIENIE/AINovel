"""Regression coverage for the configuration API imported by the live release guard."""
from pathlib import Path
import shutil
import sys
import tempfile
import unittest

HELPERS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HELPERS))
from config_pair import ConfigError, parse_env, walk_nodes
from shell_configuration import configuration
import yaml


SHARED = "486f7ab3d02c6e19b574ef098a321d5ce764b2f80a1e396d45c07b8af9126e30"
APPLICATION = """runtime:
  configuration:
    ENV: test
    AUTH_MODE: totp
spring:
  datasource:
    url: jdbc:mysql://db.internal:3339/story?sslMode=REQUIRED
    username: root
    password: ${DB_PASSWORD}
app:
  admin-auth:
    username: operator
    password-hash: ${ADMIN_PASSWORD_HASH}
    encryption-keys: ${ADMIN_TOTP_ENCRYPTION_KEYS}
  external:
    security:
      ai:
        hmac-secret: ${GRPC_SHARED_SECRET}
      user:
        secret: ${GRPC_SHARED_SECRET}
      pay:
        secret: ${GRPC_SHARED_SECRET}
"""


class ReleaseConfigurationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        helpers = self.root / "scripts/config-pair"
        helpers.mkdir(parents=True)
        shutil.copyfile(HELPERS / "mapping.json", helpers / "mapping.json")
        self.application = self.root / "application.yml"
        self.application.write_text(APPLICATION, encoding="utf-8")
        self.env = self.root / "env.txt"
        self.secrets = {
            "DB_PASSWORD": "literal-$()-#-value",
            "ADMIN_PASSWORD_HASH": "$2a$12$deterministic.test.value",
            "ADMIN_TOTP_ENCRYPTION_KEYS": "v1:deterministic-key-material",
            "GRPC_SHARED_SECRET": SHARED,
        }
        self.write_secrets()

    def write_secrets(self):
        self.env.write_text("".join(key + "=" + value + "\n" for key, value in self.secrets.items()), encoding="utf-8")

    def test_active_guard_api_resolves_current_secrets_without_fixed_deployment_values(self):
        result = configuration(self.root, self.env)
        self.assertEqual("test", result["ENV"])
        self.assertEqual("totp", result["AUTH_MODE"])
        self.assertEqual("root", result["DB_USERNAME"])
        self.assertIn("db.internal:3339", result["DB_URL"])
        self.assertEqual(self.secrets["DB_PASSWORD"], result["DB_PASSWORD"])
        self.assertEqual(SHARED, result["GRPC_SHARED_SECRET"])

    def test_env_is_literal_and_accepts_bom_export_and_quotes(self):
        self.assertEqual({"DB_PASSWORD": "a$()#=b"}, parse_env('\ufeffexport DB_PASSWORD="a$()#=b"\r\n'))

    def test_duplicate_and_malformed_env_fail(self):
        for text in ("DB_PASSWORD=a\nDB_PASSWORD=b", "not-an-assignment"):
            with self.subTest(text=text), self.assertRaises(ConfigError):
                parse_env(text)

    def test_duplicate_yaml_fails_before_last_value_can_win(self):
        with self.assertRaises(ConfigError):
            list(walk_nodes(yaml.compose("app:\n  mode: first\n  mode: second\n")))

    def test_missing_required_credentials_fail(self):
        for key in tuple(self.secrets):
            with self.subTest(key=key):
                original = self.secrets.pop(key)
                try:
                    self.write_secrets()
                    with self.assertRaises(ConfigError):
                        configuration(self.root, self.env)
                finally:
                    self.secrets[key] = original
        self.write_secrets()

    def test_test_environment_requires_totp(self):
        self.application.write_text(APPLICATION.replace("AUTH_MODE: totp", "AUTH_MODE: password"), encoding="utf-8")
        with self.assertRaises(ConfigError):
            configuration(self.root, self.env)

    def test_nonsecret_values_still_belong_in_yaml(self):
        self.secrets["ENV"] = "test"
        self.write_secrets()
        with self.assertRaises(ConfigError):
            configuration(self.root, self.env)

    def test_obsolete_credential_name_does_not_replace_current_credentials_or_reject_start(self):
        self.secrets["EXTERNAL_PAY_SERVICE_JWT"] = "retired-unused-value"
        self.write_secrets()
        result = configuration(self.root, self.env)
        self.assertEqual(SHARED, result["GRPC_SHARED_SECRET"])
        self.assertEqual("retired-unused-value", result["EXTERNAL_PAY_SERVICE_JWT"])

    def test_weak_current_shared_key_fails(self):
        self.secrets["GRPC_SHARED_SECRET"] = "x" * 64
        self.write_secrets()
        with self.assertRaises(ConfigError):
            configuration(self.root, self.env)

    def test_cyclic_secret_reference_fails(self):
        self.application.write_text(APPLICATION.replace("${DB_PASSWORD}", "${spring.datasource.password}"), encoding="utf-8")
        with self.assertRaises(ConfigError):
            configuration(self.root, self.env)


if __name__ == "__main__":
    unittest.main()
