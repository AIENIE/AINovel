#!/usr/bin/env python3
"""Fail-closed PNPM/packaged-JAR SCA with input-bound, offline-verifiable evidence.

Only package coordinates are sent to OSV. No application classes, configuration,
credentials or source files leave the build agent. Python 3 standard library only.
"""
import argparse
import datetime as dt
import hashlib
import io
import json
import pathlib
import re
import subprocess
import sys
import urllib.error
import urllib.request
import zipfile

SCHEMA = "ainovel-dependency-security-v1"
ROOT = pathlib.Path(__file__).resolve().parents[2]
POLICY = pathlib.Path(__file__).with_name("security-exceptions.json")
OSV = "https://api.osv.dev/v1/"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":")).encode()


def read_json(path):
    return json.loads(pathlib.Path(path).read_text(encoding="utf-8-sig"))


def write_json(path, value):
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(canonical(value) + b"\n")


def input_hashes(root, module, kind):
    paths = [f"{module}/pom.xml"] if kind == "maven" else [f"{module}/package.json", f"{module}/pnpm-lock.yaml"]
    paths += ["scripts/ci/dependency-security.py", "scripts/ci/security-exceptions.json"]
    if kind == "pnpm":
        paths.append(f"{module}/pnpm-workspace.yaml")
    return {p: digest((root / p).read_bytes()) for p in paths}


def properties(data):
    result = {}
    for line in data.decode("utf-8", errors="strict").splitlines():
        if line and not line.startswith(("#", "!")) and "=" in line:
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip()
    return result


def jar_inventory(jar_path, maven_repo):
    """Bind every runtime JAR to exact Maven cache bytes; inspect shaded Netty too."""
    repo = pathlib.Path(maven_repo).resolve()
    if not repo.is_dir():
        raise ValueError("Maven repository missing")
    with zipfile.ZipFile(jar_path) as app:
        entries = [e for e in app.namelist() if e.startswith("BOOT-INF/lib/") and e.endswith(".jar")]
        if not entries or len(entries) != len(set(entries)):
            raise ValueError("Packaged Spring Boot library inventory missing or duplicated")
        basenames = {pathlib.PurePosixPath(e).name for e in entries}
        cached = {}
        for candidate in repo.rglob("*.jar"):
            if candidate.name not in basenames or candidate.is_symlink():
                continue
            parts = candidate.relative_to(repo).parts
            if len(parts) < 4:
                continue
            artifact, version = parts[-3:-1]
            if not candidate.name.startswith(f"{artifact}-{version}"):
                continue
            coordinate = (".".join(parts[:-3]), artifact, version)
            cached.setdefault(candidate.name, []).append((candidate, coordinate))
        libraries, components = [], set()
        for entry in sorted(entries):
            data = app.read(entry)
            sha = digest(data)
            matches = {coordinate for path, coordinate in cached.get(pathlib.PurePosixPath(entry).name, [])
                       if digest(path.read_bytes()) == sha}
            if len(matches) != 1:
                raise ValueError(f"Cannot uniquely identify packaged library from exact cache bytes: {entry}")
            group, artifact, version = matches.pop()
            component = f"pkg:maven/{group}/{artifact}@{version}"
            library_components = {component}
            with zipfile.ZipFile(io.BytesIO(data)) as jar:
                names = jar.namelist()
                # Merged metadata in grpc-netty-shaded may retain only one Netty
                # module. The bundled Netty family is built at one common version;
                # enumerate actual shaded class namespaces, never rely on Boot's
                # unrelated unshaded netty.version.
                prefix = "io/grpc/netty/shaded/io/netty/"
                if any(n.startswith(prefix) for n in names):
                    metadata = [n for n in names if n.endswith("io.netty.versions.properties")]
                    versions = {v for n in metadata for k, v in properties(jar.read(n)).items() if k.endswith(".version")}
                    if len(versions) != 1:
                        raise ValueError(f"Missing/ambiguous shaded Netty version: {entry}")
                    netty_version = versions.pop()
                    namespaces = {
                        "buffer/": "netty-buffer", "util/": "netty-common", "channel/": "netty-transport",
                        "resolver/": "netty-resolver", "handler/ssl/": "netty-handler",
                        "handler/codec/": "netty-codec", "handler/codec/http/": "netty-codec-http",
                        "handler/codec/http2/": "netty-codec-http2", "handler/proxy/": "netty-handler-proxy",
                        "channel/epoll/": "netty-transport-native-epoll",
                    }
                    for namespace, name in namespaces.items():
                        if any(n.startswith(prefix + namespace) and n.endswith(".class") for n in names):
                            library_components.add(f"pkg:maven/io.netty/{name}@{netty_version}")
                for name in names:
                    if name.endswith("pom.properties"):
                        prop = properties(jar.read(name))
                        if all(prop.get(k) for k in ("groupId", "artifactId", "version")):
                            library_components.add(f"pkg:maven/{prop['groupId']}/{prop['artifactId']}@{prop['version']}")
            components.update(library_components)
            libraries.append({"path": entry, "sha256": sha, "components": sorted(library_components)})
    return {"libraries": libraries, "components": sorted(components),
            "coverage": "All BOOT-INF/lib JARs matched by SHA-256; embedded Maven metadata and shaded Netty namespaces. Native binary CVEs without Maven identity require separate platform SCA."}


