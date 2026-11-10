# Verify downloaded Stock42 exact-source evidence; run from the repository root.
# Arguments: archive.zip source_commit archive_sha256 workflow_run artifact_id
# Requires the retained validated041 JAR at the path used below.
from pathlib import Path
import hashlib,json,re,subprocess,sys,zipfile,gzip,struct,io,uuid
archive=Path(sys.argv[1]);commit=sys.argv[2];artifact_sha=sys.argv[3];run=int(sys.argv[4]);artifact=int(sys.argv[5])
sha=lambda b:hashlib.sha256(b).hexdigest()
assert sha(archive.read_bytes())==artifact_sha
root=archive.parent/'expanded';fresh=not root.exists();root.mkdir(exist_ok=True)
with zipfile.ZipFile(archive) as z:
 assert z.testzip() is None
 for i in z.infolist():
  assert not Path(i.filename).is_absolute() and '..' not in Path(i.filename).parts
  assert (i.external_attr >> 16) & 0o170000 != 0o120000
 if fresh:z.extractall(root)
m=json.loads((root/'MANIFEST.json').read_text());assert m['source']==commit
expected={'catancraft_stock42':8,'native_tacz':8,'native_tacz_seed':1,'native_tacz_verify':1,'native_sbw':8,'native_sbw_seed':1,'native_sbw_verify':1,'native_combined':1,'catancraft_shop40':35,'shop_seed':1,'shop_verify':1,'catancraft':50,'catancraft_paid_recovery':19,'catancraft_rewards41':32,'rewards_seed':1,'rewards_verify':1,'catancraft_sbw':7,'procurement_seed':1,'procurement_verify':1}
assert set(m['results'])==set(expected) and m['expected_phase_executions']==178 and m['expected_unique_scenarios']==173
for f in m['files']:assert sha((root/f['saved_as']).read_bytes())==f['sha256'],f
folder=lambda ns:('native_tacz' if ns.startswith('native_tacz') else 'native_sbw' if ns.startswith('native_sbw') else ns) if ns.startswith('native_') else 'rewards' if ns.startswith('rewards_') or ns=='catancraft_rewards41' else 'shop_restart' if ns.startswith('shop_') else 'sbw_restart' if ns.startswith('procurement_') or ns=='catancraft_sbw' else ns
for ns,count in expected.items():
 ev=root/folder(ns)/'stock42-evidence';log=(ev/(ns+'.log')).read_text()
 assert (ev/'SOURCE_COMMIT.txt').read_text().strip()==commit
 assert 'BUILD SUCCESSFUL' in (ev/'build.log').read_text()
 assert len(re.findall(rf'\b{count} GAME TESTS COMPLETE\b',log))==1
 assert f'All {count} required tests passed' in log and (ev/(ns+'.exit')).read_text().strip()=='0'
 assert m['results'][ns]=={'expected':count,'passed':True}
assert m['all_passed']
jar=next((root/'shared').glob('*.jar'));jarsha=sha(jar.read_bytes())
with zipfile.ZipFile(jar) as z:
 assert z.testzip() is None
 assert f'CatanCraft-Source-Commit: {commit}' in z.read('META-INF/MANIFEST.MF').decode()
 assert '0.1.42-native-weapon-stock' in z.read('META-INF/mods.toml').decode()
 for name in ['ShopPurchase40','ShopFacility40','PhysicalWeaponShop40','MonumentRewards41','WeaponTierCatalog41','RewardTable41','NativeWeapon42','NativeTacz42','NativeSbw42','WeaponStock42']:
  assert 'com/quin/catancraft/world/'+name+'.class' in z.namelist()
for ns in expected:assert (root/folder(ns)/'stock42-evidence/JAR_SHA256.txt').read_text().split()[0]==jarsha
with zipfile.ZipFile(root/'shared/source.zip') as z:
 assert z.testzip() is None
 files=subprocess.check_output(['git','ls-tree','-r','--name-only',commit],text=True).splitlines()
 assert set(files)=={n for n in z.namelist() if not n.endswith('/')}
 for f in files:assert z.read(f)==subprocess.check_output(['git','show',commit+':'+f]),f
