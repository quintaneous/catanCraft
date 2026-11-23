import unittest

from verify import block_counts, fit, protected_content


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

    def test_mainland_and_border(self):
        asset = {'sizeXYZ':[1,1,1]}
        for x in (-1024, 1023, -1601, 1600):
            site = {'id':'sample','centerXZ':[x,0],'boundsXZ':[x,0,x+1,1],'approachFrom':'west'}
            with self.subTest(x=x), self.assertRaises(ValueError):
                fit(asset,site,[0,0,0],0)

if __name__ == '__main__':
    unittest.main()
