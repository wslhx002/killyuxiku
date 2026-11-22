#!/usr/bin/env bash
# Minecraft 26.3 server starter (requires Java 25)
cd "$(dirname "$0")" || exit 1
exec java -Xms1G -Xmx2G -XX:+UseG1GC -jar server.jar nogui
