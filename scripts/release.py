"""Host release pipeline. Signing credentials are read only from environment."""
from pathlib import Path
import argparse, hashlib, json, os, re, shutil, subprocess, sys, zipfile
ROOT=Path(__file__).resolve().parents[1]
PACKAGES={'standalone':'com.gexoty.picolibrary','system':'com.pvr.appmanager'}
APK_PATH='system/priv-app/AppManager/AppManager.apk'
def version_info(value):
    m=re.fullmatch(r'v?(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)',value)
    if not m: raise ValueError('Expected vMAJOR.MINOR.PATCH')
    a,b,c=map(int,m.groups())
    if b>999 or c>999: raise ValueError('Version limits: minor/patch <=999')
    code=200905100+a*1000000+b*1000+c
    if code>2100000000: raise ValueError('Android versionCode limit exceeded')
    if a==b==c==0: raise ValueError('versionCode must be positive')
    return f'{a}.{b}.{c}',code

def run(args, **kw):
    return subprocess.check_output(list(map(str,args)),text=True,stderr=subprocess.STDOUT,**kw)
def tool(bt,name):
    return bt/(name+('.exe' if os.name=='nt' else ''))
def signer(java,bt,*args):
    return run([java,'-jar',bt/'lib/apksigner.jar',*args])
def inspect(apk,package,version,code,bt,java):
    if not apk.is_file() or apk.stat().st_size==0: raise ValueError('Empty APK')
    badging=run([tool(bt,'aapt'),'dump','badging',apk])
    assert f"package: name='{package}'" in badging
    assert f"versionCode='{code}'" in badging and f"versionName='{version}'" in badging
    assert "sdkVersion:'29'" in badging and "targetSdkVersion:'29'" in badging
    signer(java,bt,'verify','--verbose','--print-certs',apk)
    run([tool(bt,'zipalign'),'-c','-P','16','4',apk])
    return run([tool(bt,'aapt'),'dump','xmltree',apk,'AndroidManifest.xml'])
def components(xml):
    # Compare every component subtree, including filters, metadata and permissions.
    lines=xml.splitlines(); result=[]; active=False; indent=0
    for line in lines:
        n=len(line)-len(line.lstrip())
        if active and n<=indent: active=False
        if re.match(r'\s*E: (activity|activity-alias|service|receiver|provider)\b',line):
            active=True;indent=n
        if active:
            result.append(line.replace('com.gexoty.picolibrary.shizuku','APPLICATION.shizuku').replace('com.gexoty.picolibrary.permission.','APPLICATION.permission.').replace('com.pvr.appmanager.permission.','APPLICATION.permission.').replace('com.gexoty.picolibrary.OPEN_LIBRARY','APPLICATION.OPEN_LIBRARY').replace('com.pvr.appmanager.OPEN_LIBRARY','APPLICATION.OPEN_LIBRARY'))
    # Other authorities differ by application ID; normalize only authority attribute lines.
    return [re.sub(r'com\.(?:gexoty\.picolibrary|pvr\.appmanager)(?=\.)','APPLICATION',x) if 'android:authorities' in x else x for x in result]
def module_bytes(system,version,code):
    allowed={'customize.sh','LICENSE','META-INF/com/google/android/update-binary','META-INF/com/google/android/updater-script'}
    actual={p.relative_to(ROOT/'magisk').as_posix() for p in (ROOT/'magisk').rglob('*') if p.is_file()}
    assert actual == allowed | {'UPSTREAM.md'}, 'Unexpected Magisk source files'
    files={name:(ROOT/'magisk'/name).read_bytes() for name in allowed}
    files['module.prop']=f'id=picolibrary\nname=Pico Library\nversion=v{version}\nversionCode={code}\nauthor=Gexoty\ndescription=Systemless Pico 4 AppManager replacement\n'.encode()
    files[APK_PATH]=system.read_bytes();files['system/priv-app/AppManager/.replace']=b''
    return files