old=Path('build/economy41-evidence/run1/expanded/shared/catancraft-0.1.41-economy-foundation.jar')
with zipfile.ZipFile(old) as a,zipfile.ZipFile(jar) as b:
 changed=[n for n in a.namelist() if not n.endswith('/') and (n not in b.namelist() or a.read(n)!=b.read(n))]
 allowed={'META-INF/MANIFEST.MF','META-INF/mods.toml'}|{'com/quin/catancraft/'+n+'.class' for n in ['CatanCraft','world/PhysicalWeaponShop40','world/PhysicalWeaponShop40$Quote','world/PhysicalWeaponShop40$Session','world/ShopPurchase40','world/ShopPurchase40$Lock','world/ShopNativePlayerFixture40']}
 assert set(changed)<=allowed,changed
 classes=sum(n.endswith('.class') and n in b.namelist() and a.read(n)==b.read(n) for n in a.namelist())
def native(data):
 stream=io.BytesIO(gzip.decompress(data))
 def num(fmt):return struct.unpack('>'+fmt,stream.read(struct.calcsize('>'+fmt)))[0]
 def string():return stream.read(num('H')).decode('utf-8')
 def value(t):
  if t in (1,2,3,4,5,6):return num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
  if t==7:return stream.read(num('i'))
  if t==8:return string()
  if t==9:
   subtype=num('B');return [value(subtype) for _ in range(num('i'))]
  if t==10:
   result={}
   while True:
    kind=num('B')
    if kind==0:return result
    key=string();result[key]=value(kind)
  if t in (11,12):return [num('i' if t==11 else 'q') for _ in range(num('i'))]
  raise AssertionError(t)
 assert num('B')==10;string();result=value(10);assert stream.read()==b'';return result
def sealed(path):
 data=path if isinstance(path,bytes) else path.read_bytes();magic,size=struct.unpack('>II',data[:8]);assert magic==0x43505231 and len(data)==40+size
 assert hashlib.sha256(data[40:]).digest()==data[8:40]
 return native(data[40:])
def uuidstr(a):return str(uuid.UUID(bytes=struct.pack('>4i',*a)))
def receipts(n):return n.get('ForgeData',{}).get('PlayerPersisted',{}).get('CatanCraftShop40',{})
restart=root/'shop_restart/stock42-evidence/restart'
seedpath=restart/'catancraft_shop40_restart_seed.proof';verifiedpath=restart/'catancraft_shop40_restart_verified.proof'
seed=sealed(seedpath);verified=sealed(verifiedpath)
assert seed['source']==verified['source']==commit and verified['result']=='PASS'
assert seed['process']==verified['seedProcess'] and seed['process']!=verified['process']
assert verified['seedProofSHA256']==sha(seedpath.read_bytes())
assert len(seed['actors'])==len(verified['actors'])==2
for i,(a,b) in enumerate(zip(seed['actors'],verified['actors'])):
 op=uuidstr(a['operation']);actor=uuidstr(a['actor']);intent=sealed(a['originalIntentBytes'])
 assert a['originalIntentBytes']==(restart/a['intentFile']).read_bytes()
 assert sha(a['originalIntentBytes'])==a['intentSHA256']==a['debitReceipt']
 assert intent['price']==a['price'] and intent['item']==a['item']
 assert sha(a['originalPlayerBytes'])==a['originalPlayerSHA256']
 assert sha(a['originalProgressBytes'])==a['originalProgressSHA256']
 assert sha(a['nativePlayerBeforeConsumptionBytes'])==a['nativePlayerBeforeConsumptionSHA256']
 assert a['progressPhase']==('ABSENT' if i==0 else 'DELIVERING')
 if i==0:assert a['originalProgressBytes']==b''
 else:assert sealed(a['originalProgressBytes'])['phase']=='DELIVERING'
 assert a['nativeCountAtRestart']==0 and a['nativeCountBeforeConsumption']==i
 assert a['deliveryReceipt']==('' if i==0 else a['intentSHA256'])
 assert b['nativeCountAfterRecovery']==b['newItemsAfterRecovery']==1-i
 assert b['additionalDebit']==b['completedRetrySaves']==0 and b['completedRetries']==3
 assert b['nationAfterRecovery']==a['nationAfterDebit']
 completion=restart/a['progressFile'];assert sha(completion.read_bytes())==b['completionSHA256']
 assert sealed(completion)['phase']=='COMMITTED' and sealed(completion)['intentSHA256']==a['intentSHA256']
 current=restart/(actor+'.dat');assert sha(current.read_bytes())==b['nativePlayerAfterRecoverySHA256']
 assert receipts(native(current.read_bytes()))[op]==a['intentSHA256']
 assert receipts(native(a['originalPlayerBytes'])).get(op,'')==a['deliveryReceipt']
