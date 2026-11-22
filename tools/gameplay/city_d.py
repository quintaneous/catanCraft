"""Replay one City D playstyle using the existing source-reading economy simulator."""
import json
from pathlib import Path
import re
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'economy45'))
import simulate as economy

# Keep offers at the player's own local factory. Claims follow actual map adjacency.
ACTIONS = (
    ('buy', 'd', 'procure_pistol'), ('buy', 'd', 'procure_ammo9'),
    ('build', 'd', 'COMMERCIAL_DISTRICT'), ('city', 'd', 2), ('producer', 'd', 2),
    ('claim', 'c', None), ('claim', 'g', None), ('claim', 'f', None),
    ('build', 'd', 'WEAPONS_FACTORY'), ('build', 'd', 'VEHICLE_FACTORY'),
    ('city', 'g', 2), ('build', 'g', 'STEEL_MILL'), ('build', 'g', 'CONCRETE_PLANT'),
    ('city', 'f', 2), ('build', 'f', 'REFINERY'),
    ('city', 'g', 3), ('build', 'g', 'MACHINE_SHOP'),
    ('city', 'f', 3), ('build', 'f', 'CHEMICAL_PLANT'), ('build', 'f', 'ELECTRONICS_FACTORY'),
    ('buy', 'd', 'tacz:ak47'), ('buy', 'd', 'ammo_tacz:762x39'),
    ('buy', 'd', 'procure_pickup'), ('buy', 'd', 'refill_pickup_quarter'),
    ('buy', 'd', 'sector_basic'), ('buy', 'd', 'sector_reinforced_increment'),
)


def route(pickup_first):
    actions = list(ACTIONS)
    if pickup_first:
        truck = ('buy', 'd', 'procure_pickup')
        refill = ('buy', 'd', 'refill_pickup_quarter')
        actions.remove(truck)
        actions.remove(refill)
        insert_at = actions.index(('build', 'g', 'MACHINE_SHOP')) + 1
        actions[insert_at:insert_at] = [truck, refill]
    return actions


def replay(managed_targets, pickup_first=False):
    actions = route(pickup_first)
    config = economy.load()
    if config['territories']['d']['startSlot'] != 2:
        raise ValueError('Recheck the City D starting slot')
    minutes = config['cycle_minutes']
    if minutes <= 0 or 200 * 60 % minutes:
        raise ValueError('Choose a whole-cycle observation window')
    tariff = re.search(r'PICKUP = new Tariff\(CATALOG, "pickup", (\d+)\)', economy.java('world/VehicleFuel49'))
    if tariff is None or int(tariff[1]) % 4:
        raise ValueError('Recheck cost of the selected exact quarter-tank refill')
    config['costs']['refill_pickup_quarter'] = {'fuel': int(tariff[1]) // 4}
    sim = economy.Sim(config)
    # The shared starter state contains TH1, producer 1 and no buildings.
    sim.owned['d'] = sim.owned.pop('a')
    events = []
    next_index = 0
    limit = 200 * 60 // minutes
    for cycle in range(limit + 1):
        while next_index < len(actions):
            step = actions[next_index]
            if not sim.act(step):
                break
            events.append({'minute': cycle * minutes, 'step': step,
                           'paid': sim.events[-1]['price'], 'agriculture': sim.stock.get('agriculture', 0)})
            next_index += 1
        if next_index == len(actions) or cycle == limit:
            break
        if managed_targets:
            economy.manage_targets(sim, actions[next_index])
        sim.tick()
    return {'managed_targets': managed_targets, 'events': events, 'next': actions[next_index:next_index + 1], 'pickup_first': pickup_first,
            'elapsed_minutes': sim.cycle * minutes, 'starved_territory_cycles': sim.starved, 'stock': sim.stock}


if __name__ == '__main__':
    print(json.dumps([replay(False), replay(True), replay(True, pickup_first=True)], indent=2))
