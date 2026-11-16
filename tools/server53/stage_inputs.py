#!/usr/bin/env python3
"""Stage pinned pristine authored inputs and supplied official runtime; no downloads/execution."""
from __future__ import annotations
import argparse
from io import BytesIO
import json
import os
from pathlib import Path
import sys
import uuid
import zipfile
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from package_authored_demo import relative, digest, R7_ROOT, R7_NAME
from prepare_fresh_demo import InputError, checked_path, read_regular, inspect_jar, sha256
from progression_ladder import encoded
from server53.contracts import *
from server53.world_profile import PROFILE_ID


def manifest_rows(root, manifest, pin, purpose):
    raw = read_regular(manifest)
    if sha256(raw) != digest(pin):
        raise InputError('Runtime manifest checksum mismatch')
    m = json.loads(raw)
    if m.get('schema') != 1 or m.get('purpose') != purpose:
        raise InputError('Unsupported runtime manifest contract')
    rows = m['files']
    if not isinstance(rows, list) or not rows:
        raise InputError('Exact runtime inventory required')
    seen = set()
    result = {}
    for row in rows:
        name = relative(row['path'])
        if name.casefold() in seen:
            raise InputError('Duplicate/case-colliding runtime files')
        seen.add(name.casefold())
        data = read_regular(root / name)
        if len(data) != row['bytes'] or sha256(data) != digest(row['sha256']):
            raise InputError('Runtime file checksum/size mismatch: ' + name)
        result[name] = data
    return m, result


