#!/usr/bin/env python3
"""AINovel staging entry invoked by the control plane's frozen deploy_script."""
import argparse
import json
import os
from pathlib import Path
import stat
import subprocess
import sys

import yaml

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "config-pair"))
from config_pair import ConfigError, parse_env
from shell_configuration import configuration


def regular_file(path, owner_only=False):
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode) or (owner_only and info.st_mode & 0o077):
        raise ConfigError("configuration requires regular files and owner-only secrets")
    return info


def validate_bundle(root, spec):
    for name, expected in ((".aienie-runtime-config-format", "application-yaml-env-v1"),
                           (".aienie-runtime-grpc-auth-format", "grpc-shared-key-v1")):
        path = root / name
        regular_file(path)
        if path.read_text(encoding="utf-8").strip() != expected:
            raise ConfigError("incompatible configuration artifact")
    groups = spec["configuration_groups"]
    application_mounts = {group["application_mount"] for group in groups}
    secret_mounts = {group["secrets_mount"] for group in groups}
    compose = yaml.safe_load((root / "docker-compose.yml").read_text(encoding="utf-8"))
    for service in compose["services"].values():
        sources = set()
        for mount in service.get("volumes", []):
            if isinstance(mount, dict):
                source, target, readonly = mount.get("source"), mount.get("target"), mount.get("read_only", False)
            else:
                parts = mount.split(":")
                if len(parts) < 2:
                    continue
                source, target, readonly = parts[0], parts[1], "ro" in parts[2:]
            if target in application_mounts | secret_mounts:
                if not source or not readonly:
                    raise ConfigError("configuration mount is missing or writable")
                regular_file(root / source)
                sources.add(target)
        if sources & secret_mounts and not sources & application_mounts:
            raise ConfigError("YAML mount is missing")
    for group in groups:
        if (root / group["retired_administrator_path"]).exists():
            raise ConfigError("configuration contains a retired administrator file")
        regular_file(root / group["application_path"])
        env = root / group["secrets_path"]
        regular_file(env, owner_only=True)
        configuration(root / group["source_root"], env)


def run(arguments, environment=None, capture=False):
    output = subprocess.check_output(arguments, env=environment, text=True)
    if output and not capture:
        print(output, end="", flush=True)
    return output


def deploy(candidate, live, version, component, environment, release_id):
    if component != "ai-novel" or environment != "staging":
        raise ValueError("this entry deploys AINovel staging")
    candidate, live = Path(candidate), Path(live)
    spec = json.loads((candidate / "release/deployment.json").read_text(encoding="utf-8"))
    spec = {**spec, **spec.get("environments", {}).get(environment, {})}
    if spec["layout"] != "live":
        raise ValueError("AINovel staging requires its live Compose layout")
    validate_bundle(candidate, spec)
    owner = (candidate / "env.txt").stat()
    env = {**os.environ, "AIENIE_RUNTIME_UID": str(owner.st_uid), "AIENIE_RUNTIME_GID": str(owner.st_gid)}
    directories = spec.get("persistent_dirs", [])
    excludes = ["--exclude=/.aienie-releases/", "--exclude=/backups/"]
    excludes += ["--exclude=/" + name.split("/")[0] + "/" for name in directories]
    if Path("/etc/aienie-storage-tiers.json").is_file():
        protected = run([sys.executable, "/opt/aienie-cold-storage/verify_storage_tiers.py",
                         "--manifest", "/etc/aienie-storage-tiers.json", "--rsync-excludes-for", str(live)], env, capture=True)
        excludes += ["--exclude=" + value for value in protected.splitlines() if value]
    live.mkdir(parents=True, exist_ok=True)
    run(["rsync", "-a", "--delete", "--checksum", *excludes, str(candidate) + "/", str(live) + "/"], env)
    for name in directories:
        directory = live / name
        directory.mkdir(parents=True, exist_ok=True)
        run(["chown", "-R", str(owner.st_uid) + ":" + str(owner.st_gid), "--", str(directory)], env)
    env.update(parse_env((live / "images.env").read_text(encoding="utf-8")))
    project = spec.get("project", component + "_" + environment)
    # This Compose template needs only immutable image references and UID/GID;
    # runtime application values and secrets remain in the mounted file pair.
    compose = ["docker", "compose", "--project-directory", str(live), "-p", project,
               "--env-file", str(live / "images.env"), "-f", str(live / "docker-compose.yml")]
    definition = json.loads(run([*compose, "config", "--format", "json"], env, capture=True))
    labels = {"com.aienie.component": component, "com.aienie.environment": environment,
              "com.aienie.release_id": release_id, "com.aienie.release_version": version}
    override = live / ".compose-release.json"
    override.write_text(json.dumps({"services": {name: {"labels": labels,
        "logging": {"driver": "json-file", "options": spec["logging"]}}
        for name in definition["services"]}}), encoding="utf-8")
    compose += ["-f", str(override)]
    run([*compose, "pull"], env)
    start = [*compose, "up", "-d", "--no-build"]
    if spec.get("force_recreate"):
        start += ["--force-recreate", "--remove-orphans"]
    if spec.get("wait_seconds"):
        start += ["--wait", "--wait-timeout", str(spec["wait_seconds"])]
    run(start, env)
    return {"status": "succeeded", "component": component, "release_id": release_id,
            "version": version, "project": project, "layout": spec["layout"]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("candidate", "live", "version", "component", "environment", "release_id"):
        parser.add_argument(name)
    args = parser.parse_args()
    print(json.dumps(deploy(**vars(args))))
