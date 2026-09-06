#!/usr/bin/env bash
# 只安装、不启动（等价于：bash deploy/start.sh --install-only）
exec bash "$(dirname "$0")/start.sh" --install-only "$@"
