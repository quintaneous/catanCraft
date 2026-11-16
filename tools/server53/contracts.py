"""Pinned release inputs and evidence gates; validation is not native execution."""
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from prepare_fresh_demo import InputError, read_regular, sha256

PROGRESSION_MODE = 'verified-clean-r7-protected-map-progression53'
ORIGINAL_MANIFEST_SHA = 'e7ecd5d0f0ac5c1457be5c234d2e5e08c9239007787b20e0bdc45b3e2b8d862d'
FORGE_INSTALLER_SHA = '1912760b4cb6b803d8a826de603c9076b1da71ec2765e9a1f8c1ca78f65278e3'
WINDOWS_JAVA_ARCHIVE_SHA = 'fe4656bd78fb09812dc38c49da777f79cc33e7ee435a254be82f9e7f40d9d117'
PUBLISHED_MODS = {
 'tacz-forge-1.20.1-1.2.0.jar': '1c2f2915f76908d396a0da577665c977564c279ccb89d459a00a29225aa45a92',
 'superbwarfare-0.8.9.2-mc1.20.1-65d985a64-all.jar': '559930003f638080de648a8621821d785158768c3f69a762094ef4c8d3c4cc12',
 'kotlinforforge-4.11.0-all.jar': 'ef988f86d170af499d147ea8a5c34c99dbd6d8f4a5ad236a4b43818dc4470a10',
}
ALL_CITY_APPROVAL = {'cities':list('abcdefghijklmnop'), 'sectors':['north','east','south','west'],
                     'structuralTiers':[1,2], 'ownerAcceptance':False}


def evidence_files(root, report):
    from package_authored_demo import relative, digest
    result = {}
    for section in ('serverWideValidation', 'automatedValidation'):
        evidence = report.get(section, {}).get('nativeEvidence', [])
        if not isinstance(evidence, list) or len(evidence) > 256:
            raise InputError('Invalid bounded native evidence list')
        for row in evidence:
            name = relative(row['source'])
            data = read_regular(root / name)
            if sha256(data) != digest(row['sha256']):
                raise InputError('Native evidence checksum mismatch: ' + name)
            result[name] = data
    return result


def readiness(report, commit, fixture):
    """Check explicit aggregate contract only; human review of logs is still required."""
    s = report.get('serverWideValidation', {})
    a = report.get('automatedValidation', {})
    r = a.get('retainedRegression', {})
    return (not fixture and report.get('fixtureOnly') is False
        and s.get('schema') == 1 and s.get('status') == 'PASS'
        and s.get('cities') == list('abcdefghijklmnop') and s.get('wallSectors') == 64
        and s.get('structuralCombinationsPerCity') == 81 and s.get('ownerAcceptance') is False
        and bool(s.get('nativeEvidence')) and bool(a.get('nativeEvidence'))
        and a.get('schema') == 1 and a.get('status') == 'PASS' and a.get('sourceCommit') == commit
        and a.get('authoredWalls') == {'cities':16,'sectors':64,'purchases':128,'seedPassed':True,'restartPassed':True}
        and a.get('progression') == {'layouts':9,'cityTierCheckpoints':720,'factoryTypeSitePairs':432,
             'paidFactoryLevels':[1,2,3,4,5],'restartCityChecks':144}
        and type(r.get('executed')) is int and r['executed'] > 0
        and r.get('passed') == r['executed'] and r.get('failed') == 0
        and a.get('combinedPublishedRuntime') is True)
