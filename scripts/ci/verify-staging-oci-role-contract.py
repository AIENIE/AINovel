#!/usr/bin/env python3
"""Validate the repository-owned staging OCI role closure without rendering secrets."""

from __future__ import annotations

import json
import pathlib
import re
import sys


def fail(message: str) -> None:
    raise SystemExit(f"staging OCI role contract validation failed: {message}")


def reject_duplicates(pairs: list[tuple[str, object]]) -> dict[str, object]:
    value: dict[str, object] = {}
    for key, item in pairs:
        if key in value:
            fail(f"duplicate JSON key: {key}")
        value[key] = item
    return value


if len(sys.argv) != 4:
    fail("usage: verify-staging-oci-role-contract.py <contract> <compose> <component-id>")

contract_path = pathlib.Path(sys.argv[1])
compose_path = pathlib.Path(sys.argv[2])
component_id = sys.argv[3]
repository_root = pathlib.Path(__file__).resolve().parent.parent.parent
for path in (contract_path, compose_path):
    if not path.is_file() or path.is_symlink():
        fail(f"input is missing or unsafe: {path}")

try:
    contract = json.loads(
        contract_path.read_text(encoding="utf-8", errors="strict"),
        object_pairs_hook=reject_duplicates,
    )
    compose = compose_path.read_text(encoding="utf-8", errors="strict")
except (UnicodeError, json.JSONDecodeError) as error:
    fail(str(error))

if set(contract) != {"schema_version", "component_id", "oci_repository", "roles"}:
    fail("top-level fields are not closed")
if contract["schema_version"] != "aienie-staging-oci-role-contract-v1":
    fail("schema version is not approved")
if contract["component_id"] != component_id:
    fail("component identity drifted")
repository = f"ghcr.io/aienie/{component_id}"
if contract["oci_repository"] != repository:
    fail("component OCI repository is not canonical")

roles = contract["roles"]
if not isinstance(roles, list) or not roles:
    fail("roles must be a non-empty array")
role_names: set[str] = set()
service_names: set[str] = set()
environment_variables: set[str] = set()
expected_service_images: dict[str, str] = {}
for role in roles:
    if not isinstance(role, dict) or set(role) != {
        "role", "compose_service", "environment_variable", "oci_repository", "build"
    }:
        fail("role fields are not closed")
    name = role["role"]
    service = role["compose_service"]
    variable = role["environment_variable"]
    if not isinstance(name, str) or re.fullmatch(r"[a-z][a-z0-9-]*", name) is None:
        fail("role name is invalid")
    if not isinstance(service, str) or re.fullmatch(r"[a-z][a-z0-9-]*", service) is None:
        fail("Compose service name is invalid")
    if not isinstance(variable, str) or re.fullmatch(r"[A-Z][A-Z0-9_]*_IMAGE", variable) is None:
        fail("image environment variable is invalid")
    if role["oci_repository"] != repository:
        fail("every role must use the component OCI repository")
    build = role["build"]
    if not isinstance(build, dict) or set(build) != {
        "source", "context", "dockerfile", "target", "build_args"
    }:
        fail("role build fields are not closed")
    if build["source"] != "pinned-source-tree" or build["build_args"] != {}:
        fail("role builds must use the pinned source tree without ambient build args")
    for field, expected_type in (("context", "directory"), ("dockerfile", "file")):
        value = build[field]
        pure = pathlib.PurePosixPath(value) if isinstance(value, str) else None
        if (pure is None or pure.is_absolute() or ".." in pure.parts
                or pure.as_posix() != value or "$" in value or "\\" in value):
            fail(f"role build {field} is not a canonical repository-relative path")
        resolved = repository_root.joinpath(*pure.parts)
        if expected_type == "directory" and (not resolved.is_dir() or resolved.is_symlink()):
            fail("role build context is missing or unsafe")
        if expected_type == "file" and (not resolved.is_file() or resolved.is_symlink()):
            fail("role Dockerfile is missing or unsafe")
    target = build["target"]
    if target is not None and (not isinstance(target, str)
            or re.fullmatch(r"[a-zA-Z0-9][a-zA-Z0-9_.-]*", target) is None):
        fail("role build target is invalid")
    if name in role_names or service in service_names or variable in environment_variables:
        fail("role, service, and image variable identities must be unique")
    role_names.add(name)
    service_names.add(service)
    environment_variables.add(variable)
    expected_service_images[service] = variable

if "env_file:" in compose:
    fail("Compose env_file is forbidden")
if re.search(r"(?m)^\s*-\s+[^#\n]+:[^:\n]+:ro\s*$", compose):
    fail("protected runtime files must use long bind syntax")
for target in re.findall(r"(?m)^\s+target: ((?:/app|/run/secrets)/\S+)$", compose):
    block = re.compile(
        rf"(?m)^\s*- type: bind\n\s+source: [^\n]+\n\s+target: {re.escape(target)}\n"
        r"\s+read_only: true\n\s+bind:\n\s+create_host_path: false$"
    )
    if block.search(compose) is None:
        fail(f"protected runtime file may be auto-created: {target}")
if re.search(r"\$\{[^}]*(?:PORT|HOST|ROOT)[^}]*\}", compose):
    fail("host port or bind source may not use ambient interpolation")
if re.search(r"(?m)^\s+source:\s+['\"]?\$\{", compose):
    fail("long-form bind source may not use ambient interpolation")
if re.search(r"(?m)^\s+-\s+['\"]?\$\{[^}]+\}[^\n]*:", compose):
    fail("short-form bind source or port may not use ambient interpolation")

actual_service_images: dict[str, str] = {}
current_service: str | None = None
in_services = False
for line in compose.splitlines():
    if line == "services:":
        in_services = True
        current_service = None
        continue
    if not in_services:
        continue
    if line and not line.startswith(" "):
        break
    service_match = re.fullmatch(r"  ([a-z][a-z0-9-]*):", line)
    if service_match:
        current_service = service_match.group(1)
        continue
    image_match = re.fullmatch(
        r'    image: "\$\{([A-Z][A-Z0-9_]*_IMAGE):\?[^\"]+\}"',
        line,
    )
    if image_match:
        if current_service is None or current_service in actual_service_images:
            fail("image declaration is outside a unique Compose service")
        actual_service_images[current_service] = image_match.group(1)

if actual_service_images != expected_service_images:
    fail("Compose role-to-image closure does not match the tracked contract")

print(f"Validated staging OCI role closure for {component_id}: {len(roles)} role(s)")
