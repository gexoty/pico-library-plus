from pathlib import Path
import sys,xml.etree.ElementTree as ET,zipfile
decoded=Path(sys.argv[1]);apk=Path(sys.argv[2]);ns='{http://schemas.android.com/apk/res/android}'
package=sys.argv[3] if len(sys.argv)>3 else 'com.gexoty.picolibrary'
root=ET.parse(decoded/'AndroidManifest.xml').getroot();assert root.get('package')==package
assert ns+'sharedUserId' not in root.attrib
authorities=[p.get(ns+'authorities') for p in root.find('application').findall('provider')]
assert len(set(authorities))==len(authorities) and all(a.startswith(package+'.') for a in authorities)
assert package+'.shizuku' in authorities
for p in (decoded/'smali').rglob('*.smali'):
    assert package=='com.pvr.appmanager' or 'content://com.pvr.appmanager.' not in p.read_text(encoding='utf-8'),'Old internal URI: '+str(p)
permissions=[p.get(ns+'name') for p in root.findall('uses-permission')];assert 'android.permission.FORCE_STOP_PACKAGES' in permissions
tracker=(decoded/'smali/os/teatracker/m.smali').read_text(encoding='utf-8')
assert '->bindTracker(Landroid/content/Context;Landroid/content/Intent;Landroid/content/ServiceConnection;I)Z' in tracker
assert 'Landroid/content/Context;->bindService(' not in tracker,'Unguarded TeaTracker bind'
activity=(decoded/'smali/com/pvr/appmanager/AllAppActivity.smali').read_text(encoding='utf-8')
assert '->initAdapter()V\n\n    invoke-static {p0}, Lcom/gexoty/picolibrary/LibraryExtensions;->attach(Landroid/app/Activity;)V' in activity,'Attach must precede reuse of p0'
assert root.find("application/activity[@"+ns+"name='com.gexoty.picolibrary.LauncherSettingsActivity']") is not None
assert (decoded/'res/layout/gexoty_button.xml').is_file()
assert (decoded/'res/layout/gexoty_settings_button.xml').is_file()
assert '->openPermissions(' in (decoded/'smali/com/pvr/appmanager/utils/Utils.smali').read_text(encoding='utf-8')
assert '->selectedCatalog()' in (decoded/'smali/com/pvr/appmanager/d0/a.smali').read_text(encoding='utf-8')
assert 'IconMask;->begin(' in (decoded/'smali/androidx/constraintlayout/utils/widget/ImageFilterView.smali').read_text(encoding='utf-8')
with zipfile.ZipFile(apk) as z:
    assert z.read('classes2.dex').startswith(b'dex\n')
    assert b'com/gexoty/picolibrary/LibraryExtensions' in z.read('classes2.dex')
    assert b'rikka/shizuku/ShizukuProvider' in z.read('classes2.dex')
    assert b'com/gexoty/picolibrary/StopUserService' in z.read('classes2.dex')
    assert 'classes.dex' in z.namelist()
print('Manifest, authority isolation, permission and extension DEX checks: passed')
