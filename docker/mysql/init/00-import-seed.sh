#!/bin/bash
set -euo pipefail

seed_dir="$(mktemp -d)"
trap 'rm -rf "$seed_dir"' EXIT

unzip -q /seed/db.zip -d "$seed_dir"

mysql_cmd=(mysql --protocol=socket -uroot --max_allowed_packet=128M)
if [ -n "${MYSQL_ROOT_PASSWORD:-}" ]; then
  mysql_cmd+=(-p"${MYSQL_ROOT_PASSWORD}")
fi

for dump in hackwars.sql hackerforum.sql hackwars_drupal.sql chat.sql; do
  echo "Importing ${dump}..."
  normalized_dump="${seed_dir}/${dump}.normalized"
  sed 's/ ROW_FORMAT=FIXED//g' "${seed_dir}/${dump}" > "${normalized_dump}"
  "${mysql_cmd[@]}" < "${normalized_dump}"
done
