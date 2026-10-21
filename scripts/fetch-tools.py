from pathlib import Path
import hashlib,json,urllib.request
root=Path(__file__).resolve().parents[1]
lock=json.loads((root/'scripts/tools.lock.json').read_text())['apktool']
data=urllib.request.urlopen(lock['url'],timeout=120).read()
assert hashlib.sha256(data).hexdigest()==lock['sha256'],'Apktool digest mismatch'
out=root/'.tools/apktool.jar';out.parent.mkdir(exist_ok=True);out.write_bytes(data)
print('Pinned Apktool downloaded and verified')
