#!/usr/bin/env python3
import ipaddress,json,pathlib,re,sys
from urllib.parse import urlparse
c=pathlib.Path(sys.argv[1]); y=pathlib.Path(sys.argv[2]); expected=sys.argv[3]; paths=map(pathlib.Path,sys.argv[4:]); v=json.loads(c.read_text(encoding='utf-8')); compose=y.read_text(encoding='utf-8'); runtime=compose+'\n'+'\n'.join(p.read_text(encoding='utf-8') for p in paths)
if (v.get('schema_version'),v.get('canonical_component_id'),v.get('profile_id'),v.get('environment')) != ('aienie-product-production-contract-v1',expected,'prod-products-68','production'): raise SystemExit('production identity drifted')
o=urlparse(v.get('public_origin',''))
if o.scheme!='https' or not o.hostname or not o.hostname.endswith('.seekerhut.com'): raise SystemExit('invalid public origin')
if v.get('platform_injected_digest_fields')!=['source_commit','config_digest','artifact_digest','image_digests']: raise SystemExit('digest contract drifted')
if v.get('authorization')!={'minimum_release_manifest_version':4,'policy':'signed-v4-only','outer_signature_required':True}: raise SystemExit('production releases require a signed outer v4 manifest')
expected_overlay={
  'schema_version':'aienie-production-protected-config-overlay-contract-v1',
  'allowed_files':[{'target_path':'env.txt','file_mode':'0600','owner':'runtime_identity','consumers':['backend-runtime','production-migration-executor']}],
  'forbidden_exact_paths':['docker-compose.yml','application.yml','prompt.yml'],
  'forbidden_prefixes':['backend/','frontend/','release/'],
  'artifact_override_policy':'deny','unlisted_path_policy':'deny',
}
if v.get('protected_config_overlay_contract')!=expected_overlay: raise SystemExit('protected config overlay contract drifted')
expected_migration={
  'mode':'flyway-one-shot','location':'filesystem:/app/release/migrations/sql',
  'ledger':'release/migrations/flyway-ledger.json','latest_version':'14',
  'baseline_on_migrate':False,'validate_on_migrate':True,'clean_disabled':True,
  'execution':'one-shot-before-app','startup_behavior':'flyway-current-validation-only-after-preactivation',
  'executor':{'path':'release/production-migration-executor','cli':'precheck|execute|reconcile <component>',
              'stdin':'none','environment':'fixed-protected-candidate-only',
              'stdout':'compact-canonical-secret-free-json'},
  'artifact_manifest':'release/production-migration-artifacts.json','precheck':'strict-read-only',
  'restore_point':'consistent-jdbc-logical-checkpoint-before-execute',
  'authority':'signed-v4-platform-evidence-and-helper-invocation',
  'stdout_schema':'aienie-production-migration-executor-receipt-v1',
}
if v.get('migration')!=expected_migration: raise SystemExit('production migration contract drifted')
if 'SPRING_FLYWAY_ENABLED=false' not in runtime or 'SPRING_FLYWAY_ENABLED=true' in runtime:
    raise SystemExit('production startup must validate through the one-shot Flyway current guard')
for bad in ('.testhut.top','.aienie.com','.localhut.com','localhost','extra_hosts','host-gateway','/etc/aienie','env_file:','build:'):
    if bad in runtime: raise SystemExit(f'forbidden production value: {bad}')
for m in re.finditer(r'(?<![A-Za-z0-9])(?:\d{1,3}\.){3}\d{1,3}(?![A-Za-z0-9])',runtime):
    ip=ipaddress.ip_address(m.group(0))
    if not(ip.is_loopback or ip.is_unspecified): raise SystemExit(f'IP literal leaked: {ip}')
if 'container_name:' in compose: raise SystemExit('fixed production container name')
b=re.search(r'(?m)^networks:\s*\n((?:[ \t][^\n]*\n?)*)',compose)
if b and re.search(r'(?m)^\s+name:\s*',b.group(1)): raise SystemExit('fixed network name')
for x in v['listeners']:
    if x not in compose: raise SystemExit(f'missing listener: {x}')
for d in v['dependencies']:
    host,_,port=d['authority'].rpartition(':')
    if not d['tls_required'] or not host.endswith('.seekerhut.com') or host not in runtime or port not in runtime: raise SystemExit(f'invalid dependency: {d}')
for x in v['persistent_bindings']:
    if not x['source'].startswith(f'/srv/aienie-products/{expected}/') or x['source'] not in compose: raise SystemExit(f'invalid persistence: {x}')
expected_backup = {
    'schema_version': 'aienie-production-backup-contract-v1',
    'nightly': {
        'include': ['/srv/aienie-products/ai-novel/records'],
        'exclude': [
            {'source': '/srv/aienie-products/ai-novel/logs', 'classification': 'operational-log'}
        ],
    },
}
if v.get('backup') != expected_backup: raise SystemExit('production backup classification drifted')
purposes = {item['source']: item['purpose'] for item in v['persistent_bindings']}
classified = set(expected_backup['nightly']['include']) | {item['source'] for item in expected_backup['nightly']['exclude']}
if classified != set(purposes): raise SystemExit('backup policy must classify every persistent binding')
if purposes.get('/srv/aienie-products/ai-novel/logs') != 'operational-log': raise SystemExit('nightly-excluded logs must be operational-log')
if re.search(r'(?m)^\s*-\s+\./',compose) or 'create_host_path: false' not in compose: raise SystemExit('bind policy drifted')
for required in ('./backend/production-migration-entrypoint.sh','./release/migrations','/app/bin/production-migration-entrypoint.sh','/app/release/migrations'):
    if required not in compose: raise SystemExit(f'migration mount missing: {required}')
