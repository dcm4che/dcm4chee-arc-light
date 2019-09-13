import {j4care} from "./j4care.service";
import {DicomNetworkConnection} from "../interfaces";
import {DcmWebApp} from "../models/dcm-web-app";

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

const arr = [
    {
        key:"test1"
    },
    {
        key:"test2"
    }
]

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
        expect(j4care.formatDate(new Date("2018-02-03T02:04:05.582+01:00"), undefined)).toBe("20180203");
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

    it("Check if value is set",()=>{
        expect(j4care.isSet("2018.01.01")).toBeTruthy();
        expect(j4care.isSet("")).toBeTruthy();
        expect(j4care.isSet(undefined)).toBeFalsy();
    });

    it("Converts two dates in to range",()=>{
        expect(j4care.convertToDatePareString("2018.01.01","2018.01.04")).toEqual("20180101-20180104");
        expect(j4care.convertToDatePareString("2018.01.01","2018.01.01")).toEqual("20180101");
        expect(j4care.convertToDatePareString(new Date("2018.01.01"), "2018.01.01")).toEqual("20180101");
        expect(j4care.convertToDatePareString(undefined , undefined)).toEqual(undefined);
        expect(j4care.convertToDatePareString("" , "")).toEqual(undefined);
    });

    it("Should split the range to smaller blocks",()=>{
        expect(j4care.splitRange("20180101-20180108")).toEqual([
            "20180101",
            "20180102",
            "20180103",
            "20180104",
            "20180105",
            "20180106",
            "20180107",
            "20180108"
        ]);

        expect(j4care.splitRange("20181128-20181203")).toEqual([
            "20181128",
            "20181129",
            "20181130",
            "20181201",
            "20181202",
            "20181203"
        ]);

        expect(j4care.splitRange("20180910-20181029")).toEqual([
            "20180910-20180911",
            "20180912-20180913",
            "20180914",
            "20180915-20180916",
            "20180917-20180918",
            "20180919",
            "20180920-20180921",
            "20180922-20180923",
            "20180924",
            "20180925-20180926",
            "20180927",
            "20180928-20180929",
            "20180930-20181001",
            "20181002",
            "20181003-20181004",
            "20181005-20181006",
            "20181007",
            "20181008-20181009",
            "20181010-20181011",
            "20181012",
            "20181013-20181014",
            "20181015",
            "20181016-20181017",
            "20181018-20181019",
            "20181020",
            "20181021-20181022",
            "20181023-20181024",
            "20181025",
            "20181026-20181027",
            "20181028-20181029"
        ])
    });

    it("Checks if key or value is in the array path",()=>{
        expect(j4care.arrayHasIn(arr,"key")).toBeTruthy();
        expect(j4care.arrayHasIn(arr,"key", "test1")).toBeTruthy();
        expect(j4care.arrayHasIn(arr,"key", "not in the object")).toBeFalsy();
    });

    it("Schould join array elements",()=>{
        expect(j4care.join(["test1","test2","test3"],", ", " and ")).toEqual("test1, test2 and test3");
        expect(j4care.join(["test1","test2","test3"],", ")).toEqual("test1, test2, test3");
        expect(j4care.join(["test1","test2"],", ", " and ")).toEqual("test1 and test2");
        expect(j4care.join(["test1"],", ", " and ")).toEqual("test1");
        expect(j4care.join([],", ", " and ")).toEqual("");
        expect(j4care.join(undefined,", ", " and ")).toEqual("");
    });

    it("Should return protocol from dicomNetworkConnection",()=>{
        expect(
            j4care.getHTTPProtocolFromDicomNetworkConnection(
                new DicomNetworkConnection({
                    cn: "test",
                    dicomHostname: "shefki-lifebook",
                    dicomPort: 8080,
                    dicomTLSCipherSuite: [
                        "SSL_RSA_WITH_3DES_EDE_CBC_SHA"
                    ],
                    dicomInstalled: true,
                    dcmNetworkConnection: {
                        "dcmProtocol": "HTTP"
                    }
                })
            )
        ).toEqual("https");

        expect(
            j4care.getHTTPProtocolFromDicomNetworkConnection(
                new DicomNetworkConnection({
                    "cn": "syslog",
                    "dicomHostname": "127.0.0.1",
                    "dcmNetworkConnection": {
                        "dcmProtocol": "SYSLOG_TLS",
                        "dcmClientBindAddress": "0.0.0.0"
                    }
                })
            )
        ).toEqual("");

        expect(
            j4care.getHTTPProtocolFromDicomNetworkConnection(
                new DicomNetworkConnection({
                    cn: "test",
                    dicomHostname: "shefki-lifebook",
                    dicomPort: 8080,
                    dicomInstalled: true,
                    dcmNetworkConnection: {
                        "dcmProtocol": "HTTP"
                    }
                })
            )
        ).toEqual("http");

        expect(
            j4care.getHTTPProtocolFromDicomNetworkConnection(
                new DicomNetworkConnection({
                    "cn": "hl7",
                    "dicomHostname": "127.0.0.1",
                    "dicomPort": 2575,
                    "dcmNetworkConnection": {
                        "dcmProtocol": "HL7",
                        "dcmBindAddress": "0.0.0.0",
                        "dcmClientBindAddress": "0.0.0.0"
                    }
                })
            )
        ).toEqual("");
    });

    it("Should base url from DicomNetworkConnection",()=>{
        expect(
            j4care.getBaseUrlFromDicomNetworkConnection(
                [
                    new DicomNetworkConnection({
                        cn: "test1",
                        dicomHostname: "test1",
                        dicomPort: 8080,
                        dicomTLSCipherSuite: [
                            "SSL_RSA_WITH_3DES_EDE_CBC_SHA"
                        ],
                        dicomInstalled: true,
                        dcmNetworkConnection: {
                            "dcmProtocol": "HTTP"
                        }
                    }),
                    new DicomNetworkConnection({
                        cn: "test2",
                        dicomHostname: "test2",
                        dicomPort: 8080,
                        dicomInstalled: true,
                        dcmNetworkConnection: {
                            "dcmProtocol": "HTTP"
                        }
                    })
                ]
            )
        ).toEqual("https://test1:8080");

        expect(
            j4care.getBaseUrlFromDicomNetworkConnection(
                [
                    new DicomNetworkConnection({
                        cn: "test1",
                        dicomHostname: "test1",
                        dicomPort: 8080,
                        dicomInstalled: true,
                        dcmNetworkConnection: {
                            "dcmProtocol": "HTTP"
                        }
                    }),
                    new DicomNetworkConnection({
                        cn: "test2",
                        dicomHostname: "test2",
                        dicomPort: 8080,
                        dicomInstalled: true,
                        dcmNetworkConnection: {
                            "dcmProtocol": "HTTP"
                        }
                    })
                ]
            )
        ).toEqual("http://test1:8080");

        expect(
            j4care.getBaseUrlFromDicomNetworkConnection(
                []
            )
        ).toEqual(window.location.origin);
    });

    it("Should return whole URL from DcmWebApp",()=>{
        expect(
            j4care.getUrlFromDcmWebApplication(
                new DcmWebApp(
                    {
                        dcmWebAppName: "DCM4CHEE",
                        dicomDescription: "Hide instances rejected for Quality Reasons",
                        dcmWebServicePath: "/dcm4chee-arc/aets/DCM4CHEE/rs",
                        dcmWebServiceClass: [
                            "WADO_RS",
                            "STOW_RS",
                            "QIDO_RS",
                            "DCM4CHEE_ARC_AET"
                        ],
                        dicomAETitle: "DCM4CHEE",
                        dicomNetworkConnectionReference: [
                            "/dicomNetworkConnection/1"
                        ]
                    }
                )
            )
        ).toEqual(`${window.location.origin}/dcm4chee-arc/aets/DCM4CHEE/rs`);

        expect(
            j4care.getUrlFromDcmWebApplication(
                new DcmWebApp(
                    {
                        dcmWebAppName: "DCM4CHEE",
                        dicomDescription: "Hide instances rejected for Quality Reasons",
                        dcmWebServicePath: "/dcm4chee-arc/aets/DCM4CHEE/rs",
                        dcmWebServiceClass: [
                            "WADO_RS",
                            "STOW_RS",
                            "QIDO_RS",
                            "DCM4CHEE_ARC_AET"
                        ],
                        dicomAETitle: "DCM4CHEE",
                        dicomNetworkConnectionReference: [
                            new DicomNetworkConnection({
                                "cn": "http",
                                "dicomHostname": "127.0.0.1",
                                "dicomPort": 2575,
                                "dcmNetworkConnection": {
                                    "dcmProtocol": "HTTP",
                                    "dcmBindAddress": "0.0.0.0",
                                    "dcmClientBindAddress": "0.0.0.0"
                                }
                            })
                        ]
                    }
                )
            )
        ).toEqual(`http://127.0.0.1:2575/dcm4chee-arc/aets/DCM4CHEE/rs`);

    });

    it("Should cut float number",()=>{
        expect(j4care.round(5.2343,2, true)).toEqual(5.23);
        expect(j4care.round(5.2343,undefined, true)).toEqual(5.23);
        expect(j4care.round(5.2363,undefined, true)).toEqual(5.24);
        expect(j4care.round(5,2, true)).toEqual(5);
        expect(j4care.round(0,2, true)).toEqual(0);
        expect(j4care.round("5.2343",2, true)).toEqual(5.23);
        expect(j4care.round("",2, true)).toEqual("");
        expect(j4care.round(undefined,2, true)).toEqual(undefined);
    })

    it("Should return the string with prefix and suffix if exist",()=>{
        expect(j4care.meyGetString(AETS1[0],"dicomAETitle","#",";")).toBe("#DCM4CHEE;")
        expect(j4care.meyGetString(AETS1[0],"dicomAETitle","#")).toBe("#DCM4CHEE")
        expect(j4care.meyGetString(AETS1[0],"dicomAETitle")).toBe("DCM4CHEE")
        expect(j4care.meyGetString(AETS1[0],"dicomAETitle",undefined,":")).toBe("DCM4CHEE:")
        expect(j4care.meyGetString(AETS1[0],"",undefined,":")).toBe("")
        expect(j4care.meyGetString(AETS1[0],"",undefined,":",true)).toBe(":")
        expect(j4care.meyGetString(AETS1[0],"","/",":",true)).toBe("/:")
    });

    it("Should return diff object from two objects",()=>{
        expect(j4care.diffObjects(
            {
                a:1,
                b:2
        },{
                b:2,
                a:1
        })).toEqual({});

        expect(j4care.diffObjects(
            {
                a:1,
                b:2,
                c:[]
        },{
                b:2,
                a:1
        })).toEqual({c:[]});

        expect(j4care.diffObjects(
            {
                a:1,
                b:2,
                c:{}
        },{
                b:2,
                a:1
        },false)).toEqual({c:{}});

        console.log("diff",j4care.diffObjects(
            {
                a:1,
                b:2
            },{
                b:2,
                a:1,
                c:{}
            }));
        console.log("diff",j4care.diffObjects(
            {
                b:2,
                a:1,
                c:{}
            },{
                a:1,
                b:2
            }));
        expect(j4care.diffObjects(
            {
                a:1,
                b:2
        },{
                b:2,
                a:1,
                c:{}
        },false)).toEqual({c:{}})
    });

    it("Should traverse the object",()=>{
        expect(j4care.traverse([
            [
                [
                    {
                    test:"selam"
                    }
                ],
                [
                    {
                        test:"button",
                        id:"count"
                    }
                ]
            ]
        ],(m,i,t,s)=>{
            console.log("m",m);
            console.log("i",i);
            console.log("t",t);
            console.log("s",s);
        },""))
    })
});