assert sha((restart/'catancraft.dat').read_bytes())==verified['nativeLedgerAfterRecoverySHA256']
rewardroot=root/'rewards/stock42-evidence/rewards-restart'
rseedpath=rewardroot/'catancraft_rewards41_restart_seed.proof'
rverifypath=rewardroot/'catancraft_rewards41_restart_verified.proof'
rs=sealed(rseedpath);rv=sealed(rverifypath)
assert rs['source']==rv['source']==commit and rv['result']=='PASS'
assert rv['seedProcess']==rs['process'] and rv['process']!=rs['process']
assert rv['seedProofSHA256']==sha(rseedpath.read_bytes())
assert sha(rs['preparedNativeLedgerBytes'])==rs['preparedNativeLedgerSHA256']
assert sha(rs['originalNativeLedgerBytes'])==rs['originalNativeLedgerSHA256']
assert native(rs['originalNativeLedgerBytes'])['data']==rs['capturedNativeLedger']
assert rs['preparedActivation']['phase']=='OPEN' and 'winner' not in rs['preparedActivation']
assert rs['capturedActivation']['phase']=='CAPTURED' and rv['settledActivation']['phase']=='SETTLED'
assert rs['capturedActivation']['winner']==rv['settledActivation']['winner']==rs['nation']
for key in ('id','identity','monument','tableRevision','tableSHA256','entry','roll'):
 assert rs['preparedActivation'][key]==rs['capturedActivation'][key]==rv['settledActivation'][key]
intent_expected={k:v for k,v in rs['capturedActivation'].items() if k!='phase'}
assert rv['intentBytes']==(rewardroot/rv['intentFile']).read_bytes()
assert rv['receiptBytes']==(rewardroot/rv['receiptFile']).read_bytes()
assert sha(rv['intentBytes'])==rv['intentSHA256'] and sealed(rv['intentBytes'])==intent_expected
rc=sealed(rv['receiptBytes']);assert sha(rv['receiptBytes'])==rv['receiptSHA256']
assert rc=={'schema':1,'id':rs['activation'],'phase':'COMMITTED','intentSHA256':rv['intentSHA256']}
assert sha((rewardroot/'catancraft.dat').read_bytes())==rv['settledNativeLedgerSHA256']
assert native((rewardroot/'catancraft.dat').read_bytes())['data']==rv['settledNativeLedger']
assert rv['settledNativeLedger']['placedMapAssets']['monument41_paid:'+uuidstr(rs['activation'])]==rv['intentSHA256']
assert rv['pendingEntitlements']==3 and rv['spawnedEquipment']==rv['completedRetrySaves']==0 and rv['completedRetries']==3
assert len(rs['expectedReward']['entitlements'])==3 and rs['expectedReward']==intent_expected['roll']
assert rv['nationAfterRecovery']['treasury']==rs['nationBefore']['treasury']+rs['expectedReward']['money']
for resource,amount in rs['expectedReward']['resources'].items():
 assert rv['nationAfterRecovery']['stockpile'][resource.lower()]==rs['nationBefore']['stockpile'][resource.lower()]+amount