def request_json(path, body=None):
    data = canonical(body) if body is not None else None
    request = urllib.request.Request(OSV + path, data=data, headers={"Content-Type": "application/json", "User-Agent": SCHEMA})
    with urllib.request.urlopen(request, timeout=45) as response:
        if response.status != 200:
            raise ValueError("OSV did not return HTTP 200")
        return json.load(response)


def osv_findings(components, fetch=request_json):
    if not components:
        raise ValueError("Empty Maven component inventory")
    findings, details, queries = [], {}, []
    for start in range(0, len(components), 100):
        batch = components[start:start + 100]
        answer = fetch("querybatch", {"queries": [{"package": {"purl": p}} for p in batch]})
        results = answer.get("results") if isinstance(answer, dict) else None
        if not isinstance(results, list) or len(results) != len(batch):
            raise ValueError("OSV returned missing/incomplete query results")
        for component, result in zip(batch, results):
            if not isinstance(result, dict) or result.get("next_page_token"):
                raise ValueError("OSV result is invalid or paginated; do not silently omit findings")
            queries.append({"component": component, "result": result})
            vulns = result.get("vulns", [])
            if not isinstance(vulns, list):
                raise ValueError("Invalid OSV vulnerability list")
            for vuln in vulns:
                identifier = vuln.get("id")
                if not isinstance(identifier, str) or not re.fullmatch(r"[A-Za-z0-9-]+", identifier):
                    raise ValueError("Invalid OSV vulnerability identifier")
                if identifier not in details:
                    details[identifier] = fetch("vulns/" + identifier)
                detail = details[identifier]
                if detail.get("id") != identifier:
                    raise ValueError("OSV vulnerability response identity mismatch")
                if detail.get("withdrawn"):
                    continue
                severity = str(detail.get("database_specific", {}).get("severity", "UNKNOWN")).upper()
                # Unknown severity blocks too: an incomplete advisory is not a clean result.
                findings.append({"id": identifier, "component": component, "severity": severity,
                                 "summary": detail.get("summary", ""), "aliases": detail.get("aliases", [])})
    return findings, {"queries": queries, "vulnerabilities": details}


def apply_policy(findings, policy, today=None):
    today = today or dt.datetime.now(dt.timezone.utc).date()
    exceptions = policy.get("exceptions")
    if not isinstance(exceptions, list):
        raise ValueError("Missing security exception policy")
    indexed = {}
    for item in exceptions:
        if not all(item.get(k) for k in ("id", "component", "reason", "owner", "expires")):
            raise ValueError("Incomplete security exception")
        if dt.date.fromisoformat(item["expires"]) < today:
            raise ValueError("Expired security exception: " + item["id"])
        key = (item["id"], item["component"])
        if key in indexed:
            raise ValueError("Duplicate security exception")
        indexed[key] = item
    blocking = []
    for finding in findings:
        exception = indexed.get((finding["id"], finding["component"]))
        finding["exception"] = exception
        if finding["severity"].upper() not in ("LOW", "MODERATE", "MEDIUM", "INFO") and not exception:
            blocking.append(finding)
    return blocking


def pnpm_audit(module_dir, command):
    result = subprocess.run([*command, "--dir", str(module_dir), "audit", "--json"], capture_output=True, text=True, encoding="utf-8")
    if result.returncode not in (0, 1):
        raise ValueError(f"pnpm audit failed with exit {result.returncode}")
    raw = json.loads(result.stdout)
    validate_pnpm_raw(raw)
    findings = []
    for advisory in raw["advisories"].values():
        for match in advisory.get("findings", []):
            findings.append({"id": advisory["github_advisory_id"],
                             "component": f"pkg:npm/{advisory['module_name']}@{match['version']}",
                             "severity": advisory["severity"].upper(), "summary": advisory["title"],
                             "paths": match["paths"], "dev": match.get("dev")})
    expected = sum(raw["metadata"]["vulnerabilities"].values())
    if expected and not findings:
        raise ValueError("pnpm reported vulnerabilities without advisory details")
    return findings, raw


def validate_pnpm_raw(raw):
    counts = raw.get("metadata", {}).get("vulnerabilities")
    if not isinstance(raw.get("advisories"), dict) or not isinstance(counts, dict):
        raise ValueError("pnpm audit response lacks a complete advisory result")
    if any(not isinstance(counts.get(k), int) or counts[k] < 0 for k in ("info", "low", "moderate", "high", "critical")):
        raise ValueError("pnpm audit response lacks valid vulnerability counts")
    if raw.get("metadata", {}).get("totalDependencies", 0) <= 0:
        raise ValueError("pnpm audit returned an empty dependency inventory")


