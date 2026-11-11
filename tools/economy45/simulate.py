#!/usr/bin/env python3
"""Offline affordability model; reads repository source, never worlds or installed mods."""
from pathlib import Path
import re,json,hashlib,math,subprocess,copy,argparse
ROOT=Path(__file__).resolve().parents[2]
JAVA=ROOT/'src/main/java/com/quin/catancraft'
SOURCES={}
def source(path):
 p=ROOT/path; data=p.read_bytes(); SOURCES[path]=hashlib.sha256(data).hexdigest(); return data.decode()
def java(path):return source('src/main/java/com/quin/catancraft/'+path+'.java')
def method(text,name):
 p=re.search(r'(?:public|private) (?:static )?[\w<>, ]+ '+name+r'\(',text).start(); a=text.index('{',p);depth=1;i=a+1
 while depth:
  depth+=(text[i]=='{')-(text[i]=='}');i+=1
 return text[a+1:i-1]
def numbers(s):return [int(x.replace('_','')) for x in re.findall(r'\b\d[\d_]*',s)]
def cost(s):
 m=re.search(r'new EconomyCost\(([\d_]+),\s*Map.of\((.*?)\)\)',s,re.S);assert m,s
 return {'money':int(m[1].replace('_','')),**{r.lower():int(v.replace('_','')) for r,v in re.findall(r'ResourceType\.(\w+),\s*([\d_]+)L',m[2])}}

def switch(s):
 result={}
 for keys,value in re.findall(r'case\s+(.*?)\s*->\s*(.*?);',s,re.S):
  for key in keys.split(','):result[key.strip()]=value.strip()
 d=re.search(r'default\s*->\s*(.*?);',s,re.S)
 return result,d[1] if d else None

def level_values(s):
 values,default=switch(s);return [int(re.sub('[L_]','',values.get(str(i),default))) for i in range(1,6)]
def scale(c,p):return {k:(max(1,math.floor(v*p/100+.5)) if k!='money' else math.floor(v*p/100+.5)) for k,v in c.items()}

