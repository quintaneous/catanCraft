#!/usr/bin/env python3
"""Recover checked complete patch sections; reconstruct exact supplied assets; never edit a world."""
from pathlib import Path
import base64, hashlib, io, json, lzma, struct, subprocess, zipfile, zlib

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'tools/construction34'
def sha(b): return hashlib.sha256(b).hexdigest()
def replace(path,old,new):
    p=ROOT/path;s=p.read_text()
    if s.count(old)!=1: raise RuntimeError('Expected unique source at '+path)
    p.write_text(s.replace(old,new))
parts=sorted(BASE.glob('source.patch.xz.b64.*'))
assert [p.name for p in parts]==['source.patch.xz.b64.00','source.patch.xz.b64.01','source.patch.xz.b64.02']
s=''.join(p.read_text().strip() for p in parts);d=lzma.LZMADecompressor();patch=d.decompress(base64.b64decode(s[:len(s)//4*4]))
assert not d.eof and sha(patch)=='b8fb6c423187c5033b0fad4f98d8ed7dd740d7f357e0a05d63e462dbf7d02072'
text=patch.decode();marker='--- /dev/null\n+++ b/src/main/resources/data/catancraft/basic_construction/assets.b64\n'
assert text.count(marker)==1
complete,tail=text.split(marker)
subprocess.run(['git','apply','--check','-'],input=complete.encode(),check=True,cwd=ROOT)
subprocess.run(['git','apply','-'],input=complete.encode(),check=True,cwd=ROOT)
encoded=''.join(line[1:] for line in tail.splitlines() if line.startswith('+'))
partial=base64.b64decode(encoded[:len(encoded)//4*4]);off=0;members={}
while partial[off:off+4]==b'PK\x03\x04':
    _,v,f,m,t,date,crc,cs,us,nl,xl=struct.unpack_from('<IHHHHHIIIHH',partial,off)
    name=partial[off+30:off+30+nl].decode();start=off+30+nl+xl
    if start+cs>len(partial):break
    assert m==0 and cs==us
    members[name]=partial[start:start+cs];off=start+cs
first=['starter_settlement.schem','th1.schem','th2.schem','th3.schem']
assert list(members)==first
for name,digest in zip(first,['fe03d2ddcc76555ad56f0767d601100380193053bfdca1f56dd2f45d7be9e443','dfe74c98ba01bc2d66496b2d306a61de951ad0a3a24af6d6de895d2dbb16ec88','63041e5acbd8f669b8dcc71251382e2df4a901d52c34d94f4398e35047ce23c9','e659c7fd88dfa7cad2464d3f4bb1a9da28178efab7463da9106a1a76e56e5129']):assert sha(members[name])==digest
supp=(BASE/'factory-supplement.b64').read_bytes();assert sha(supp)=='38dcd8d267b86d18e3c2d02c421efa865dcda969f5f87e3eeda6fe162c480f28'
with zipfile.ZipFile(io.BytesIO(lzma.decompress(base64.b64decode(supp)))) as z:
    meta=json.loads(z.read('metadata.json'))
    for name,record in meta.items():
        compressor=zlib.compressobj(9,zlib.DEFLATED,-15)
        value=bytes.fromhex(record['header'])+compressor.compress(z.read(name))+compressor.flush()+bytes.fromhex(record['footer'])
        assert sha(value)==record['sha256'] and name not in members
        members[name]=value
assert len(members)==13
out=io.BytesIO()
with zipfile.ZipFile(out,'w',zipfile.ZIP_STORED) as z:
    for name,value in members.items():
        info=zipfile.ZipInfo(name,(2026,10,28,0,0,0));info.external_attr=0o100644<<16;z.writestr(info,value)
bundle=out.getvalue();assert sha(bundle)=='cbaf8b095958f7de19a58bbca2723f18a1273d908eb5f174d36cd4aa1e9608d1'
p=ROOT/'src/main/resources/data/catancraft/basic_construction/assets.b64';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(base64.encodebytes(bundle))
replace('src/main/java/com/quin/catancraft/world/BasicConstructionAssets.java','081f7f543e6ba3e270c9b08a25aa3119feb461bd6fe89d8ef64f662d4035671d',sha(bundle))
original_names={'starter_settlement':'starter_settlement_v2','th1':'thall1','th2':'thall2','th3':'thall3','vehicle_factory':'vehiclefactory','weapons_factory':'weaponsfactory'}
evidence={'archive_sha256':'2a7a362047a6672b802c0077cd58db236183c10a69cc69dc928c64859d985edc','source':'mcwar.zip / mcwar/config/catancraft/schematics_bundle.zip','members':[{'asset':name[:-6],'source_member':original_names.get(name[:-6],name[:-6])+'.schem','sha256':sha(value),'size':len(value)} for name,value in members.items()],'bundle_sha256':sha(bundle),'recovered_complete_members_byte_identical':first}
(ROOT/'tests/fixtures/basic-construction-provenance.json').write_text(json.dumps(evidence,indent=2)+'\n')
replace('src/main/java/com/quin/catancraft/world/DefenseTransaction.java','if(!e.cost().canAfford(e.nation())){if(exists)throw new PreparationDeferred("Original defense price is outstanding: "+e.cost().describe());throw new IOException("Cannot afford defense: "+e.cost().describe());}e.save();','if(!e.cost().canAfford(e.nation()))throw new IOException("Cannot afford defense: "+e.cost().describe());e.save();')
replace('src/main/java/com/quin/catancraft/world/DefenseTransaction.java','throw new IOException("Ambiguous defense reservation");e.cost().charge(e.nation());','throw new IOException("Ambiguous defense reservation");\n            if(!e.cost().canAfford(e.nation()))throw new PreparationDeferred("Original defense price is outstanding: "+e.cost().describe());\n            e.cost().charge(e.nation());')
p=ROOT/'src/main/java/com/quin/catancraft/world/ConstructionSafetyTests.java';s=p.read_text();i=s.rfind('\n}')
s=s[:i]+'''
    @GameTest(template="townhall_state_empty",batch="queue06",timeoutTicks=6000)
    public static void legacy_prepared_defense_waits_for_original_price_without_writes(GameTestHelper h)throws Exception {
        var server=h.getLevel().getServer();PaidRecoveryService.stopped(server);
        var f=new DefenseTests.Fixture(h.getLevel(),"a","front_w_04");AABB bounds=null;
        for(var c:f.plan.cells()){AABB b=new AABB(c.pos()).inflate(2);bounds=bounds==null?b:bounds.minmax(b);}for(var c:f.plan.reads())bounds=bounds.minmax(new AABB(c.pos()).inflate(2));
        final AABB area=bounds;PaidRecoveryTests.ready(h,area,()->{
            f.stage();long cash=f.nation.treasury();
            PaidRecoveryTests.refused(h,()->DefenseTransaction.run(f,DefenseTransaction.Action.APPLY,(phase,n)->{if(phase.equals("prepared"))throw new IOException("prepared-interrupted");}),"prepared-interrupted");
            byte[] journal=Files.readAllBytes(f.file);f.nation.setTreasury(0);
            try{DefenseTransaction.run(f,DefenseTransaction.Action.RESUME);throw new IOException("Expected deferred original-price wait");}
            catch(PreparationDeferred expected){h.assertTrue(expected.getMessage().contains("Original defense price"),"Missing original-price reason");}
            h.assertTrue(Arrays.equals(journal,Files.readAllBytes(f.file))&&f.nation.treasury()==0,"Deferred reservation changed history or balance");
            for(var c:f.plan.cells())h.assertTrue(f.level().getBlockState(c.pos()).equals(c.before()),"Price wait changed world");
            f.nation.setTreasury(cash);DefenseTransaction.run(f,DefenseTransaction.Action.RESUME);
            long paid=f.nation.treasury();h.assertTrue(paid==cash-f.cost().money(),"Original price not settled once");
            h.assertTrue(DefenseTransaction.run(f,DefenseTransaction.Action.RESUME).writes()==0&&f.nation.treasury()==paid,"Repeated payment");h.succeed();
        });
    }
'''+s[i:];p.write_text(s)
replace('docs/CONSTRUCTION_SAFETY_034.md','23 new cases','24 new cases')
print('Materialized complete reviewed source and 13 exact delivered schematic members. No world files accessed.')
