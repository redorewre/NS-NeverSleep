#!/bin/sh
# Запрашивает токен без отображения на экране и запускает upload.sh.
printf 'Token: '
stty -echo 2>/dev/null || true
IFS= read -r TOKEN
stty echo 2>/dev/null || true
printf '\n'
if [ -z "$TOKEN" ]; then
  echo "Токен не введён" >&2
  exit 1
fi
GH_TOKEN="$TOKEN" sh "$(dirname "$0")/upload.sh"
