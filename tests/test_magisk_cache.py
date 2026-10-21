import os, shutil, subprocess, tempfile, unittest
from pathlib import Path

HELPER=Path(__file__).resolve().parents[1]/'magisk/cache-tools.sh'
SHELL=shutil.which('sh') or ('C:/Program Files/Git/bin/bash.exe' if os.name=='nt' else None)

@unittest.skipUnless(SHELL, 'POSIX shell unavailable')
class MagiskCacheTests(unittest.TestCase):
    def invoke(self, root):
        return subprocess.run([SHELL,'-c','. "$1"; invalidate_appmanager_cache "$PWD/cache" "$PWD/backups"','test',HELPER.as_posix()],cwd=root,capture_output=True)

    def test_only_exact_appmanager_cache_and_verified_backup(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            for bucket in ['a','b']:
                p=root/'cache'/bucket;p.mkdir(parents=True)
                for name in ['AppManager-16','AppManager-0','AppManager-16x','AppManagerOther-16','com.example-0']:
                    (p/name).write_bytes((bucket+name).encode())
            result=self.invoke(root)
            self.assertEqual(result.returncode,0,result.stderr)
            for bucket in ['a','b']:
                for name in ['AppManager-16','AppManager-0']:
                    self.assertFalse((root/'cache'/bucket/name).exists())
                    self.assertEqual((root/'backups'/bucket/name).read_bytes(),(bucket+name).encode())
                for name in ['AppManager-16x','AppManagerOther-16','com.example-0']:
                    self.assertTrue((root/'cache'/bucket/name).exists())

    def test_backup_failure_keeps_cache(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);p=root/'cache/a';p.mkdir(parents=True)
            entry=p/'AppManager-16';entry.write_bytes(b'original')
            (root/'backups').write_bytes(b'cannot-create-directory')
            self.assertNotEqual(self.invoke(root).returncode,0)
            self.assertEqual(entry.read_bytes(),b'original')

    def test_existing_backup_not_overwritten(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);(root/'cache/a').mkdir(parents=True);(root/'backups/a').mkdir(parents=True)
            (root/'cache/a/AppManager-16').write_bytes(b'current')
            (root/'backups/a/AppManager-16').write_bytes(b'previous')
            self.assertNotEqual(self.invoke(root).returncode,0)
            self.assertEqual((root/'cache/a/AppManager-16').read_bytes(),b'current')
            self.assertEqual((root/'backups/a/AppManager-16').read_bytes(),b'previous')

    def test_absent_cache_is_noop(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(self.invoke(tmp).returncode,0)
            self.assertFalse((Path(tmp)/'backups').exists())

    @unittest.skipIf(os.name=='nt','Native symbolic link test runs on Linux CI')
    def test_symlink_bucket_is_untouched(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);(root/'cache').mkdir();(root/'outside').mkdir()
            (root/'outside/AppManager-16').write_bytes(b'outside')
            (root/'cache/link').symlink_to(root/'outside',target_is_directory=True)
            self.assertEqual(self.invoke(root).returncode,0)
            self.assertEqual((root/'outside/AppManager-16').read_bytes(),b'outside')
