import unittest

from verify import V1_PINS, block_counts, compare_v1, fit, protected_content


class ReviewValidationTests(unittest.TestCase):
    def test_varint_ids_and_volume(self):
        self.assertEqual(block_counts(bytes([0, 129, 1, 0]), {0, 129}, 3), {0:2,129:1})
        for raw, volume in [(bytes([1]),1), (bytes([128]),1), (bytes([128,0]),1),
                            (bytes([128]*5),1), (bytes([0,0]),1)]:
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                block_counts(raw,{0},volume)

    def test_nested_item_or_loot_payload(self):
        for key in ('Items','LootTable','Command','Passengers','SpawnData'):
            with self.subTest(key=key), self.assertRaises(ValueError):
                protected_content({'Metadata':[{'Compound':{key:'unexpected'}}]})

    def test_rectangular_rotated_box_edge(self):
        asset = {'sizeXYZ':[55,50,47]}
        site = {'id':'light','centerXZ':[-1410,-430],'boundsXZ':[-1458,-478,-1362,-382],'approachFrom':'east'}
        self.assertEqual(fit(asset,site,[27,8,23],180)['assetBoundsXZHalfOpen'], [-1437,-453,-1382,-406])
        with self.assertRaises(ValueError):
            fit(asset,dict(site,boundsXZ=[-1436,-478,-1362,-382]),[27,8,23],180)

    def test_enlarged_wreck_mount(self):
        result = fit({'sizeXYZ':[88,40,54]}, {'id':'wreck_east','centerXZ':[1390,280],
                'boundsXZ':[1342,232,1438,328],'approachFrom':'west'},[44,15,27],0)
        self.assertEqual(result['assetBoundsXZHalfOpen'],[1346,253,1434,307])
        self.assertEqual(result['assetBoundsYHalfOpen'],[60,100])
        self.assertEqual(result['clearanceToReservationEdgeBlocks'],4)

    def test_only_wreck_can_change(self):
        assets = {}
        for path, pin in V1_PINS.items():
            group,name=path.split('/')
            assets.setdefault(group,[]).append({'file':name,'sha256':pin})
        with self.assertRaises(ValueError): compare_v1(assets)
        assets['wreck'][0]['sha256']='a'*64
        self.assertFalse(compare_v1(assets)['wreck/brinewake.schem']['byteIdentical'])
        assets['coast'][0]['sha256']='a'*64
        with self.assertRaises(ValueError): compare_v1(assets)

    def test_mainland_and_border(self):
        asset = {'sizeXYZ':[1,1,1]}
        for x in (-1024, 1023, -1601, 1600):
            site = {'id':'sample','centerXZ':[x,0],'boundsXZ':[x,0,x+1,1],'approachFrom':'west'}
            with self.subTest(x=x), self.assertRaises(ValueError):
                fit(asset,site,[0,0,0],0)

if __name__ == '__main__':
    unittest.main()