def load():
 bal=java('economy/EconomyBalance');eng=java('economy/EconomyEngine');cat=java('economy/EconomyCatalog');bt=java('data/BuildingType')
 m=json.loads(source('src/main/resources/data/catancraft/maps/season1_river_bridges_v3.json'))
 raw=level_values(method(bal,'rawProductionPerCycle')); upkeep=level_values(method(bal,'agricultureUpkeepPerCycle'))
 commercial=level_values(method(eng,'commercialIncomePerCycle'));batches=level_values(method(bt,'batchesPerCycle'))
 props={}
 types=re.search(r'enum BuildingType\s*\{(.*?);',bt,re.S)[1].replace('\n','').split(',');types=[t.strip() for t in types]
 for typ in types:
  props[typ]={}
  for name in ['processingTier','output','outputPerBatch','inputs','minCityLevel','costPercent']:
   choices,default=switch(method(bt,name));v=choices.get(typ,default)
   if name=='inputs':v={k.lower():int(v) for k,v in re.findall(r'ResourceType\.(\w+),\s*(\d+)',v)}
   elif name=='output':v=v.split('.')[-1].lower() if v!='null' else None
   else:v=int(v)
   props[typ][name]=v
 costs={'claim':cost(method(cat,'neutralTerritoryClaimCost'))}
 for name,prefix in [('cityUpgradeCost','city'),('producerUpgradeCost','producer'),('buildingUpgradeCost','upgrade_base')]:
  choices,_=switch(method(cat,name))
  for level,v in choices.items():costs[f'{prefix}_{level}']=cost(v)
 base=cost(method(cat,'buildingCost'))
 for typ,p in props.items():
  costs['build_'+typ]=scale(base,p['costPercent'])
  for level in range(1,5):costs[f'upgrade_{typ}_{level}']=scale(costs[f'upgrade_base_{level}'],p['costPercent'])
 sector=java('world/SectorPriceCatalog')
 for key in ['BASIC','REINFORCED_INCREMENT']:costs['sector_'+key.lower()]=cost(sector[sector.index(' '+key+'='):].split(';')[0])
 defenses=java('world/DefenseCostCatalog');dc,_=switch(method(defenses,'cost'))
 for k,v in dc.items():costs['legacy_defense_'+k.strip('"')]=cost(v)
 procurement=java('command/NationProcurement')
 for key,body in re.findall(r'offer\("(.*?)",(.*?)\)\);',procurement,re.S):
  if 'new EconomyCost' in body: costs['procure_'+key]=cost(body+')')
 stock=java('world/WeaponStock42'); tiers=json.loads(source('src/main/resources/data/catancraft/economy/weapon_tiers41.json'))['entries']
 prices={k:numbers(v) for k,v in re.findall(r'"(\w+)", List.of\((.*?)\)',stock)}
 mats=[numbers(v) for v in re.findall(r'new Materials\((.*?)\)',stock)];assert len(mats)==5
 section=stock[stock.index('CATEGORY_STEEL ='):stock.index('// Published SBW')];steel={k:int(v) for k,v in re.findall(r'"(\w+)", (\d+)L',section)}
 active=[e for e in tiers if e['provider']=='tacz' and e['disposition'] in ['STARTER','SHOP'] and not (set(e['qualificationFlags'])-{'REFERENCE_ONLY'})]
 assert len(active)==52
 guns={};ammos={}
 for e in active:
  name=e['id'];level=max(0,e['proposedLevel']);category=e['category'].lower()
  if name in ['tacz:glock_17','tacz:ak47']:c=costs['procure_'+('pistol' if name.endswith('glock_17') else 'ak47')]
  else:
   c={'money':prices[category][level-1],**dict(zip(['wood','steel','mechanical_parts','electronics'],mats[level-1]))};c['steel']+=steel[category];c={k:v for k,v in c.items() if v}
  costs[name]=c;guns[name]={'level':level,'category':category,'ammo':e['ammo']};ammos[e['ammo']]=min(level,ammos.get(e['ammo'],99))
 ammo_specs={k:{'money':int(a),'steel':int(b),'explosives':int(c)} for k,a,b,c in re.findall(r'Map.entry\("(tacz:[^"]+)", ammo\("[^"]+", (\d+), (\d+), (\d+)\)\)',stock)}
 for name,level in ammos.items():costs['ammo_'+name]=costs['procure_ammo9' if name=='tacz:9mm' else 'procure_ammo762'] if name in ['tacz:9mm','tacz:762x39'] else ammo_specs[name]
 assert len(ammos)==17
 saved=java('data/CatanSavedData');start=int(re.search(r'new NationData\(UUID.randomUUID\(\), name, leaderId, ([\d_]+)\)',saved)[1].replace('_',''))
 # Record behavioral sources too: hash provenance is not a claim of executing Java.
 for f in ['world/ActiveWeaponStock44','world/IndustrialPlotService','data/BuildingInstance','command/CatanCommands','economy/EconomyCost']:java(f)
 return dict(cycle_minutes=int(re.search(r'CYCLE_MINUTES = (\d+)',bal)[1]),cash=int(re.search(r'INCOME_PER_CYCLE = (\d+)L',bal)[1]),raw=raw,upkeep=upkeep,commercial=commercial,batches=batches,props=props,costs=costs,start=start,territories={t['id']:t for t in m['territories']},guns=guns,ammos=ammos,ammo_pack_counts={k:30 if k=='tacz:338' else 32 for k in ammos})

