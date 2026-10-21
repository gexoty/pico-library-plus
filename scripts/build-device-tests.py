"""Build only the separate, debug-signed instrumentation APK. Does not connect to a device."""
from pathlib import Path
import subprocess,os,json,zipfile
ROOT=Path(__file__).resolve().parents[1]
sdk=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'AppData/Local/Android/Sdk')))
java=Path(os.environ.get('JAVA_HOME',r'C:\Program Files\Java\jdk-21'))/'bin/java.exe'
bt=sdk/'build-tools/36.0.0';android=sdk/'platforms/android-34/android.jar'
latest=json.loads((ROOT/'build/latest.json').read_text());run=(ROOT/latest['artifact']).parent
out=run/'instrumentation';out.mkdir(exist_ok=True);classes=out/'classes';classes.mkdir(exist_ok=True);dex=out/'dex';dex.mkdir(exist_ok=True)
manifest=out/'AndroidManifest.xml'
manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.gexoty.picolibrary.tests"><uses-sdk android:minSdkVersion="29" android:targetSdkVersion="29"/><application android:label="PicoLibrary device tests"/><instrumentation android:name="com.gexoty.picolibrary.tests.LauncherInstrumentation" android:targetPackage="com.gexoty.picolibrary"/></manifest>''')
def command(args):subprocess.run([str(a) for a in args],check=True)
command([java.with_name('javac.exe'),'--release','8','-encoding','UTF-8','-cp',os.pathsep.join(map(str,[android,run/'extensions.jar'])),'-d',classes,ROOT/'tests/LauncherInstrumentation.java'])
jar=out/'tests.jar'
with zipfile.ZipFile(jar,'w') as z:
    for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
command([java,'-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','29','--lib',android,'--classpath',run/'extensions.jar','--output',dex,jar])
base=out/'base.apk';command([bt/'aapt.exe','package','-f','-M',manifest,'-I',android,'-F',base])
with zipfile.ZipFile(base,'a') as z:z.write(dex/'classes.dex','classes.dex')
aligned=out/'aligned.apk';command([bt/'zipalign.exe','-f','4',base,aligned])
env=os.environ.copy();env['JAVA_HOME']=str(java.parent.parent)
subprocess.run([str(bt/'apksigner.bat'),'sign','--ks',str(Path.home()/'.android/debug.keystore'),'--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--out',str(out/'tests.apk'),str(aligned)],check=True,env=env)
print(out/'tests.apk')