def pack(system,dest,version,code):
    files=module_bytes(system,version,code)
    with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED) as z:
        for name,data in sorted(files.items()):
            info=zipfile.ZipInfo(name,(2020,1,1,0,0,0));info.external_attr=(0o100755 if name.endswith('update-binary') else 0o100644)<<16
            z.writestr(info,data,compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(dest) as z:
        assert z.testzip() is None and len(z.namelist())==len(files) and set(z.namelist())==set(files)
        for name,data in files.items(): assert z.read(name)==data
    return hashlib.sha256(system.read_bytes()).hexdigest()
def main():
    p=argparse.ArgumentParser();p.add_argument('--version',default=(ROOT/'VERSION').read_text().strip());p.add_argument('--apktool',required=True)
    p.add_argument('--java-home',default=os.environ.get('JAVA_HOME',r'C:\Program Files\Java\jdk-21'))
    p.add_argument('--sdk',default=os.environ.get('ANDROID_HOME',str(Path.home()/'AppData/Local/Android/Sdk')))
    p.add_argument('--keystore');p.add_argument('--output',default='dist');args=p.parse_args()
    version,code=version_info(args.version);bt=Path(args.sdk)/'build-tools/36.0.0';java=Path(args.java_home)/('bin/java.exe' if os.name=='nt' else 'bin/java')
    out=ROOT/args.output;out.mkdir(parents=True,exist_ok=True)
    if any(out.iterdir()): raise ValueError('Output directory must be empty')
    # Missing release credentials must fail before any release artifact is produced.
    if args.keystore:
        for key in ['ANDROID_KEYSTORE_PASSWORD','ANDROID_KEY_ALIAS','ANDROID_KEY_PASSWORD']:
            if not os.environ.get(key): raise ValueError('Missing signing environment: '+key)
        keystore=Path(args.keystore);alias=os.environ['ANDROID_KEY_ALIAS']
    else:
        # Standard Android debug key; shared by both variants, kept outside the repository.
        keystore=Path.home()/'.android/debug.keystore';alias='androiddebugkey'
        os.environ['ANDROID_KEYSTORE_PASSWORD']=os.environ['ANDROID_KEY_PASSWORD']='android'
        if not keystore.exists():
            keystore.parent.mkdir(parents=True,exist_ok=True)
            run([java.with_name('keytool.exe' if os.name=='nt' else 'keytool'),'-genkeypair','-keystore',keystore,'-alias',alias,'-storepass:env','ANDROID_KEYSTORE_PASSWORD','-keypass:env','ANDROID_KEY_PASSWORD','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Android Debug,O=Android,C=US'])
    manifests={};apks={}
    for variant,package in PACKAGES.items():
        subprocess.run([sys.executable,ROOT/'scripts/build.py','--variant',variant,'--version',version,'--java-home',args.java_home,'--sdk',args.sdk,'--apktool',args.apktool],check=True)
        result=json.loads((ROOT/f'build/latest-{variant}.json').read_text());src=ROOT/result['artifact']
        dest=out/(f'PicoLibrary-v{version}.apk' if variant=='standalone' else f'PicoLibrary-System-v{version}.apk')
        signer(java,bt,'sign','--ks',keystore,'--ks-key-alias',alias,'--ks-pass','env:ANDROID_KEYSTORE_PASSWORD','--key-pass','env:ANDROID_KEY_PASSWORD','--out',dest,src)
        manifests[variant]=inspect(dest,package,version,code,bt,java);apks[variant]=dest
    assert components(manifests['standalone'])==components(manifests['system']), 'Manifest component regression between variants'
    certificates=[re.search(r'Signer #1 certificate SHA-256 digest: (\w+)',signer(java,bt,'verify','--print-certs',a)).group(1) for a in apks.values()]
    assert certificates[0]==certificates[1]
    archive=out/f'PicoLibrary-Magisk-v{version}.zip';pack(apks['system'],archive,version,code)
    (out/'SHA256SUMS.txt').write_text(''.join(f'{hashlib.sha256(a.read_bytes()).hexdigest()}  {a.name}\n' for a in [apks['standalone'],archive]),encoding='utf-8')
    (out/'verification.json').write_text(json.dumps({'version':version,'versionCode':code,'signing':'developer' if args.keystore else 'android-debug','certificate_sha256':certificates[0],'device_tested':False,'packages':PACKAGES,'magisk':'verified','components':'equal'},indent=2))
    print('Both signed APKs and Magisk ZIP verified:',out)
if __name__=='__main__': main()
