import datetime as dt
import importlib.util
import io
import json
import pathlib
import tempfile
import unittest
import urllib.error
import zipfile
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("dependency_security", pathlib.Path(__file__).with_name("dependency-security.py"))
security = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(security)


class DependencySecurityTest(unittest.TestCase):
    def test_network_failure_and_incomplete_results_fail_closed(self):
        components = ["pkg:maven/example/a@1"]
        for result in ({}, {"results": []}, {"results": [None]}, {"results": [{"next_page_token": "more"}]}):
            with self.subTest(result=result), self.assertRaises(ValueError):
                security.osv_findings(components, lambda *args: result)
        with self.assertRaises(ValueError):
            security.osv_findings([], lambda *args: {})
        with self.assertRaises(urllib.error.URLError):
            security.osv_findings(components, lambda *args: (_ for _ in ()).throw(urllib.error.URLError("offline")))

    def test_valid_empty_vulnerability_result_requires_one_result_per_component(self):
        findings, raw = security.osv_findings(["pkg:maven/example/a@1"], lambda *args: {"results": [{}]})
        self.assertEqual([], findings)
        self.assertEqual([{"component": "pkg:maven/example/a@1", "result": {}}], raw["queries"])

    def test_high_and_unknown_block_moderate_does_not(self):
        findings = [{"id": "GHSA-test", "component": "pkg:maven/a/b@1", "severity": level}
                    for level in ("HIGH", "UNKNOWN", "MODERATE")]
        self.assertEqual(2, len(security.apply_policy(findings, {"exceptions": []})))
        with self.assertRaises(ValueError):
            security.apply_policy([], {"exceptions": [{"id": "x"}]})
        with self.assertRaises(ValueError):
            security.apply_policy([], {"exceptions": [{"id": "x", "component": "x", "reason": "r", "owner": "o", "expires": "2020-01-01"}]})

    def test_exact_jar_inventory_includes_shaded_netty_and_rejects_unknown_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            nested = io.BytesIO()
            with zipfile.ZipFile(nested, "w") as jar:
                jar.writestr("META-INF/io.netty.versions.properties", "netty-transport-native-epoll.version=4.1.100.Final\n")
                jar.writestr("io/grpc/netty/shaded/io/netty/handler/codec/http2/Test.class", b"fixture")
            artifact = root / "repo/io/grpc/grpc-netty-shaded/1.63.0/grpc-netty-shaded-1.63.0.jar"
            artifact.parent.mkdir(parents=True)
            artifact.write_bytes(nested.getvalue())
            app = root / "app.jar"
            with zipfile.ZipFile(app, "w") as jar:
                jar.writestr("BOOT-INF/lib/" + artifact.name, nested.getvalue())
            inventory = security.jar_inventory(app, root / "repo")
            self.assertIn("pkg:maven/io.netty/netty-codec-http2@4.1.100.Final", inventory["components"])
            self.assertIn("pkg:maven/io.grpc/grpc-netty-shaded@1.63.0", inventory["components"])
            artifact.write_bytes(b"tampered")
            with self.assertRaises(ValueError):
                security.jar_inventory(app, root / "repo")
            with zipfile.ZipFile(app, "w") as jar:
                jar.writestr("application.class", b"fixture")
            with self.assertRaises(ValueError):
                security.jar_inventory(app, root / "repo")

    def test_report_bindings_reject_missing_failed_changed_and_forged_empty_inventory(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            for relative, content in {"backend/pom.xml": "<project/>", "scripts/ci/dependency-security.py": "fixture",
                                      "scripts/ci/security-exceptions.json": '{"exceptions":[]}'}.items():
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
            path = root / "report.json"
            report = {"schema": security.SCHEMA, "kind": "maven", "module": "backend", "source_commit": "a" * 40,
                      "inputs": security.input_hashes(root, "backend", "maven"), "status": "passed",
                      "blocking_count": 0, "findings": [], "raw": {"queries": [{"component": "fixture", "result": {}}]}, "inventory": {"components": ["fixture"]}}
            security.write_json(path, report)
            # No network primitive may be called when consuming offline evidence.
            with patch.object(security, "request_json", side_effect=AssertionError("network during offline verification")):
                security.verify_report(root, "backend", "maven", path, "a" * 40)
            for field, bad in (("status", "failed"), ("source_commit", "b" * 40), ("inventory", {}), ("raw", None), ("inputs", {})):
                security.write_json(path, {**report, field: bad})
                with self.subTest(field=field), self.assertRaises(ValueError):
                    security.verify_report(root, "backend", "maven", path, "a" * 40)
            with self.assertRaises(OSError):
                security.verify_report(root, "backend", "maven", root / "missing.json", "a" * 40)


if __name__ == "__main__":
    unittest.main()
