"""Independent raw native-driving observation audit; no route qualification inferred."""
from __future__ import annotations
import hashlib
import json
import math
from pathlib import Path
import re
import uuid

NS = 'catancraft_vehicle48'
RUNTIME = '6e4424ef5a564609dc893c5d9d1ff3efcc7b897d8b9ae9a9acf96d7c331c9452'
CASES = [('sodayo_pick_up', 0), ('sodayo_pick_up', 180), ('lav_150', 0), ('lav_150', 180)]

def require(condition, message):
    if not condition:
        raise ValueError(message)

def number(value):
    return type(value) in (int, float) and math.isfinite(value)

def close(a, b):
    return number(a) and number(b) and math.isclose(a, b, rel_tol=1e-7, abs_tol=1e-4)

def pairs(items):
    result = {}
    for key, value in items:
        require(key not in result, 'Duplicate JSON field ' + key)
        result[key] = value
    return result

def invalid_constant(value):
    raise ValueError('Nonfinite JSON constant ' + value)

def verify_driving(evidence, source):
    evidence = Path(evidence)
    require(re.fullmatch('[0-9a-f]{40}', source) is not None, 'Exact source required')
    directory = evidence / 'native' / NS / 'data'
    files = sorted(directory.glob('catancraft_vehicle48_*.json'))
    require([p.name for p in files] == [f'catancraft_vehicle48_{n}.json' for n in range(4)], 'Missing/foreign driving evidence')
    log = (evidence / (NS + '.log')).read_text()
    markers = re.findall(r'VEHICLE48_DRIVE_PASS case=(\d+) vehicle=(\w+) rotation=(\d+) samples=(\d+) distance=(\S+) heading=(\S+) stop=(\w+) fullBayQualified=false', log)
    require(len(markers) == 4 and {int(r[0]) for r in markers} == set(range(4)), 'Missing/duplicate driving markers')
    marker_by_case = {int(r[0]): r for r in markers}
    identities, results = set(), []
    for index, path in enumerate(files):
        blob = path.read_bytes()
        require(0 < len(blob) <= 4 * 1024 * 1024, 'Driving evidence size out of bounds')
        p = json.loads(blob, object_pairs_hook=pairs, parse_constant=invalid_constant)
        vehicle, rotation = CASES[index]
        require(p['source'] == source and p['namespace'] == NS and p['runtimeSHA256'] == RUNTIME, 'Foreign source/runtime/fixture')
        require((p['case'], p['vehicle'], p['rotation']) == (index, vehicle, rotation), 'Wrong case/vehicle/orientation')
        require((p['anchorX'], p['anchorY'], p['anchorZ']) == (4096 + index * 160, 100, 4096), 'Foreign authored fixture anchor')
        require(p['result'] == 'PASS' and p['failure'] == '', 'Driving scenario did not pass')
        require(p['controlPath'] == 'VehicleMovementMessage.handler' and p['naturalServerTicks'] is True and p['postSpawnPoseWrites'] == 0, 'Foreign movement path')
        require(p['fullBayQualified'] is False, 'Observation promoted to bay qualification')
        require(p['stagedCells'] == p['sourceCellsUnchanged'] == 18400, 'Authored source preservation incomplete')
        sign = 1 if rotation == 0 else -1
        limits = [(p['anchorX'] + min(-29*sign, 10*sign), p['anchorX'] + max(-29*sign, 10*sign) + 1), (99, 108), (p['anchorZ'] + min(-22*sign, 23*sign), p['anchorZ'] + max(-22*sign, 23*sign) + 1)]
        require(p['stop'] in ('AUTHORED_ENVELOPE_BOUNDARY', 'BOUNDED_STEERING_OBSERVATION', 'TICK_LIMIT'), 'Unknown observation endpoint')
        for field in ('entityUUID', 'driverUUID'):
            identity = str(uuid.UUID(p[field]))
            require(identity == p[field] and identity != str(uuid.UUID(int=0)) and identity not in identities, 'Invalid/reused native identity')
            identities.add(identity)
        rows = p['trajectory']
        require(isinstance(rows, list) and 22 <= len(rows) <= 301, 'Unbounded/incomplete trajectory')
        first, last = rows[0], rows[-1]
        require(close(first['x'], p['anchorX'] + .5 + (-12 if rotation == 0 else 12)) and close(first['y'], 100.1) and close(first['z'], 4096.5), 'Original spawn differs')
        require(close(first['yaw'], -90 if rotation == 0 else 90), 'Original heading differs')
        require(type(p['initialEnergy']) is int and 0 < p['initialEnergy'] <= 1000000 and first['energy'] == p['initialEnergy'], 'Native energy fixture differs')
        turning, turn_yaw, turn_ticks = False, None, 0
        distances, headings, steering, path_length = [], [], [], 0.0
        previous = None
        for tick, row in enumerate(rows):
            require(type(row['tick']) is int and row['tick'] == tick, 'Noncontiguous observer ticks')
            for field in ('entityTick', 'gameTime', 'energy'):
                require(type(row[field]) is int and row[field] >= 0, 'Invalid native integer ' + field)
            for field in ('x', 'y', 'z', 'yaw', 'pitch', 'roll', 'vx', 'vy', 'vz', 'speed'):
                require(number(row[field]), 'Nonfinite native sample ' + field)
            require(row['turning'] is turning, 'Recorded steering phase disagrees with trajectory')
            require(close(row['speed'], math.hypot(row['vx'], row['vz'])), 'Native velocity/speed mismatch')
            distance = math.hypot(row['x'] - first['x'], row['z'] - first['z'])
            if tick <= 20:
                require(distance < .1, 'Neutral control moved horizontally before propulsion')
            for field in ('onGround', 'horizontalCollision', 'verticalCollision'):
                require(type(row[field]) is bool, 'Invalid native collision flag')
            bounds = row['nativeBounds']
            require(isinstance(bounds, list) and len(bounds) == (8 if vehicle == 'sodayo_pick_up' else 10), 'Native OBB inventory changed')
            outside = False
            for box in bounds:
                require(isinstance(box, list) and len(box) == 6 and all(number(v) for v in box) and all(box[n] < box[n+3] for n in range(3)), 'Invalid native OBB bounds')
                outside |= any(not (limits[n][0] <= box[n] < limits[n][1] and limits[n][0] <= box[n+3] < limits[n][1]) for n in range(3))
            require(not outside or tick == len(rows)-1, 'Observer continued outside bounded authored fixture')
            if tick == len(rows)-1:
                require(outside == (p['stop'] == 'AUTHORED_ENVELOPE_BOUNDARY'), 'Declared boundary stop differs from raw OBB evidence')
            distances.append(distance)
            headings.append(abs(math.remainder(row['yaw'] - first['yaw'], 360)))
            if turning:
                steering.append(abs(math.remainder(row['yaw'] - turn_yaw, 360)))
            if previous is not None:
                require(row['gameTime'] == previous['gameTime'] + 1, 'Samples did not follow consecutive server ticks')
                require(0 <= row['entityTick'] - previous['entityTick'] <= 1, 'Native entity tick skipped or doubled')
                require(row['energy'] <= previous['energy'], 'Energy was refilled during trajectory')
                path_length += math.hypot(row['x'] - previous['x'], row['z'] - previous['z'])
            if tick == len(rows) - 1:
                require('keys' not in row, 'Terminal observation sent additional movement')
            else:
                if not turning and distance >= 12:
                    turning, turn_yaw = True, row['yaw']
                if turning:
                    turn_ticks += 1
                require(turn_ticks < 60 and tick < 300, 'Movement continued beyond bounded observer endpoint')
                require(type(row['keys']) is int and row['keys'] == (0 if tick < 20 else 5 if turning else 4), 'Native input schedule differs')
            previous = row
        require(last['entityTick'] - first['entityTick'] >= len(rows) - 6, 'Insufficient genuine entity ticks')
        require(max(distances) > 2 and turn_yaw is not None and close(turn_yaw, p['turnYaw']), 'Native forward/turn transition missing')
        require(steering and max(steering) > 5 and close(max(steering), p['maxSteering']), 'Native steering was not independently observed')
        require(last['energy'] < p['initialEnergy'], 'Powered observation consumed no native energy')
        if p['stop'] == 'BOUNDED_STEERING_OBSERVATION':
            require(turn_ticks == 59, 'Wrong steering observation duration')
        if p['stop'] == 'TICK_LIMIT':
            require(last['tick'] == 300, 'Wrong terminal tick limit')
        marker = marker_by_case[index]
        require((marker[1], int(marker[2]), int(marker[3]), marker[6]) == (vehicle, rotation, len(rows), p['stop']), 'Marker differs from raw observation')
        require(close(float(marker[4]), max(distances)) and close(float(marker[5]), max(headings)), 'Marker trajectory metrics differ')
        results.append(dict(case=index, vehicle=vehicle, rotation=rotation, samples=len(rows), stop=p['stop'], maximum_displacement=max(distances), horizontal_path_length=path_length, maximum_heading_change=max(headings), maximum_steering_change=max(steering), energy_consumed=p['initialEnergy'] - last['energy'], entity_ticks=last['entityTick'] - first['entityTick'], entity_uuid=p['entityUUID'], driver_uuid=p['driverUUID'], evidence_sha256=hashlib.sha256(blob).hexdigest(), full_bay_qualified=False))
    return dict(result='PASS', source=source, runtime_manifest_sha256=RUNTIME, observations=results, full_bay_qualified=False, scope='Four bounded native movement observations; not road-route completion, production activation or authenticated-client acceptance')