class Sim:
 def __init__(self,c):self.c=c;self.stock={'money':c['start']};self.owned={'a':{'city':1,'producer':1,'buildings':[]}};self.events=[];self.cycle=0;self.starved=0
 def add(self,k,v):self.stock[k]=self.stock.get(k,0)+v;assert self.stock[k]>=0,(k,self.stock[k])
 def afford(self,cost):return all(self.stock.get(k,0)>=v for k,v in cost.items())
 def pay(self,cost):
  assert self.afford(cost)
  for k,v in cost.items():self.add(k,-v)
 def output(self,tid,r):
  t=self.c['territories'][tid];level=self.owned[tid]['producer'];return math.floor(self.c['raw'][level-1]*t.get('resourceYieldPercent',{}).get(r,100)/100+.5)
 def tick(self):
  c=self.c
  for tid in sorted(self.owned):
   if 'agriculture' in c['territories'][tid]['resources']:self.add('agriculture',self.output(tid,'agriculture'))
  for tid,t in sorted(self.owned.items()):
   raw=[r for r in c['territories'][tid]['resources'] if r!='agriculture']
   if not raw:continue
   upkeep=c['upkeep'][t['producer']-1];available=self.stock.get('agriculture',0);ratio=min(1,available/upkeep);self.add('agriculture',-min(upkeep,available))
   if ratio<1:self.starved+=1
   for r in raw:self.add(r,math.floor(self.output(tid,r)*ratio))
  for tier in range(1,4):
   for tid,t in sorted(self.owned.items()):
    logistics=max([b['level'] for b in t['buildings'] if b['type']=='LOGISTICS_CENTER'] or [0]);bonus=[0,10,15,20,25,30][logistics]
    for b in t['buildings']:
     p=c['props'][b['type']];r=p['output']
     if p['processingTier']!=tier or self.stock.get(r,0)>=b['target']:continue
     batches=min(c['batches'][b['level']-1],max(1,math.ceil((b['target']-self.stock.get(r,0))/p['outputPerBatch'])))
     batches=min([batches]+[self.stock.get(k,0)//v for k,v in p['inputs'].items()])
     for k,v in p['inputs'].items():self.add(k,-v*batches)
     base=batches*p['outputPerBatch'];self.add(r,base+(base*bonus+99)//100)
  for t in self.owned.values():self.add('money',c['cash']+sum(c['commercial'][b['level']-1] for b in t['buildings'] if b['type']=='COMMERCIAL_DISTRICT'))
  self.cycle+=1
 def act(self,a):
  kind,tid,arg=a;t=self.owned.get(tid);costs=self.c['costs']
  if kind=='claim':
   if tid in self.owned or not any(n in self.owned for n in self.c['territories'][tid]['neighbors']):return False
   price=costs['claim']
  elif kind=='city':
   if t['city']>=arg:return True
   price=costs['city_'+str(t['city'])]
  elif kind=='producer':
   if t['producer']>=arg:return True
   price=costs['producer_'+str(t['producer'])]
  elif kind=='build':
   if any(b['type']==arg for b in t['buildings']):return True
   p=self.c['props'][arg];industry=arg not in ['COMMERCIAL_DISTRICT','LOGISTICS_CENTER']
   if t['city']<p['minCityLevel'] or len(t['buildings'])>=2*t['city'] or (industry and sum(b['type'] not in ['COMMERCIAL_DISTRICT','LOGISTICS_CENTER'] for b in t['buildings'])>=3):return False
   price=costs['build_'+arg]
  elif kind=='upgrade':
   typ,level=arg;b=next(b for b in t['buildings'] if b['type']==typ)
   if b['level']>=level:return True
   if b['level']>=t['city']:return False
   price=costs[f"upgrade_{typ}_{b['level']}"]
  elif kind=='buy':price=costs[arg]
  else:raise ValueError(kind)
  if not self.afford(price):return False
  self.pay(price)
  if kind=='claim':self.owned[tid]={'city':1,'producer':1,'buildings':[]}
  elif kind=='city':t['city']+=1
  elif kind=='producer':t['producer']+=1
  elif kind=='upgrade':b['level']+=1
  elif kind=='build':t['buildings'].append({'type':arg,'level':1,'target':1000 if arg in ['CONCRETE_PLANT','STEEL_MILL','MACHINE_SHOP','ELECTRONICS_FACTORY'] else 100})
  self.events.append({'hour':self.cycle/4,'action':a,'price':price.copy()});return True

def plan(players=2):
 p=[('build','a','COMMERCIAL_DISTRICT'),('city','a',2),('producer','a',2),('claim','b',None),('claim','f',None),('claim','g',None),('build','a','STEEL_MILL'),('build','a','CONCRETE_PLANT'),('city','f',2),('build','f','REFINERY'),('city','g',2),('build','g','WEAPONS_FACTORY'),('build','g','VEHICLE_FACTORY'),('city','a',3),('build','a','MACHINE_SHOP'),('city','f',3),('build','f','ELECTRONICS_FACTORY'),('build','f','CHEMICAL_PLANT')]
 p += [('buy','g','tacz:ak47')]*players+[('buy','g','ammo_tacz:762x39')]*(players*4)+[('buy','g','procure_pickup'),('buy','g','procure_lav150')]
 p += [('city','g',3),('build','g','TEXTILE_MILL'),('build','a','LOGISTICS_CENTER')]+[('buy','a','sector_basic')]*4+[('buy','a','sector_reinforced_increment')]*4
 return p

def endgame_plan():
 p=plan(2)[:6]+[('claim','e',None)]+plan(2)[6:]
 buildings={'a':['COMMERCIAL_DISTRICT','STEEL_MILL','CONCRETE_PLANT','MACHINE_SHOP','LOGISTICS_CENTER'],'f':['REFINERY','ELECTRONICS_FACTORY','CHEMICAL_PLANT'],'g':['WEAPONS_FACTORY','VEHICLE_FACTORY','TEXTILE_MILL']}
 for level in range(2,6):
  for tid,types in buildings.items():
   if level>3:p.append(('city',tid,level))
   p.append(('producer',tid,level))
   p.extend(('upgrade',tid,(typ,level)) for typ in types)
 return p

def manage_targets(sim,action):
 kind,tid,arg=action;c=sim.c;t=sim.owned.get(tid);costs=c['costs']
 if kind=='claim':price=costs['claim']
 elif kind in ['city','producer']:price=costs[kind+'_'+str(t[kind])]
 elif kind=='build':price=costs['build_'+arg]
 elif kind=='upgrade':
  b=next(b for b in t['buildings'] if b['type']==arg[0]);price=costs[f"upgrade_{arg[0]}_{b['level']}"]
 else:price=costs[arg]
 goals={k:v for k,v in price.items() if k!='money'}
 # Work backward through real input dependencies. Existing stock offsets each requirement.
 # Conservative whole batches do not assume an as-yet-unbuilt Logistics bonus.
 for tier in [3,2,1]:
  for p in c['props'].values():
   if p['processingTier']!=tier:continue
   missing=max(0,goals.get(p['output'],0)-sim.stock.get(p['output'],0));n=math.ceil(missing/p['outputPerBatch'])
   for k,v in p['inputs'].items():goals[k]=goals.get(k,0)+n*v
 for t in sim.owned.values():
  for b in t['buildings']:
   p=c['props'][b['type']]
   if p['processingTier']:b['target']=goals.get(p['output'],0)

def run(c,actions,hours=200,managed=False):
 s=Sim(c);pending=copy.deepcopy(actions);snapshots={}
 for tick in range(hours*4+1):
  while pending and s.act(pending[0]):pending.pop(0)
  if tick in [8,16,32,64,128,256,400,800]:snapshots[str(tick/4)]={'stock':s.stock.copy(),'purchases':len(s.events),'next':pending[0] if pending else None,'starved_territory_cycles':s.starved,'owned':copy.deepcopy(s.owned)}
  if tick<hours*4:
   if managed and pending:manage_targets(s,pending[0])
   s.tick()
 return {'snapshots':snapshots,'events':s.events,'unfinished':pending,'final_stock':s.stock}

def parity(c):
 resources=[x.lower() for x in re.findall(r'^    ([A-Z_]+)[,;]',java('data/ResourceType'),re.M)]
 def new(raw='agriculture',level=1,actual=False):
  cc=copy.deepcopy(c);sim=Sim(cc);sim.stock={'money':0}
  if not actual:cc['territories']['a']={'resources':[raw],'resourceYieldPercent':{}}
  sim.owned['a']['producer']=level
  return sim
 def b(name,level=1,target=100):return {'type':name,'level':level,'target':target}
 def result(name,sim):return {'case':name,'cash':sim.stock['money'],'cycles':sim.cycle,'stock':{r:sim.stock.get(r,0) for r in resources}}
 out={};sim=new(actual=True)
 for _ in range(8):sim.tick()
 out['starter_8_cycles']=result('starter_8_cycles',sim)
 sim=new(level=4,actual=True);sim.tick();out['starter_l4_rounding']=result('starter_l4_rounding',sim)
 sim=new('wood',4);sim.add('agriculture',2);sim.tick();out['partial_upkeep']=result('partial_upkeep',sim)
 sim=new();sim.stock.update(iron=10,coal=5,copper=8);sim.owned['a']['buildings']=[b('ELECTRONICS_FACTORY'),b('MACHINE_SHOP'),b('STEEL_MILL')];sim.tick();out['dependency_chain']=result('dependency_chain',sim)
 sim=new();sim.stock.update(concrete=6,stone=10);sim.owned['a']['buildings']=[b('CONCRETE_PLANT',target=7),b('LOGISTICS_CENTER',target=0),b('COMMERCIAL_DISTRICT',level=2,target=0)];sim.tick();sim.add('stone',10);sim.tick();out['logistics_target_pause']=result('logistics_target_pause',sim)
 sim=new();sim.tick();sim=copy.deepcopy(sim);sim.tick();out['unowned_reload']=result('unowned_reload',sim)
 return out

def main():
 c=load();results={name:run(c,p) for name,p in [('no_purchases',[]),('commercial_only',[('build','a','COMMERCIAL_DISTRICT')]),('progression_2_players',plan(2)),('progression_4_players',plan(4)),('stone_expansion_2_players',plan(2)[:6]+[('claim','e',None)]+plan(2)[6:]),('all_industries_to_level5',endgame_plan())]}
 results['managed_targets_2_players']=run(c,plan(2),managed=True)
 results['managed_targets_all_industries_to_level5']=run(c,endgame_plan(),managed=True)
 results['managed_targets_stone_2_players']=run(c,plan(2)[:6]+[('claim','e',None)]+plan(2)[6:],managed=True)
 # Independent boundary expectations, not merely a copy of implementation formulas.
 s=Sim(c)
 for _ in range(8):s.tick()
 assert s.stock=={'money':13000,'agriculture':32,'wood':40,'stone':40},s.stock
 s=Sim(c);s.owned['a']['producer']=4;assert s.output('a','wood')==23 # Java round, not Python bankers' round.
 assert c['costs']['build_WEAPONS_FACTORY']=={'money':3750,'wood':63,'stone':63}
 assert c['costs']['build_VEHICLE_FACTORY']=={'money':5250,'wood':88,'stone':88}
 assert len(c['guns'])==52 and len(c['ammos'])==17
 for result in results.values():
  for snap in result['snapshots'].values():
   assert all(v>=0 for v in snap['stock'].values())
   for t in snap['owned'].values():assert len(t['buildings'])<=2*t['city'] and sum(b['type'] not in ['COMMERCIAL_DISTRICT','LOGISTICS_CENTER'] for b in t['buildings'])<=3
 # Replacement demand per active hour is a scenario assumption, not a native firing model.
 sustain={}
 for players in [2,4]:
  for packs in [1,2,4]:sustain[f'{players}players_{packs}packs_each_hour']={k:v*players*packs for k,v in c['costs']['ammo_tacz:762x39'].items()}
 vectors=parity(c)
 output={'parity_vectors':vectors,'schema':1,'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'source_sha256':SOURCES,'assumptions':{'time':'active 20TPS server time, no offline/catch-up production','territory_order':'sorted territory IDs, sensitivity required if runtime iteration order differs','processor_targets':'100 except concrete/steel/mechanical/electronics1000; explicit modeled player target changes','purchases':'instant after affordable economic conditions, no construction delay, combat losses or blocked geometry','trade':'none; no monument rewards or admin grants','progression':'illustrative sequential strategy, not an optimal search or accepted match duration','weapon_buy_gates':'scripted weapon purchases occur after weapon factory; remaining catalog prices are reference comparison only'},'catalog':c,'all_building_level5_capital':{k:sum(v.get(k,0) for name,v in c['costs'].items() if name.startswith('build_') or (name.startswith('upgrade_') and not name.startswith('upgrade_base_'))) for k in {k for v in c['costs'].values() for k in v}},'scenarios':results,'ammo_sustain_per_hour':sustain,'validation':{'starter_8_cycles':True,'java_rounding_ties':True,'scaled_material_costs':True,'active52guns17ammo':True,'nonnegative_balances_and_slot_caps':True}}
 parser=argparse.ArgumentParser();parser.add_argument('--native-log',type=Path);args=parser.parse_args()
 if args.native_log:
  found={}
  for line in args.native_log.read_text().splitlines():
   if 'ECONOMY45_NATIVE_PASS {' in line:
    v=json.loads(line.split('ECONOMY45_NATIVE_PASS ',1)[1]);found[v['case']]=v
  assert found==vectors, {'native':found,'offline':vectors}
  output['validation']['actual_native_log_parity']=str(args.native_log)
 out=ROOT/'tools/economy45/results.json';out.write_text(json.dumps(output,indent=2,sort_keys=True)+'\n')
 print(json.dumps({k:{'purchases':len(v['events']),'unfinished':len(v['unfinished']),'last_event':v['events'][-1] if v['events'] else None} for k,v in results.items()},indent=2))
if __name__=='__main__':main()
