"""One-shot SHA-pinned source transfer. No world installer or production data access."""
import base64,hashlib,io,json,pathlib,subprocess,zipfile
ROOT=pathlib.Path.cwd();INPUT=ROOT/'.integration/public27';OUT=ROOT/'build/public27';OUT.mkdir(parents=True,exist_ok=True)
BASE='a06288c8edc5a3704d84ebc826ffb18495392718'
PACKET='6c08a9805d640f7cddfb0f90c88ee7b2eb7b74e9f4306bf5e54efda4e0fbd546'
SHAS=['8ae307496e351c754edda9e23ae562f3370f8ec0','7412f63f6bb169ea087577bb5728f0b0f2366f38','26deb2084bc8f7213cf9f82cd133fd6960df4147','386296346826d1d75da550232bc8cfd35ecf2946','c5a675fa46b8a621ec0a4116dc6cec10855727d1','e9898210e0b3ec4db51a6473e99675eab3b575c0','2d1de37f0e24c4d3384548c155d99ed182dc384f','4021172294bbb8eced41ca48221aafcf4ed1f9ac','1be9ad8df8573cbc3f228569ae2ee8b5fe04167b','b6328a230cecd4c2b5f19075d15061b0a0710bad','1f4898716773fdbd4e2b36464c716375d97c188b','5f57f59c4b7550a263772b4a71fd2a43f2315292','9deeeaa582bc4d18b5a0b95e88ee91ec34354e35','77a2bd8ecac227129e25fa75c0f8a9ca1273aaae','4c96587a39f962d63dd5411f52c6c7d06f693b59','5aff6f6e74383e17e14a154732af0c362baca32a','1c6df0f8f9a8559cebc1cc61381e4aa5c9fda608','1ad4ebb670d534af8e6b338bcb077a9acef4b83c']
# Bounded transport transcription corrections; canonical pieces AND full archive remain pinned.
CORRECTIONS={6:[('dW6Y6Ytj','dW6Ytj')],8:[('XI4KEbHidd','XI4KEHidd')]}
def sha(data):return hashlib.sha256(data).hexdigest()
def safe(name):
    p=pathlib.PurePosixPath(name)
    assert not p.is_absolute() and '..' not in p.parts and p.parts and '\\' not in name,name
    return ROOT.joinpath(*p.parts)
subprocess.run(['git','merge-base','--is-ancestor',BASE,'HEAD'],check=True)
parts=[]
for i,expected in enumerate(SHAS):
    text=(INPUT/f'p{i:02}.txt').read_text()
    for old,new in CORRECTIONS.get(i,[]):
        if old in text:assert text.count(old)==1;text=text.replace(old,new)
    b=text.encode();assert hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()==expected,f'Piece {i}'
    parts.append(b)
raw=base64.b64decode(b''.join(parts),validate=True)
assert len(raw)==26131 and sha(raw)==PACKET,'Archive SHA mismatch'
z=zipfile.ZipFile(io.BytesIO(raw));assert z.testzip() is None
assert len(z.namelist())==len(set(z.namelist()))
m=json.loads(z.read('manifest.json'));assert m['base']==BASE
files=m['files'];existing={'build.gradle','src/main/java/com/quin/catancraft/world/ProducerService.java','src/main/java/com/quin/catancraft/world/ProducerTransaction.java'}
new={'docs/R5_PUBLIC_INTEGRATION.md','tests/r5_public_asset_audit.json','src/main/resources/data/catancraft/r5_public/contract.b64'}
new.update('src/main/java/com/quin/catancraft/world/'+n+'.java' for n in ['ProducerOccupancyTests','StreetscapeJournal','StreetscapeR5','StreetscapeService','StreetscapeTests','StreetscapeTransaction'])
assert set(files)==existing|new
assert set(z.namelist())=={'manifest.json','changes.patch'}|{'files/'+n for n in new}
for name,meta in files.items():
    p=safe(name)
    if meta['before'] is None:assert not p.exists(),name
    else:assert p.is_file() and not p.is_symlink() and sha(p.read_bytes())==meta['before'],name
patch=OUT/'changes.patch';patch.write_bytes(z.read('changes.patch'))
subprocess.run(['git','apply','--check',str(patch)],check=True);subprocess.run(['git','apply',str(patch)],check=True)
for name in new:
    p=safe(name);p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read('files/'+name))
for name,meta in files.items():assert sha(safe(name).read_bytes())==meta['after'],name
receipts=[]
# Explicit hash-checked corrections only; cannot add arbitrary files or execute supplied scripts.
for fix in sorted(INPUT.glob('fix-*.json')):
    for edit in json.loads(fix.read_text()):
        name=edit['path'];assert name in files
        p=safe(name);data=p.read_bytes();assert sha(data)==edit['before'],name
        old,new_text=edit['old'].encode(),edit['new'].encode();assert old and data.count(old)==1,name
        data=data.replace(old,new_text);assert sha(data)==edit['after'],name
        p.write_bytes(data);files[name]['after']=sha(data)
    receipts.append({'name':fix.name,'sha256':sha(fix.read_bytes())})
assert set(subprocess.check_output(['git','diff','--name-only']).decode().splitlines())<=existing
assert set(subprocess.check_output(['git','ls-files','--others','--exclude-standard']).decode().splitlines())==new
m.update(packet_sha256=PACKET,fixes=receipts,required_tests=61)
(OUT/'candidate-manifest.json').write_text(json.dumps(m,indent=2)+'\n')
print('Exact public-space candidate staged: 12 source/assets/docs files; original 50 regressions unchanged.')
