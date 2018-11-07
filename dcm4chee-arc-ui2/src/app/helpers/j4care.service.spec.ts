import {j4care} from "./j4care.service";

describe('j4care', () => {
    it("Should set 0 as prefix if only one digit available: setZeroPrefix()",()=>{
        expect(j4care.setZeroPrefix('1')).toBe('01');
        expect(j4care.setZeroPrefix('12')).toBe('12');
        expect(j4care.setZeroPrefix(1)).toBe('01');
        expect(j4care.setZeroPrefix('123')).toBe('123');
        expect(j4care.setZeroPrefix('')).toBe('');
        expect(j4care.setZeroPrefix('0')).toBe('00');
        expect(j4care.setZeroPrefix(undefined)).toBe(undefined);
    });

    it("Should return difference of two dates as HH:mm:ss:SSS",()=>{
        expect(j4care.diff(new Date("2018-11-01T12:32:01.582+02:00"), new Date("2018-11-01T12:42:03.582+02:00"))).toBe("00:10:02.0");
        expect(j4care.diff(new Date("2018-11-01T12:02:01.582+02:00"), new Date("2018-11-01T12:42:03.342+02:00"))).toBe("00:40:01.760");
        expect(j4care.diff(new Date("2018-11-01T12:32:01.582+02:00"), new Date("2018-11-01T12:22:03.582+02:00"))).toBe('');
        expect(j4care.diff(new Date("2018-11-01T10:32:01.582+02:00"), new Date("2018-11-01T12:22:03.582+02:00"))).toBe("01:50:02.0");
    });

    it("Should format date",()=>{
        expect(j4care.formatDate(new Date("2018-11-01T12:32:01.582+01:00"), 'yyyy.MM.dd HH:mm:ss.SSS')).toBe("2018.11.01 12:32:01.582");
        expect(j4care.formatDate(new Date("2018-11-01T13:32:40.582+01:00"), 'HH:mm:ss.SSS')).toBe("13:32:40.582");
        expect(j4care.formatDate(new Date("2018-11-03T02:04:05.582+01:00"), 'HH:mm')).toBe("02:04");
        expect(j4care.formatDate(new Date("2018-02-03T02:04:05.582+01:00"), 'yyyyMMdd')).toBe("20180203");
    })
});