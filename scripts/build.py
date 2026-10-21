"""Windows build: exact APK -> fresh decoded tree -> patches -> DEX extension -> unsigned APK.
No ADB, root, deployment, security-setting changes, or signing keys are used.
"""
from pathlib import Path
import argparse,datetime,hashlib,json,os,subprocess,sys,zipfile
from patch import transform
from release import version_info

ROOT=Path(__file__).resolve().parents[1]
EXPECTED='e330afb8a59878756163f7cd3a4588f61e7eb338874563b5eebb9bdd9ce1364f'
def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--java-home',default=os.environ.get('JAVA_HOME',r'C:\Program Files\Java\jdk-21'))
    parser.add_argument('--sdk',default=os.environ.get('ANDROID_HOME',str(Path.home()/'AppData/Local/Android/Sdk')))
    parser.add_argument('--apktool',default=os.environ.get('APKTOOL_JAR',str(ROOT/'.tools/apktool.jar')))
    parser.add_argument('--variant',choices=['standalone','system'],default='standalone')
    parser.add_argument('--version',default=(ROOT/'VERSION').read_text().strip())
    args=parser.parse_args();version,code=version_info(args.version)
    suffix='.exe' if os.name=='nt' else ''
    java=Path(args.java_home)/('bin/java'+suffix);javac=java.with_name('javac'+suffix)
    sdk=Path(args.sdk);bt=sdk/'build-tools/36.0.0';android=sdk/'platforms/android-34/android.jar'
    for p in [java,javac,Path(args.apktool),android,bt/'lib/d8.jar']:
        if not p.is_file():raise SystemExit('Missing prerequisite: '+str(p))
    apk=ROOT/'input/original.apk';assert hashlib.sha256(apk.read_bytes()).hexdigest()==EXPECTED,'Wrong base APK'
    run=ROOT/'build'/datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f');run.mkdir(parents=True)
    log=run/'build.log';env=os.environ.copy();env['JAVA_HOME']=args.java_home
    def command(cmd):
        print('Running:',Path(str(cmd[0])).name,flush=True)
        with log.open('a',encoding='utf-8') as f:
            f.write('\nCOMMAND '+json.dumps([str(x) for x in cmd],ensure_ascii=False)+'\n');f.flush()
            subprocess.run([str(x) for x in cmd],stdout=f,stderr=subprocess.STDOUT,env=env,check=True)
    decoded=run/'decoded'
    command([java,'-jar',args.apktool,'d',apk,'-p',run/'framework','-o',decoded])
    transform(decoded,args.variant,version,code)
    classes=run/'classes';classes.mkdir();dex=run/'dex';dex.mkdir()
    deps=[ROOT/'vendor/shizuku'/f'{a}.jar' for a in ['api','provider','aidl','shared']]
    # Artifacts are vendored and pinned; validate them before compilation.
    for row in json.loads((ROOT/'vendor/shizuku/manifest.json').read_text()):
        a=ROOT/'vendor/shizuku'/(row['artifact']+('.aar' if row['artifact'] in ['api','provider','shared'] else '.jar'))
        # Metadata records the actual packaging; use URL filename extension if it differs.
        a=ROOT/'vendor/shizuku'/(row['artifact']+'.'+row['url'].rsplit('.',1)[1])
        assert hashlib.sha256(a.read_bytes()).hexdigest()==row['sha256'],'Changed dependency: '+str(a)
        if a.suffix=='.aar':assert zipfile.ZipFile(a).read('classes.jar')==a.with_suffix('.jar').read_bytes()
    sources=list((ROOT/'src').rglob('*.java'))
    command([javac,'--release','8','-encoding','UTF-8','-classpath',os.pathsep.join(map(str,[android,*deps])),'-d',classes,*sources])
    extension=run/'extensions.jar'
    with zipfile.ZipFile(extension,'w') as z:
        for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes).as_posix())
    command([java,'-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','29','--lib',android,'--output',dex,extension,*deps])
    # Test target validation with the host JVM; no Android app is started.
    testclasses=run/'testclasses';testclasses.mkdir()
    command([javac,'--release','8','-d',testclasses,ROOT/'src/com/gexoty/picolibrary/StopPolicy.java',ROOT/'tests/StopPolicyTest.java'])
    command([java,'-cp',testclasses,'StopPolicyTest'])
    command([javac,'--release','8','-encoding','UTF-8','-d',testclasses,ROOT/'src/com/gexoty/picolibrary/CatalogRules.java',ROOT/'tests/CatalogRulesTest.java'])
    command([java,'-cp',testclasses,'CatalogRulesTest'])
    command([javac,'--release','8','-encoding','UTF-8','-d',testclasses,ROOT/'src/com/gexoty/picolibrary/StopPolicy.java',ROOT/'src/com/gexoty/picolibrary/RemovalPolicy.java',ROOT/'tests/RemovalPolicyTest.java'])
    command([java,'-cp',testclasses,'RemovalPolicyTest'])
    base=run/'base-unsigned.apk';command([java,'-jar',args.apktool,'b',decoded,'-p',run/'framework','-o',base])
    unsigned=run/'picolibrary-unsigned.apk'
    with zipfile.ZipFile(base) as source,zipfile.ZipFile(unsigned,'w') as target:
        assert 'classes2.dex' not in source.namelist(),'Unexpected multidex baseline'
        for info in source.infolist():target.writestr(info,source.read(info.filename))
        target.write(dex/'classes.dex','classes2.dex',compress_type=zipfile.ZIP_STORED)
    aligned=run/'picolibrary-aligned-unsigned.apk'
    command([bt/('zipalign'+suffix),'-P','16','-f','4',unsigned,aligned])
    command([bt/('aapt'+suffix),'dump','badging',aligned])
    command([sys.executable,ROOT/'scripts/verify.py',decoded,aligned,'com.pvr.appmanager' if args.variant=='system' else 'com.gexoty.picolibrary'])
    result={'artifact':str(aligned.relative_to(ROOT)),'sha256':hashlib.sha256(aligned.read_bytes()).hexdigest(),'signing':'unsigned','device_tested':False,'variant':args.variant,'version':version,'versionCode':code}
    (run/'result.json').write_text(json.dumps(result,indent=2));(ROOT/'build/latest.json').write_text(json.dumps(result,indent=2));(ROOT/('build/latest-'+args.variant+'.json')).write_text(json.dumps(result,indent=2))
    print(json.dumps(result,indent=2))
if __name__=='__main__':main()
