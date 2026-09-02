#!/bin/sh
set -eu
[ "$#" -eq 3 ] || { echo 'usage: production-persistence-preflight.sh CONTRACT UID GID' >&2; exit 64; }; contract=$1 uid=$2 gid=$3
case "$uid" in ''|*[!0-9]*) exit 64;; esac; case "$gid" in ''|*[!0-9]*) exit 64;; esac
[ "$uid" -gt 0 ] && [ "$gid" -gt 0 ] || { echo 'root runtime identity is forbidden' >&2; exit 64; }
for n in python3 realpath stat du find namei mktemp rm; do command -v "$n" >/dev/null || exit 1; done
paths_file=$(mktemp); cleanup(){ rm -f -- "$paths_file"; }; trap cleanup EXIT HUP INT TERM
python3 - "$contract" >"$paths_file" <<'PY'
import json,pathlib,sys
p=pathlib.Path(sys.argv[1]);
if not p.is_file() or p.is_symlink(): raise SystemExit('unsafe contract')
bindings=json.loads(p.read_text(encoding='utf-8')).get('persistent_bindings')
if not isinstance(bindings,list) or not bindings: raise SystemExit('persistent bindings are required')
sources=[x.get('source') for x in bindings if isinstance(x,dict)]
if len(sources)!=len(bindings) or len(sources)!=len(set(sources)): raise SystemExit('invalid persistent bindings')
for source in sources:
    if not isinstance(source,str) or not source.startswith('/srv/aienie-products/ai-novel/'): raise SystemExit('persistent binding escaped component root')
    print(source)
PY
[ -s "$paths_file" ] || exit 1
while IFS= read -r path; do
  [ "$(realpath -e -- "$path")" = "$path" ] && [ -d "$path" ] && [ ! -L "$path" ] || exit 1
  [ "$(stat -c '%u:%g' -- "$path")" = "$uid:$gid" ] || exit 1
  [ -z "$(find "$path" -xdev -type l -print -quit)" ] || exit 1
  namei -om -- "$path"; du -sb -- "$path"
done <"$paths_file"