native_proofs={}
for provider in ('tacz','sbw'):
 nr=root/('native_'+provider)/'stock42-evidence/native-restart'
 seedfile=nr/('catancraft_native42_'+provider+'_restart_seed.proof')
 donefile=nr/('catancraft_native42_'+provider+'_restart_verified.proof')
 ns=sealed(seedfile);nv=sealed(donefile)
 assert ns['source']==nv['source']==commit and nv['result']=='PASS'
 assert ns['process']==nv['seedProcess'] and ns['process']!=nv['process']
 assert nv['seedProofSHA256']==sha(seedfile.read_bytes())
 op=uuidstr(ns['operation']);actor=uuidstr(ns['actor'])
 original=sealed(ns['originalIntentBytes']);progress=sealed(ns['originalProgressBytes'])
 assert sha(ns['originalIntentBytes'])==ns['intentSHA256']==ns['debitReceipt']==ns['deliveryReceipt']==nv['intentSHA256']
 assert sha(ns['originalProgressBytes'])==ns['progressSHA256'] and progress['phase']=='DELIVERING'
 intentfile=nr/('catancraft_shop40_'+actor+'_'+op+'.dat')
 assert intentfile.read_bytes()==ns['originalIntentBytes']
 assert original['item']==ns['item'] and original['catalog'].endswith('@'+ns['fingerprint'])
 assert sha(ns['originalPlayerBytes'])==ns['playerSHA256']
 assert (nr/(actor+'.dat')).read_bytes()==ns['originalPlayerBytes']
 assert receipts(native(ns['originalPlayerBytes']))[op]==ns['intentSHA256']
 assert sha(ns['originalLedgerBytes'])==ns['ledgerSHA256'] and native(ns['originalLedgerBytes'])['data']==ns['nativeLedger']
 assert native((nr/'catancraft.dat').read_bytes())['data']==ns['nativeLedger']
 completion=sealed(nr/(intentfile.name+'.receipt'))
 assert completion['phase']=='COMMITTED' and completion['intentSHA256']==ns['intentSHA256'] and completion['id']==ns['operation']
 assert nv['additionalDebit']==nv['newItemsAfterRecovery']==0 and nv['nativeItemCount']==1 and nv['completedRetries']==3
 assert nv['firstRecoveryLedgerSaves']==1 and nv['firstRecoveryPlayerSaves']==nv['completedRetryLedgerSaves']==nv['completedRetryPlayerSaves']==0
 if provider=='sbw':assert ns['nativeGunUUID']==nv['nativeGunUUID']
 native_proofs[provider]={'source':commit,'seed_process':ns['process'],'verify_process':nv['process'],'item_id':ns['item']['id'],'fingerprint':ns['fingerprint'],'native_uuid':uuidstr(ns['nativeGunUUID']) if provider=='sbw' else None,'first_recovery_ledger_saves':1,'completed_retry_saves':0}
record={'source':commit,'run':run,'workflow_url':f'https://github.com/quintaneous/catanCraft/actions/runs/{run}','artifact_id':artifact,'archive_sha256':artifact_sha,'unique_scenarios':173,'phase_executions':178,'passed_phase_executions':178,'failed':0,'results':m['results'],'jar_file':jar.name,'jar_bytes':jar.stat().st_size,'jar_sha256':jarsha,'source_files_byte_compared':len(files),'all_manifest_files_verified':len(m['files']),'identical_preexisting_classes_vs_041':classes,'changed_preexisting_jar_entries_vs_041':changed,'native_weapon_restart_proofs':native_proofs,'restart_proof_verified':True,'weighted_reward_component_restart_verified':True,'reward_seed_process':rs['process'],'reward_verify_process':rv['process'],'seed_process':seed['process'],'verify_process':verified['process'],'scope':'Controlled native TaCZ1.2.0/SBW0.8.9.2 gun/ammo supply, purchase recovery, provider coexistence and separate-JVM restarts; provisional prices; no owner installed-pack, authenticated client, combat balance, monument equipment claims or owner acceptance'}
(archive.parent/'verification.json').write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record,indent=2))
