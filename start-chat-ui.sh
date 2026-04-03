#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

JAR_PATH="$SCRIPT_DIR/target/advanced-chat-1.0-SNAPSHOT-jar-with-dependencies.jar"
PID_FILE="$SCRIPT_DIR/.chat-server.pid"
LOG_FILE="$SCRIPT_DIR/.chat-server.log"
PORT=5050
URL="http://localhost:$PORT"

pick_builder() {
  if command -v mvn >/dev/null 2>&1; then
    echo "mvn"
    return
  fi
  if command -v mvnd >/dev/null 2>&1; then
    echo "mvnd"
    return
  fi
  echo ""
}

find_port_pid() {
  if command -v lsof >/dev/null 2>&1; then
    lsof -ti tcp:"$PORT" -sTCP:LISTEN 2>/dev/null | head -n 1 || true
    return
  fi
  if command -v ss >/dev/null 2>&1; then
    ss -ltnp 2>/dev/null | awk '/:'"$PORT"' / {print $NF}' | sed -n 's/.*pid=\([0-9]\+\).*/\1/p' | head -n 1 || true
    return
  fi
  echo ""
}

stop_if_running() {
  local stopped=0

  if [[ -f "$PID_FILE" ]]; then
    local old_pid
    old_pid="$(cat "$PID_FILE" 2>/dev/null || true)"
    if [[ -n "${old_pid:-}" ]] && kill -0 "$old_pid" >/dev/null 2>&1; then
      echo "[Step 1/5] Stopping previous server from PID file: $old_pid"
      kill "$old_pid" >/dev/null 2>&1 || true
      sleep 1
      if kill -0 "$old_pid" >/dev/null 2>&1; then
        kill -9 "$old_pid" >/dev/null 2>&1 || true
      fi
      stopped=1
    fi
  fi

  local port_pid
  port_pid="$(find_port_pid)"
  if [[ -n "${port_pid:-}" ]] && kill -0 "$port_pid" >/dev/null 2>&1; then
    echo "[Step 1/5] Stopping process using port $PORT: PID $port_pid"
    kill "$port_pid" >/dev/null 2>&1 || true
    sleep 1
    if kill -0 "$port_pid" >/dev/null 2>&1; then
      kill -9 "$port_pid" >/dev/null 2>&1 || true
    fi
    stopped=1
  fi

  if [[ "$stopped" -eq 0 ]]; then
    echo "[Step 1/5] No previous server process found."
  fi
}

BUILDER="$(pick_builder)"
if [[ -z "$BUILDER" ]]; then
  echo "Neither mvn nor mvnd was found in PATH."
  echo "Install Maven and try again."
  exit 1
fi

stop_if_running

echo "[Step 2/5] Building shaded jar using $BUILDER..."
"$BUILDER" -q clean package

if [[ ! -f "$JAR_PATH" ]]; then
  echo "Build finished, but jar not found: $JAR_PATH"
  exit 1
fi

echo "[Step 3/5] Starting ChatServer in background..."
nohup java -jar "$JAR_PATH" >"$LOG_FILE" 2>&1 &
SERVER_PID=$!
echo "$SERVER_PID" > "$PID_FILE"

cleanup_on_fail() {
  if kill -0 "$SERVER_PID" >/dev/null 2>&1; then
    kill "$SERVER_PID" >/dev/null 2>&1 || true
  fi
}

trap cleanup_on_fail ERR

echo "[Step 4/5] Waiting for http://localhost:$PORT ..."
for _ in {1..60}; do
  if curl -sSf "http://localhost:$PORT" >/dev/null 2>&1; then
    echo "Server is up."
    break
  fi
  sleep 1
done

if ! curl -sSf "http://localhost:$PORT" >/dev/null 2>&1; then
  echo "Server did not start in time. Check logs: $LOG_FILE"
  exit 1
fi

echo "[Step 5/5] Opening browser..."
opened=0
if [[ -z "${DISPLAY:-}" && -z "${WAYLAND_DISPLAY:-}" ]]; then
  echo "No GUI display detected in this terminal session."
  echo "Please open manually: $URL"
elif command -v opera >/dev/null 2>&1; then
  if nohup opera --new-window "$URL" >/dev/null 2>&1 & then
    opened=1
    echo "Tried opening in Opera (new window)."
  elif nohup opera --new-tab "$URL" >/dev/null 2>&1 & then
    opened=1
    echo "Tried opening in Opera (new tab)."
  fi
elif command -v opera-stable >/dev/null 2>&1; then
  if nohup opera-stable --new-window "$URL" >/dev/null 2>&1 & then
    opened=1
    echo "Tried opening in Opera Stable (new window)."
  elif nohup opera-stable --new-tab "$URL" >/dev/null 2>&1 & then
    opened=1
    echo "Tried opening in Opera Stable (new tab)."
  fi
elif command -v xdg-open >/dev/null 2>&1; then
  if nohup xdg-open "$URL" >/dev/null 2>&1 & then
    opened=1
    echo "Tried opening with system default browser."
  fi
elif command -v sensible-browser >/dev/null 2>&1; then
  if nohup sensible-browser "$URL" >/dev/null 2>&1 & then
    opened=1
    echo "Tried opening with sensible-browser."
  fi
else
  echo "No browser launcher command found."
fi

if [[ "$opened" -eq 0 ]]; then
  echo "Auto-open may not have worked in this shell."
  echo "Open manually in Opera: opera \"$URL\""
  echo "Or paste this in browser: $URL"
fi

trap - ERR

echo "Done."
echo "Server PID: $SERVER_PID"
echo "To stop: kill \"$(cat "$PID_FILE")\""
echo "Logs: $LOG_FILE"
