import {j4care} from "./j4care.service";

const AETS1 = [{
        "dicomAETitle": "DCM4CHEE",
        "dicomDescription": "Hide instances rejected for Quality Reasons",
    }, {
        "dicomAETitle": "IOCM_EXPIRED",
        "dicomDescription": "Only show instances rejected for Data Retention Expired",
    }];

const AETS2 = [{
        "dicomAETitle": "DCM4CHEE",
        "dicomDescription": "Hide instances rejected for Quality Reasons",
        "dcmAcceptedUserRole": ["user", "admin"],
    }, {
        "dicomAETitle": "IOCM_EXPIRED",
        "dicomDescription": "Only show instances rejected for Data Retention Expired",
        "dcmAcceptedUserRole": ["user", "admin"],
    }, {
        "dicomAETitle": "IOCM_PAT_SAFETY",
        "dicomDescription": "Only show instances rejected for Patient Safety Reasons",
        "dcmAcceptedUserRole": ["admin"],
    }, {
        "dicomAETitle": "IOCM_QUALITY",
        "dicomDescription": "Only show instances rejected for Quality Reasons",
        "dcmAcceptedUserRole": ["admin"],
    }, {
        "dicomAETitle": "IOCM_REGULAR_USE",
        "dicomDescription": "Show instances rejected for Quality Reasons",
        "dcmAcceptedUserRole": ["admin"],
    }, {
        "dicomAETitle": "IOCM_WRONG_MWL",
        "dicomDescription": "Only show instances rejected for Incorrect Modality Worklist Entry",
        "dcmAcceptedUserRole": ["admin"],
    }, {"dicomAETitle": "TESCACHE"}
];

describe('j4care', () => {
    it("Should set 0 as prefix if only one digit available: setZeroPrefix()", () => {
        expect(j4care.setZeroPrefix('1')).toBe('01');
        expect(j4care.setZeroPrefix('12')).toBe('12');
        expect(j4care.setZeroPrefix(1)).toBe('01');
        expect(j4care.setZeroPrefix('123')).toBe('123');
        expect(j4care.setZeroPrefix('')).toBe('');
        expect(j4care.setZeroPrefix('0')).toBe('00');
        expect(j4care.setZeroPrefix(undefined)).toBe(undefined);
    });

    it("Should return difference of two dates as HH:mm:ss:SSS", () => {
        expect(j4care.diff(new Date("2018-11-01T12:32:01.582+02:00"), new Date("2018-11-01T12:42:03.582+02:00"))).toBe("00:10:02.0");
        expect(j4care.diff(new Date("2018-11-01T12:02:01.582+02:00"), new Date("2018-11-01T12:42:03.342+02:00"))).toBe("00:40:01.760");
        expect(j4care.diff(new Date("2018-11-01T12:32:01.582+02:00"), new Date("2018-11-01T12:22:03.582+02:00"))).toBe('');
        expect(j4care.diff(new Date("2018-11-01T10:32:01.582+02:00"), new Date("2018-11-01T12:22:03.582+02:00"))).toBe("01:50:02.0");
        expect(j4care.diff(new Date("2018-11-30T11:56:13.862+02:00"), new Date("2018-11-30T11:56:47.683+0200"))).toBe("00:00:33.821");
    });

    it("Should format date", () => {
        expect(j4care.formatDate(new Date("2018-11-01T12:32:01.582+01:00"), 'yyyy.MM.dd HH:mm:ss.SSS')).toBe("2018.11.01 12:32:01.582");
        expect(j4care.formatDate(new Date("2018-11-01T13:32:40.582+01:00"), 'HH:mm:ss.SSS')).toBe("13:32:40.582");
        expect(j4care.formatDate(new Date("2018-11-03T02:04:05.582+01:00"), 'HH:mm')).toBe("02:04");
        expect(j4care.formatDate(new Date("2018-02-03T02:04:05.582+01:00"), 'yyyyMMdd')).toBe("20180203");
    });

    it("Should get the main aet", () => {
        expect(j4care.getMainAet(AETS1)).toEqual([{
            "dicomAETitle": "DCM4CHEE",
            "dicomDescription": "Hide instances rejected for Quality Reasons",
        }]);
        expect(j4care.getMainAet(AETS2)).toEqual([{
            "dicomAETitle": "DCM4CHEE",
            "dicomDescription": "Hide instances rejected for Quality Reasons",
            "dcmAcceptedUserRole": ["user", "admin"],
        }]);
        expect(j4care.getMainAet(AETS2.slice(1, 3))).toEqual([{
            "dicomAETitle": "IOCM_EXPIRED",
            "dicomDescription": "Only show instances rejected for Data Retention Expired",
            "dcmAcceptedUserRole": ["user", "admin"],
        }]);
        expect(j4care.getMainAet(AETS2.slice(2, 5))).toEqual([{
            "dicomAETitle": "IOCM_PAT_SAFETY",
            "dicomDescription": "Only show instances rejected for Patient Safety Reasons",
            "dcmAcceptedUserRole": ["admin"],
        }])
        expect(j4care.getMainAet(AETS2.reverse())).toEqual([{
            "dicomAETitle": "IOCM_EXPIRED",
            "dicomDescription": "Only show instances rejected for Data Retention Expired",
            "dcmAcceptedUserRole": ["user", "admin"],
        }])
    })

    it("Should return date range as object from string extractDateTimeFromString(input:string)",()=>{
        expect(j4care.extractDateTimeFromString('20181012-20181130')).toEqual({
            mode:"range",
            firstDateTime:{
                FullYear:"2018",
                Month:"10",
                Date:"12",
                Hours:undefined,
                Minutes:undefined,
                Seconds:undefined,
                dateObject: new Date("2018-10-12 00:00:00")
            },
            secondDateTime:{
                FullYear:"2018",
                Month:"11",
                Date:"30",
                Hours:undefined,
                Minutes:undefined,
                Seconds:undefined,
                dateObject: new Date("2018-11-30 00:00:00")
            }
        });
        expect(j4care.extractDateTimeFromString('20181104051400-20181122090335')).toEqual({
            mode:"range",
            firstDateTime:{
                FullYear:"2018",
                Month:"11",
                Date:"04",
                Hours:"05",
                Minutes:"14",
                Seconds:"00",
                dateObject: new Date("2018-11-04 05:14:00")
            },
            secondDateTime:{
                FullYear:"2018",
                Month:"11",
                Date:"22",
                Hours:"09",
                Minutes:"03",
                Seconds:"35",
                dateObject: new Date("2018-11-22 09:03:35")
            }
        });
        expect(j4care.extractDateTimeFromString('-20180326')).toEqual({
            mode:"leftOpen",
            firstDateTime:undefined,
            secondDateTime:{
                FullYear:"2018",
                Month:"03",
                Date:"26",
                Hours:undefined,
                Minutes:undefined,
                Seconds:undefined,
                dateObject: new Date("2018-03-26 00:00:00")
            }
        });
        expect(j4care.extractDateTimeFromString('test')).toEqual(null);
        expect(isNaN(j4care.extractDateTimeFromString('12345678').firstDateTime.dateObject.getTime())).toBe(true);
    });
});