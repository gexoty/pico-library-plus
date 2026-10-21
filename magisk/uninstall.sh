#!/system/bin/sh
MODDIR=${0%/*}
. "$MODDIR/cache-tools.sh"
backup="/data/adb/picolibrary-backups/uninstall-$(date +%Y%m%d-%H%M%S)-$$"
if ! invalidate_appmanager_cache /data/system/package_cache "$backup"; then
  echo "Pico Library: cache backup failed; original cache retained where backup failed. Review before reboot."
  exit 1
fi
