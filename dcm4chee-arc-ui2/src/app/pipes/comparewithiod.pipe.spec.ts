/* tslint:disable:no-unused-variable */

import { TestBed, waitForAsync } from '@angular/core/testing';
import { ComparewithiodPipe } from './comparewithiod.pipe';

describe('Pipe: Comparewithiod', () => {
    let pipe: ComparewithiodPipe;
    let iod: any;
    let obj: any;
    let ergebnis: any;
    let rest:any;

    beforeEach(() => {
        pipe = new ComparewithiodPipe();
        iod = {
            '00080005': { 'vr': 'CS', 'multi': true },
            '00080020': { 'vr': 'DA', 'required': 2 },
            '00080030': { 'vr': 'TM', 'required': 2 },
            '00080050': { 'vr': 'SH', 'required': 2 },
            '00080051': { 'vr': 'SQ',
                'items': {
                    '00400031': { 'vr': 'UT' },
                    '00400032': { 'vr': 'UT' },
                    '00400033': { 'vr': 'CS' }
                }
            },
            '00080090': { 'vr': 'PN', 'required': 2 },
            '00081030': { 'vr': 'LO' },
        };
        obj = {
            '00080005': { 'vr': 'CS', 'Value': ['test'] },
            '00080020': { 'vr': 'DA', 'Value': ['test2'] },
            '00080030': { 'vr': 'TM', 'Value': ['test3'] },
            '00080031': { 'vr': 'TM', 'Value': ['test4'] },
            '00080033': { 'vr': 'TM', 'Value': ['test5'] }
        };
        ergebnis = {
            '00080005': { 'vr': 'CS', 'Value': ['test'] },
            '00080020': { 'vr': 'DA', 'Value': ['test2'] },
            '00080030': { 'vr': 'TM', 'Value': ['test3'] }
        };
        rest = {
            '00080031': { 'vr': 'TM', 'Value': ['test4'] },
            '00080033': { 'vr': 'TM', 'Value': ['test5'] }
        }
    });

    it('should remove all elements that are not in iod', () => {
        let result = pipe.transform(obj, iod);
        expect(result).toEqual(ergebnis);
    });
    it('should return two objects as tuple, one of them should contain only keys and objects that are also in ' +
        'iod and the other only the keys and objects that are not in iod', () => {
        let result = pipe.transform(obj, [iod, "both"]);
        expect(result).toEqual([ergebnis,rest]);
    });
    it('should return only the object with the keys that are not in iod', () => {
        let result = pipe.transform(obj, [iod, "rest-only"]);
        expect(result).toEqual(rest);
    });
});
