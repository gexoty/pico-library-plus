import sys,unittest,tempfile,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
from release import version_info,pack,APK_PATH
class ReleaseTests(unittest.TestCase):
    def test_numeric_order(self):
        versions=['1.0.9','1.0.10','1.9.999','1.10.0','2.0.0']
        codes=[version_info(v)[1] for v in versions]
        self.assertEqual(codes,sorted(set(codes)))
    def test_invalid(self):
        for value in ['01.2.3','1.1000.0','0.0.0','v1.2.3-beta','1.2','2100.0.0','1.2.3;echo']:
            with self.assertRaises(ValueError):version_info(value)
    def test_magisk_archive(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp);apk=p/'system.apk';apk.write_bytes(b'test payload')
            pack(apk,p/'module.zip','1.2.3',1002003)
            with zipfile.ZipFile(p/'module.zip') as z:
                self.assertEqual(z.read(APK_PATH),apk.read_bytes())
                self.assertIn('system/priv-app/AppManager/.replace',z.namelist())
                self.assertIn(b'versionCode=1002003',z.read('module.prop'))
                self.assertNotIn('service.sh',z.namelist())
if __name__=='__main__':unittest.main()
