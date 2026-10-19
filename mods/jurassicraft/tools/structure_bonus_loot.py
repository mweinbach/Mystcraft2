"""Dynamic 1.12 bonus pools stored directly in the port's structure tables.

Forge's loot-load event is not fired for resources supplied by mod packs.
Keep these pools in the data so they load alongside the original room loot.
"""


def _uniform(low, high):
    return {'type': 'minecraft:uniform', 'min': low, 'max': high}


def _dynamic(kind, low=None, high=None, weight=1):
    entry = {'type': 'jurassicraft:dynamic_bonus', 'kind': kind}
    if weight != 1:
        entry['weight'] = weight
    if low is not None:
        entry['functions'] = [{'function': 'minecraft:set_count', 'count': _uniform(low, high)}]
    return entry


def structure_bonus_pool(name):
    """Return a fresh named pool for a visitor room or the fossil digsite."""
    if name == 'ground_storage':
        rolls, entries = (5, 6), [_dynamic('amber', 0, 3), _dynamic('wool', 0, 64)]
    elif name == 'laboratory':
        rolls, entries = (3, 4), [_dynamic('dinosaur_dna'), _dynamic('animal_tissue', 0, 3),
                                  _dynamic('plant_tissue', 0, 3), _dynamic('amber', 0, 5)]
    elif name == 'dining_hall':
        rolls, entries = (8, 11), [_dynamic('amber', 0, 1, 2), _dynamic('bone', 1, 2, 2),
                                   _dynamic('action_figure')]
    elif name == 'kitchen':
        rolls, entries = (3, 4), [{'type': 'minecraft:item', 'name': 'minecraft:potion', 'functions': [
            {'function': 'minecraft:set_count', 'count': _uniform(0, 1)},
            {'function': 'minecraft:set_potion', 'id': 'minecraft:water'}]}]
    elif name == 'fossil_digsite':
        rolls, entries = (6, 9), [_dynamic('bone', 1, 1), _dynamic('bone', 1, 2, 2)]
    else:
        return None
    return {'name': 'jurassicraft_bonus', 'rolls': _uniform(*rolls), 'entries': entries}