def stage(args):
    original_root = checked_path(args.original_root, directory=True)
    raw = read_regular(original_root / 'INPUT_MANIFEST.json')
    if sha256(raw) != ORIGINAL_MANIFEST_SHA:
        raise InputError('Only the verified pristine R7 original manifest is accepted')
    original = json.loads(raw)
    original_pins = {row['path']:row['sha256'] for row in original['files']}
    for row in original['files']:
        data = read_regular(original_root / relative(row['path']))
        if len(data) != row['bytes'] or sha256(data) != row['sha256']:
            raise InputError('Pristine original input changed: ' + row['path'])
    jar = read_regular(args.jar)
    if sha256(jar) != digest(args.jar_sha256):
        raise InputError('Final JAR checksum mismatch')
    metadata = inspect_jar(jar)
    evidence_raw = read_regular(args.evidence)
    if sha256(evidence_raw) != digest(args.evidence_sha256):
        raise InputError('Source evidence checksum mismatch')
    evidence = json.loads(evidence_raw)
    if evidence.get('kind') != 'matching-runtime-evidence' or evidence.get('sourceCommit') != args.source_commit or evidence.get('jarSHA256') != args.jar_sha256:
        raise InputError('Evidence must match final source and JAR')
    with zipfile.ZipFile(BytesIO(jar)) as z:
        mf = z.read('META-INF/MANIFEST.MF').decode().replace('\r\n ', '').replace('\n ', '')
    if 'CatanCraft-Source-Commit: ' + args.source_commit not in mf.splitlines():
        raise InputError('Final JAR source commit does not match')
    components = {'catancraft.jar': jar, 'original-source-evidence.json': evidence_raw,
                  'original-INPUT_MANIFEST.json': raw}
    for section in ('serverWideValidation', 'automatedValidation'):
        rows = evidence.get(section, {}).get('nativeEvidence', [])
        if not isinstance(rows, list) or len(rows) > 256:
            raise InputError('Invalid native evidence list')
        for i, row in enumerate(rows):
            data = read_regular(args.evidence.parent / relative(row['source']))
            if sha256(data) != digest(row['sha256']):
                raise InputError('Native evidence checksum mismatch')
            name = f'evidence/{section}-native-{i:03d}-' + Path(row['source']).name
            components[name] = data
            row['source'] = name
    components['source-evidence.json'] = encoded(evidence)
    forge_m, forge = manifest_rows(checked_path(args.forge_root,directory=True), args.forge_manifest,
        args.forge_manifest_sha256, 'official-forge-dedicated-runtime-inputs-only')
    if forge_m.get('minecraft') != '1.20.1' or forge_m.get('forge') != '47.4.10' or forge_m.get('installerSHA256') != FORGE_INSTALLER_SHA:
        raise InputError('Wrong pinned Forge installer/runtime')
    if len(forge) != 104:
        raise InputError('Complete reviewed 104-file Forge runtime required')
    if any(not name.startswith('libraries/') for name in forge):
        raise InputError('Forge inputs must be library files')
    components.update(forge)
    full = {'schema':1, 'mods':[], 'forgeLibraries':[], 'windowsJava':[]}
    for name,data in forge.items():
        full['forgeLibraries'].append({'source':name,'target':name,'sha256':sha256(data)})
    for name,pin in PUBLISHED_MODS.items():
        folder = args.tacz_root if name.startswith('tacz-') else args.sbw_root
        data = read_regular(checked_path(folder,directory=True) / 'inputs' / name)
        if sha256(data) != pin:
            raise InputError('Published mod changed: ' + name)
        target = 'mods/' + name
        components[target] = data
        full['mods'].append({'source':target,'target':target,'sha256':pin})
    java_options = (args.windows_java_root,args.windows_java_manifest,args.windows_java_manifest_sha256)
    if any(java_options) and not all(java_options):
        raise InputError('Supply all three Windows Java root/manifest/checksum options')
    if all(java_options):
        jm, java = manifest_rows(checked_path(args.windows_java_root,directory=True), args.windows_java_manifest,
            args.windows_java_manifest_sha256,'official-temurin17-windows-runtime-inputs-only')
        if jm.get('archiveSHA256') != WINDOWS_JAVA_ARCHIVE_SHA:
            raise InputError('Wrong pinned Windows Java archive')
        if len(java) != 316 or 'bin/java.exe' not in java:
            raise InputError('Complete reviewed 316-file Windows Java runtime required')
        full['windowsJavaArchiveSHA256'] = WINDOWS_JAVA_ARCHIVE_SHA
        for name,data in java.items():
            target = 'runtime/java17/' + name
            components[target] = data
            full['windowsJava'].append({'source':target,'target':target,'sha256':sha256(data)})
    world = original_root / 'source_world' / R7_ROOT
    expected = {'level.dat'} | {f'region/r.{x}.{z}.mca' for x in range(-2,2) for z in range(-2,2)}
    actual = set()
    for p in world.rglob('*'):
        if p.is_symlink(): raise InputError('Pristine world redirect forbidden')
        if p.is_file(): actual.add(p.relative_to(world).as_posix())
    if actual != expected: raise InputError('Pristine world inventory changed')
    world_pins = {}
    stream = BytesIO()
    with zipfile.ZipFile(stream,'w',compression=zipfile.ZIP_DEFLATED) as z:
        for name in sorted(expected):
            data = read_regular(world/name)
            world_pins[name] = sha256(data)
            if world_pins[name] != original_pins['source_world/'+R7_ROOT+'/'+name]:
                raise InputError('Pristine world changed during staging')
            info = zipfile.ZipInfo(R7_ROOT+'/'+name, (1980,1,1,0,0,0))
            info.external_attr = 0o100644 << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info,data)
    components['pristine-world.zip'] = stream.getvalue()
    configs, assets = [], []
    for row in original['files']:
        prefix = 'bootstrap_reference/config/'
        if row['path'].startswith(prefix):
            target = 'config/' + row['path'][len(prefix):]
            components[target] = read_regular(original_root / row['path'])
            entry = {'source':target,'target':target,'sha256':row['sha256']}
            (configs if target.endswith('/map.json') else assets).append(entry)
    m = {'schemaVersion':1,'packageKind':'catancraft-authored-demo-input','fixtureOnly':False,
      'approval':{'freshAuthoredWorld':True,'bootstrapReviewed':True,'sectorArtEnabled':args.sector_art_enabled},
      'sourceCommit':args.source_commit,'runtime':{'javaMajor':17,'minecraft':'1.20.1','forge':'47.4.10'},
      'world':{'source':'pristine-world.zip','sha256':sha256(components['pristine-world.zip']), 'root':R7_ROOT,
               'levelName':R7_NAME,'seed':1042026,'regionFiles':16,'files':world_pins},
      'bootstrap':{'mode':PROGRESSION_MODE,'metadataFiles':[], 'authorization':{'worldId':str(uuid.uuid4()),
                    'mapSha256':next(x['sha256'] for x in configs if x['target']=='config/catancraft/map.json'),
                    'bootstrapRevision':PROFILE_ID}},
      'jar':{'source':'catancraft.jar','sha256':args.jar_sha256,'version':metadata['version']},
      'sourceEvidence':{'source':'source-evidence.json','sha256':sha256(components['source-evidence.json'])},
      'config':configs,'assets':assets,'fullRuntime':full,
      'forgeRuntimeFiles':{name:sha256(data) for name,data in forge.items()},
      'provenance':{'originalManifestSHA256':ORIGINAL_MANIFEST_SHA,'sourceEvidenceOriginalSHA256':args.evidence_sha256,
                    'forgeManifestSHA256':args.forge_manifest_sha256,'noServerExecuted':True,'eulaAccepted':False}}
    if args.sector_art_enabled: m['sectorApproval'] = ALL_CITY_APPROVAL
    components['INPUT_MANIFEST.json'] = encoded(m)
    output = Path(os.path.abspath(args.output))
    checked_path(output.parent,directory=True)
    if output.exists() or output.is_symlink() or output == original_root or original_root in output.parents or any(p.lower() in {'mods','saves'} for p in output.parts):
        raise InputError('Use an isolated NEW staging destination')
    output.mkdir()
    for name,data in sorted(components.items()):
        p = output / relative(name)
        p.parent.mkdir(parents=True,exist_ok=True)
        with p.open('xb') as out: out.write(data)
        if sha256(read_regular(p)) != sha256(data): raise InputError('Staging readback mismatch')
    return {'manifestSHA256':sha256(components['INPUT_MANIFEST.json']), 'files':len(components), 'serverExecuted':False}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('original-root','jar','evidence','forge-root','forge-manifest','tacz-root','sbw-root','output'):
        p.add_argument('--'+name,type=Path,required=True)
    for name in ('jar-sha256','evidence-sha256','forge-manifest-sha256','source-commit'):
        p.add_argument('--'+name,required=True)
    p.add_argument('--windows-java-root',type=Path)
    p.add_argument('--windows-java-manifest',type=Path)
    p.add_argument('--windows-java-manifest-sha256')
    p.add_argument('--sector-art-enabled',action='store_true')
    try: print(json.dumps(stage(p.parse_args()),indent=2))
    except (InputError,OSError,ValueError,KeyError,TypeError) as ex:
        print('REFUSED: '+str(ex),file=sys.stderr); return 2
    return 0
if __name__=='__main__': sys.exit(main())
