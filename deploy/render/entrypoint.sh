#!/usr/bin/env bash
set -euo pipefail

RENDER_PORT="${PORT:-8080}"
echo "[render] Configuring Tomcat connector port: ${RENDER_PORT}"
sed -ri "s/port=\"[0-9]+\" protocol=\"HTTP\/1\.1\"/port=\"${RENDER_PORT}\" protocol=\"HTTP\/1.1\"/" /usr/local/tomcat/conf/server.xml

echo "[render] Starting Tomcat..."
exec catalina.sh run