def scan(args):
    root = pathlib.Path(args.root).resolve()
    inputs = input_hashes(root, args.module, args.kind)
    policy = read_json(root / "scripts/ci/security-exceptions.json")
    inventory = None
    if args.kind == "maven":
        inventory = jar_inventory(args.jar, args.maven_repo)
        findings, raw = osv_findings(inventory["components"])
    else:
        findings, raw = pnpm_audit(root / args.module, args.pnpm_command)
    blocking = apply_policy(findings, policy)
    evidence = {"schema": SCHEMA, "kind": args.kind, "module": args.module,
                "source_commit": args.source_commit, "scanned_at": dt.datetime.now(dt.timezone.utc).isoformat(),
                "inputs": inputs, "inventory": inventory, "findings": findings, "raw": raw,
                "status": "failed" if blocking else "passed", "blocking_count": len(blocking)}
    write_json(args.output, evidence)
    print(json.dumps({"module": args.module, "status": evidence["status"], "findings": len(findings), "blocking": len(blocking)}))
    return 1 if blocking else 0


def verify_report(root, module, kind, path, source_commit):
    value = read_json(path)
    if value.get("schema") != SCHEMA or value.get("kind") != kind or value.get("module") != module:
        raise ValueError("Security report schema/module mismatch")
    if value.get("source_commit") != source_commit or value.get("inputs") != input_hashes(root, module, kind):
        raise ValueError("Security report input/source mismatch")
    if value.get("status") != "passed" or value.get("blocking_count") != 0 or not isinstance(value.get("findings"), list):
        raise ValueError("Security report did not pass")
    if kind == "maven" and not value.get("inventory", {}).get("components"):
        raise ValueError("Security report has no component inventory")
    if not isinstance(value.get("raw"), dict):
        raise ValueError("Security report lacks scanner evidence")
    if kind == "maven":
        queries = value["raw"].get("queries", [])
        if [q.get("component") for q in queries] != value["inventory"]["components"]:
            raise ValueError("Security evidence is missing per-component OSV query results")
        if any(not isinstance(q.get("result"), dict) or q["result"].get("next_page_token") for q in queries):
            raise ValueError("Security evidence has incomplete OSV query results")
    else:
        validate_pnpm_raw(value["raw"])
    if apply_policy(value["findings"], read_json(root / "scripts/ci/security-exceptions.json")):
        raise ValueError("Security findings fail the current policy")
    return value


def bindings(root, specs, cache, source_commit):
    result = []
    for line in pathlib.Path(specs).read_text(encoding="utf-8").splitlines():
        kind, module = line.split("\t", 1)
        if kind not in ("maven", "pnpm"):
            raise ValueError("No mandatory security scanner defined for module type: " + kind)
        relative = "security/" + module.replace("/", "_") + ".json"
        path = pathlib.Path(cache) / relative
        verify_report(root, module, kind, path, source_commit)
        result.append({"module": module, "kind": kind, "path": relative, "sha256": digest(path.read_bytes()), "status": "passed"})
    if not result:
        raise ValueError("No security reports to bind")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    scan_parser = sub.add_parser("scan")
    scan_parser.add_argument("--kind", choices=("maven", "pnpm"), required=True)
    scan_parser.add_argument("--root", default=str(ROOT))
    scan_parser.add_argument("--module", required=True)
    scan_parser.add_argument("--source-commit", required=True)
    scan_parser.add_argument("--output", required=True)
    scan_parser.add_argument("--jar")
    scan_parser.add_argument("--maven-repo")
    scan_parser.add_argument("--pnpm-command", nargs="+", default=["pnpm"])
    binding_parser = sub.add_parser("bindings")
    binding_parser.add_argument("--root", default=str(ROOT))
    binding_parser.add_argument("--specs", required=True)
    binding_parser.add_argument("--cache", required=True)
    binding_parser.add_argument("--source-commit", required=True)
    verify = sub.add_parser("verify-jar")
    verify.add_argument("--root", default=str(ROOT))
    verify.add_argument("--module", required=True)
    verify.add_argument("--source-commit", required=True)
    verify.add_argument("--report", required=True)
    verify.add_argument("--jar", required=True)
    verify.add_argument("--maven-repo", required=True)
    args = parser.parse_args()
    try:
        if args.action == "scan":
            return scan(args)
        if args.action == "bindings":
            print(json.dumps(bindings(pathlib.Path(args.root).resolve(), args.specs, args.cache, args.source_commit)))
        else:
            report = verify_report(pathlib.Path(args.root).resolve(), args.module, "maven", args.report, args.source_commit)
            if jar_inventory(args.jar, args.maven_repo) != report["inventory"]:
                raise ValueError("Packaged runtime library bytes/coordinates differ from scanned resolve inventory")
            print("Offline packaged-JAR security inventory verified")
        return 0
    except (ValueError, OSError, KeyError, TypeError, zipfile.BadZipFile, urllib.error.URLError) as error:
        print("Dependency security check failed: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
