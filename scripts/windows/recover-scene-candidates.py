"""Replay only the approved local incident into candidate storage, never into manuscript text.

Build the backend and dependency classpath first; see doc/api/manuscript.md.
Credentials stay in the standard private configuration pair and the child environment.
"""
from pathlib import Path
import os
import subprocess
import sys

root = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(root / "scripts/config-pair"))
from read_configuration import read


def main():
    runtime = Path(os.environ.get("AIENIE_RUNTIME_ROOT", root.parent.parent / "aienie-runtime"))
    private = runtime / "private/app-secrets/ainovel.env"
    values = read(root, "local", str(private) + ".application.yml")
    for line in private.read_text(encoding="utf-8-sig").splitlines():
        if line.strip() and not line.lstrip().startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip('"').strip("'")
    env = os.environ.copy()
    for key in ("DB_URL", "DB_USERNAME", "DB_PASSWORD"):
        env[key] = values[key]
    classpath = str(root / "backend/target/classes") + os.pathsep + (
        root / "backend/target/scene-classpath.txt").read_text(encoding="utf-8").strip()
    return subprocess.run(["java", "-cp", classpath,
                           "com.ainovel.app.manuscript.SceneCandidateRecovery",
                           "--apply-local-incident"], env=env, cwd=root).returncode


if __name__ == "__main__":
    raise SystemExit(main())
