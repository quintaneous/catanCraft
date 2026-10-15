"""One-shot, hash-pinned source transfer. No world installer or production data access."""
import base64, hashlib, io, json, pathlib, subprocess, zipfile
ROOT = pathlib.Path.cwd()
INPUT = ROOT / '.integration/producer26'
OUT = ROOT / 'build/producer26'
OUT.mkdir(parents=True, exist_ok=True)
BASE = 'bda0a80b7015156faf10b298e7bde1174a68c271'
PACKET = 'a5801ae272a51c6dd9d2254fc57f4fba15b3f2001ceb09c55e62dea781021ea2'
# Bounded transcription repairs only; every canonical piece and the full archive are pinned.
CORRECTIONS = {
  2: [('Cf1FYJWJW0G/sBVIw', 'Cf1FYJW0G/sBVIw')],
  6: [('ivEvX7ouss', 'ivEvX9ouss'), ('LIBJppZZ+cCg', 'LIBJppZ+cCg')],
  8: [('BbmCQWn3Basmg', 'BbmCQW3Basmg')],
}
SHAS = ['4c3233a426705ff48b7b765a1216062a6eef7b02', 'b67facb6af221d7b8eb5c4c43f0d9881e209ce81',
'05efc0e3ebf4682499e9518802a7e2eb9f823ca4', 'd9abf27118866957f8939e1d3ed1c63f30818c62',
'1c18cf026a7b56526f1f98ed83448d66f2d57ab9', '59c74d55c0978e80df78e62f1d84648e35f4b162',
'c0b9a0a3aaa5f2e9f6a852e57a6fede28049df66', 'e76deb6a05cafe30a4ea6a345f1fb226ace69c6c',
'b266263c512dd7b33052115cdf5e235d563c3a96', 'c04cf7a9cb9e513cd07515071584ed4dff09d113']
def sha(data): return hashlib.sha256(data).hexdigest()
def safe(path):
    p = pathlib.PurePosixPath(path)
    if p.is_absolute() or '..' in p.parts or not p.parts or '\\' in path: raise ValueError(path)
    return ROOT.joinpath(*p.parts)
subprocess.run(['git','merge-base','--is-ancestor',BASE,'HEAD'],check=True)
parts = []
for i, expected in enumerate(SHAS):
    text = (INPUT / f'packet-{i:02}.txt').read_text()
    for old,new in CORRECTIONS.get(i,[]):
        if old in text:
            assert text.count(old)==1
            text=text.replace(old,new)
    data=text.encode()
    assert hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()==expected, f'Piece {i}'
    parts.append(data)
raw = base64.b64decode(b''.join(parts),validate=True)
assert len(raw)==54340 and sha(raw)==PACKET, 'Source packet SHA mismatch'
z=zipfile.ZipFile(io.BytesIO(raw))
assert z.testzip() is None
assert len(z.namelist())==len(set(z.namelist()))
m=json.loads(z.read('manifest.json'))
assert m['base_commit']==BASE
files=m['files']
allowed_existing={'build.gradle','src/main/java/com/quin/catancraft/command/CatanCommands.java','src/main/java/com/quin/catancraft/world/MapAssetService.java'}
allowed_new={'docs/PRODUCER_R1_TRANSACTION.md','tests/producer_asset_pins.json'}
allowed_new.update('src/main/java/com/quin/catancraft/world/Producer'+n+'.java' for n in ['Journal','Revisions','Service','Tests','Transaction'])
allowed_new.update(f'src/main/resources/data/catancraft/producer_revisions/{f}_l{l}.schem' for f in ['farm','lumberyard','quarry'] for l in range(1,6))
assert set(files)==allowed_existing|allowed_new
for name, meta in files.items():
    p=safe(name)
    if meta['before'] is None: assert not p.exists(),name
    else: assert p.is_file() and not p.is_symlink() and sha(p.read_bytes())==meta['before'],name
expected_members={'manifest.json','changes.patch'}|{'files/'+p for p in allowed_new}
assert set(z.namelist())==expected_members
patch=OUT/'changes.patch';patch.write_bytes(z.read('changes.patch'))
subprocess.run(['git','apply','--check',str(patch)],check=True)
subprocess.run(['git','apply',str(patch)],check=True)
for name in allowed_new:
    p=safe(name);p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read('files/'+name))
for name,meta in files.items(): assert sha(safe(name).read_bytes())==meta['after'], name
# Optional explicit, hash-checked CI corrections. Cannot add other files or run scripts.
fix_receipts=[]
for path in sorted(INPUT.glob('fix-*.json')):
    edits=json.loads(path.read_text())
    for edit in edits:
        name=edit['path'];assert name in files
        p=safe(name);data=p.read_bytes();assert sha(data)==edit['before'],name
        old,new=edit['old'].encode(),edit['new'].encode()
        assert old and data.count(old)==1,name
        data=data.replace(old,new)
        assert sha(data)==edit['after'],name
        p.write_bytes(data);files[name]['after']=sha(data)
    fix_receipts.append({'name':path.name,'sha256':sha(path.read_bytes())})
# No hidden or out-of-scope working-tree changes.
changed=subprocess.check_output(['git','diff','--name-only']).decode().splitlines()
assert set(changed)<=allowed_existing
untracked=subprocess.check_output(['git','ls-files','--others','--exclude-standard']).decode().splitlines()
assert set(untracked)==allowed_new,untracked
m.update(packet_sha256=PACKET,fixes=fix_receipts,required_tests=50)
(OUT/'candidate-manifest.json').write_text(json.dumps(m,indent=2)+'\n')
print('Exact producer candidate staged: 25 source/assets/docs files; original regressions unchanged.')
