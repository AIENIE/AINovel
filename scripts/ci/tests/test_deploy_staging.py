"""Staging configuration and deployment ordering, without Docker or model calls."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

CI = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(CI.parent / "config-pair" / "tests"))
from test_release_configuration import APPLICATION, SHARED

spec = importlib.util.spec_from_file_location("deploy_staging", CI / "deploy_staging.py")
deployment = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deployment)


class DeployStagingTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.candidate = self.root / "candidate"
        self.live = self.root / "live"
        for root in (self.candidate, self.live):
            (root / "release").mkdir(parents=True)
            (root / "scripts/config-pair").mkdir(parents=True)
            shutil.copyfile(CI.parent / "config-pair/mapping.json", root / "scripts/config-pair/mapping.json")
            (root / "application.yml").write_text(APPLICATION, encoding="utf-8")
            (root / "env.txt").write_text("DB_PASSWORD=literal-value\nADMIN_PASSWORD_HASH=$2a$test\n"
                "ADMIN_TOTP_ENCRYPTION_KEYS=v1:test-value\nGRPC_SHARED_SECRET=" + SHARED +
                "\nEXTERNAL_PAY_SERVICE_JWT=retired-unused-value\n", encoding="utf-8")
            (root / "env.txt").chmod(0o600)
            (root / ".aienie-runtime-config-format").write_text("application-yaml-env-v1")
            (root / ".aienie-runtime-grpc-auth-format").write_text("grpc-shared-key-v1")
            (root / "images.env").write_text("AINOVEL_BACKEND_IMAGE=example/backend@sha256:" + "a" * 64 +
                "\nAINOVEL_FRONTEND_IMAGE=example/frontend@sha256:" + "b" * 64 + "\n")
            (root / "docker-compose.yml").write_text("services:\n  backend:\n    volumes:\n"
                "      - ./env.txt:/app/env.txt:ro\n      - ./application.yml:/app/application.yml:ro\n  frontend: {}\n")
        self.spec = {"layout": "live", "logging": {"max-size": "10m", "max-file": "3"},
            "force_recreate": True, "environments": {"staging": {"persistent_dirs": ["logs/backend", "logs/records"]}},
            "configuration_groups": [{"application_path": "application.yml", "secrets_path": "env.txt",
                "source_root": ".", "application_mount": "/app/application.yml", "secrets_mount": "/app/env.txt",
                "retired_administrator_path": "admin.txt"}]}
        (self.candidate / "release/deployment.json").write_text(json.dumps(self.spec))
        self.arguments = (self.candidate, self.live, "test-version", "ai-novel", "staging", "test-release")

    @unittest.skipIf(os.name == "nt", "POSIX configuration file modes")
    def test_real_configuration_accepts_current_credentials_and_unused_old_name(self):
        deployment.validate_bundle(self.candidate, self.spec)

    @unittest.skipIf(os.name == "nt", "POSIX configuration file modes")
    def test_bad_configuration_fails_before_any_materialization(self):
        for invalid in (APPLICATION + "\napp: {}\n", APPLICATION.replace("AUTH_MODE: totp", "AUTH_MODE: password")):
            with self.subTest(invalid=invalid), patch.object(deployment, "run") as command:
                (self.candidate / "application.yml").write_text(invalid, encoding="utf-8")
                with self.assertRaises(deployment.ConfigError):
                    deployment.deploy(*self.arguments)
                command.assert_not_called()

    @unittest.skipIf(os.name == "nt", "POSIX configuration file modes")
    def test_writable_secret_and_writable_mount_are_rejected(self):
        secret = self.candidate / "env.txt"
        secret.chmod(0o644)
        with self.assertRaises(deployment.ConfigError):
            deployment.validate_bundle(self.candidate, self.spec)
        secret.chmod(0o600)
        compose = self.candidate / "docker-compose.yml"
        compose.write_text(compose.read_text().replace(":ro", ":rw"))
        with self.assertRaises(deployment.ConfigError):
            deployment.validate_bundle(self.candidate, self.spec)

    def test_validation_failure_never_runs_commands(self):
        with patch.object(deployment, "validate_bundle", side_effect=deployment.ConfigError("bad input")), \
                patch.object(deployment, "run") as command:
            with self.assertRaises(deployment.ConfigError):
                deployment.deploy(*self.arguments)
            command.assert_not_called()

    def test_preserves_logs_and_uses_frozen_images_and_release_labels(self):
        log = self.live / "logs/backend/existing.log"
        log.parent.mkdir(parents=True)
        log.write_text("existing evidence")
        def execute(arguments, environment=None, capture=False):
            return '{"services":{"backend":{},"frontend":{}}}' if arguments[-3:] == ["config", "--format", "json"] else ""
        with patch.dict(os.environ, {"DB_PASSWORD": "inherited-process-value"}), \
                patch.object(deployment, "validate_bundle"), patch.object(deployment, "run", side_effect=execute) as command:
            result = deployment.deploy(*self.arguments)
        self.assertEqual("succeeded", result["status"])
        self.assertEqual("existing evidence", log.read_text())
        commands = [call.args[0] for call in command.call_args_list]
        rsync = next(args for args in commands if args[0] == "rsync")
        self.assertIn("--exclude=/.aienie-releases/", rsync)
        self.assertIn("--exclude=/backups/", rsync)
        self.assertIn("--exclude=/logs/", rsync)
        self.assertIn("--no-build", commands[-1])
        self.assertIn("--force-recreate", commands[-1])
        config_call = next(call for call in command.call_args_list if call.args[0][-3:] == ["config", "--format", "json"])
        env = config_call.args[1]
        self.assertTrue(env["AINOVEL_BACKEND_IMAGE"].endswith("a" * 64))
        self.assertEqual("inherited-process-value", env["DB_PASSWORD"])
        self.assertEqual(str(self.candidate.joinpath("env.txt").stat().st_uid), env["AIENIE_RUNTIME_UID"])
        overrides = json.loads((self.live / ".compose-release.json").read_text())
        self.assertEqual("test-release", overrides["services"]["backend"]["labels"]["com.aienie.release_id"])

    def test_pull_failure_does_not_start_containers(self):
        def execute(arguments, environment=None, capture=False):
            if arguments[-1] == "pull":
                raise subprocess.CalledProcessError(1, arguments)
            return '{"services":{"backend":{},"frontend":{}}}' if arguments[-3:] == ["config", "--format", "json"] else ""
        with patch.object(deployment, "validate_bundle"), patch.object(deployment, "run", side_effect=execute) as command:
            with self.assertRaises(subprocess.CalledProcessError):
                deployment.deploy(*self.arguments)
        self.assertFalse(any("up" in call.args[0] for call in command.call_args_list))

    def test_other_environments_do_not_use_this_staging_entry(self):
        with patch.object(deployment, "run") as command:
            with self.assertRaises(ValueError):
                deployment.deploy(self.candidate, self.live, "test", "ai-novel", "production", "test-release")
            command.assert_not_called()


if __name__ == "__main__":
    unittest.main()
