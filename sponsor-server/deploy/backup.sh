#!/bin/bash
set -euo pipefail
umask 077
install -d -m 700 /var/backups/lbry-sponsors
backup_file=/var/backups/lbry-sponsors/sponsors-$(date -u +%F).sqlite
sqlite3 /var/lib/lbry-sponsors/sponsors.sqlite ".backup '$backup_file'"
chmod 600 "$backup_file"
find /var/backups/lbry-sponsors -name 'sponsors-*.sqlite' -mtime +7 -delete
