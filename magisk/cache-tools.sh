#!/system/bin/sh
# Only the PackageParser entries for /system/priv-app/AppManager.
# Arguments allow host tests with synthetic directories; callers use fixed device paths.
invalidate_appmanager_cache() {
  cache_root=$1
  backup_root=$2
  [ ! -L "$cache_root" ] || return 1
  [ -d "$cache_root" ] || return 0
  cache_root=$(readlink -f "$cache_root") || return 1
  for bucket in "$cache_root"/*; do
    [ -d "$bucket" ] && [ ! -L "$bucket" ] || continue
    for entry in "$bucket"/AppManager-*; do
      [ -f "$entry" ] && [ ! -L "$entry" ] || continue
      flags=${entry##*/}
      flags=${flags#AppManager-}
      case "$flags" in ''|*[!0-9]*) continue ;; esac
      [ "$(readlink -f "$entry")" = "$entry" ] || return 1
      bucket_name=${bucket##*/}
      saved="$backup_root/$bucket_name/AppManager-$flags"
      [ ! -e "$saved" ] && [ ! -L "$saved" ] || return 1
      (umask 077; mkdir -p "$backup_root/$bucket_name") || return 1
      chmod 700 "$backup_root" "$backup_root/$bucket_name" || return 1
      cp -p "$entry" "$saved" || return 1
      cmp "$entry" "$saved" >/dev/null 2>&1 || return 1
      rm -f "$entry" || return 1
      [ ! -e "$entry" ] || return 1
      echo "Pico Library: backed up and invalidated AppManager-$flags"
    done
  done
}
