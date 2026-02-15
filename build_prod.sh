#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
INIT_MODE=false

if [[ "${1:-}" == "--init" ]]; then
  INIT_MODE=true
  shift
fi

if [[ $# -gt 0 ]]; then
  echo "Usage: $0 [--init]"
  exit 1
fi

SUDO_PASS="${SUDO_PASSWORD:-${SUDO_PASS:-}}"

run_sudo() {
  if [[ "$EUID" -eq 0 ]]; then
    "$@"
  else
    echo "$SUDO_PASS" | sudo -S -p "" "$@"
  fi
}

ensure_nginx_installed() {
  if ! command -v nginx >/dev/null 2>&1; then
    run_sudo env DEBIAN_FRONTEND=noninteractive apt-get update
    run_sudo env DEBIAN_FRONTEND=noninteractive apt-get install -y nginx
  fi
}

ensure_certbot_installed() {
  if ! command -v certbot >/dev/null 2>&1; then
    run_sudo env DEBIAN_FRONTEND=noninteractive apt-get update
    run_sudo env DEBIAN_FRONTEND=noninteractive apt-get install -y certbot python3-certbot-nginx
  fi
}

reload_nginx() {
  run_sudo nginx -t
  if command -v systemctl >/dev/null 2>&1; then
    if run_sudo systemctl is-active --quiet nginx; then
      run_sudo systemctl reload nginx
    else
      run_sudo systemctl start nginx
    fi
  elif command -v service >/dev/null 2>&1; then
    if run_sudo service nginx status >/dev/null 2>&1; then
      run_sudo service nginx reload
    else
      run_sudo service nginx start
    fi
  else
    if run_sudo pgrep nginx >/dev/null 2>&1; then
      run_sudo nginx -s reload
    else
      run_sudo nginx
    fi
  fi
}

install_prod_nginx_conf() {
  local source_conf="$ROOT_DIR/deploy/nginx/ainovel_prod.conf"
  local target_conf="/etc/nginx/conf.d/ainovel_prod.conf"

  if [[ ! -f "$source_conf" ]]; then
    echo "Missing nginx config: $source_conf"
    exit 1
  fi

  run_sudo install -m 0644 "$source_conf" "$target_conf"
  reload_nginx
}

bash "$ROOT_DIR/build.sh"

if [[ "$INIT_MODE" == "true" ]]; then
  ensure_nginx_installed
  install_prod_nginx_conf
  ensure_certbot_installed

  local_email="${LETSENCRYPT_EMAIL:-admin@ainovel.aienie.com}"
  run_sudo certbot --nginx -d ainovel.aienie.com --non-interactive --agree-tos --email "$local_email" --redirect
  reload_nginx
fi

echo "Production deployment finished. URL: https://ainovel.aienie.com"
