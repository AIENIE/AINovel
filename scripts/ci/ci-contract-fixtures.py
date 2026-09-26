#!/usr/bin/env python3
"""Synthetic tools used ONLY by test-aienie-ci-contract.sh, never release builds."""
import importlib.util
import io
import json
import os
import pathlib
import sys
import zipfile


def zip_bytes(entries):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w") as archive:
        for name, value in entries.items():
            archive.writestr(zipfile.ZipInfo(name), value)
    return stream.getvalue()


def main():
    tool, *args = sys.argv[1:]
    if tool == "mvn":
        if "--version" in args:
            print("Apache Maven 3.9.99-test")
            return 0
        cache = pathlib.Path(os.environ["AIENIE_CI_CACHE_DIR"]) / "maven"
        cache.mkdir(parents=True, exist_ok=True)
        for arg in args:
            if arg.startswith("-Doutput="):
                pathlib.Path(arg.split("=", 1)[1]).write_text(
                    "<project><properties><junit-platform.version>1.12.2</junit-platform.version></properties></project>")
        if "package" in args:
            dependency = zip_bytes({"META-INF/maven/example/fixture/pom.properties":
                                    "groupId=example\nartifactId=fixture\nversion=1.0\n"})
            artifact = cache / "example/fixture/1.0/fixture-1.0.jar"
            artifact.parent.mkdir(parents=True, exist_ok=True)
            artifact.write_bytes(dependency)
            target = pathlib.Path(os.environ["AIENIE_CI_REPO_ROOT"]) / "backend/target"
            target.mkdir(parents=True, exist_ok=True)
            (target / "fixture.jar").write_bytes(zip_bytes({"BOOT-INF/lib/fixture-1.0.jar": dependency}))
        return 0
    if tool == "pnpm":
        if "--version" in args:
            print("11.22.0")
        elif "audit" in args:
            if os.environ.get("AIENIE_CI_PHASE") != "resolve":
                raise RuntimeError("audit must never run during offline build")
            print(json.dumps({"advisories": {}, "metadata": {"totalDependencies": 1,
                  "vulnerabilities": dict.fromkeys(("info", "low", "moderate", "high", "critical"), 0)}}))
        else:
            cache = pathlib.Path(os.environ["AIENIE_CI_CACHE_DIR"]) / "pnpm"
            cache.mkdir(parents=True, exist_ok=True)
            (cache / "fixture.bin").write_text("fixture")
        return 0
    if tool == "scanner":
        script, *arguments = args
        spec = importlib.util.spec_from_file_location("security_fixture", script)
        scanner = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(scanner)

        def response(request, **kwargs):
            if os.environ.get("AIENIE_CI_PHASE") != "resolve":
                raise RuntimeError("OSV network must never run during offline build")
            request_body = json.loads(request.data)
            if not request.full_url.endswith("/querybatch"):
                raise RuntimeError("unexpected fixture endpoint")
            result = io.BytesIO(json.dumps({"results": [{} for _ in request_body["queries"]]}).encode())
            result.status = 200
            return result

        # Only the transport is simulated; inventory, policy, report and offline
        # verification all execute the production implementation.
        scanner.urllib.request.urlopen = response
        sys.argv = [script, *arguments]
        return scanner.main()
    raise ValueError("Unknown test fixture tool")


if __name__ == "__main__":
    sys.exit(main())
