import {ControlService} from "../control/control.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {TestBed} from "@angular/core/testing";
import {AppService} from "../../app.service";
import {DeviceConfiguratorService} from "./device-configurator.service";
import {AeListService} from "../ae-list/ae-list.service";
import {Hl7ApplicationsService} from "../hl7-applications/hl7-applications.service";
import {Globalvar} from "../../constants/globalvar";
import {DevicesService} from "../devices/devices.service";

class MyServiceDependencyStub {
}
const DEVICE = {
    dicomDeviceName:"dcm4chee-arc",
    dicomInstalled:true,
    dicomManufacturer:"dcm4che.org",
    dicomManufacturerModelName:"dcm4chee-arc"
};
describe("DeviceConfiguratorService",()=> {
    let service:DeviceConfiguratorService;



    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                DeviceConfiguratorService,
                { provide: J4careHttpService, useClass: MyServiceDependencyStub },
                { provide: AppService, useClass: MyServiceDependencyStub },
                { provide: DevicesService, useClass: MyServiceDependencyStub },
                { provide: AeListService, useClass: MyServiceDependencyStub },
                { provide: Hl7ApplicationsService, useClass: MyServiceDependencyStub },
            ],
        });
        service = TestBed.get(DeviceConfiguratorService);
        //"$.dcmDevice.hl7Application[*].dcmArchiveHL7Application.hl7PrefetchRule[*].dicomAETitle"
        service.device = {
            "dicomDeviceName": "dcm4chee-arc",
            "dicomNetworkAE": [
                {
                    "dicomAETitle": "DCM4CHEE",
                    "dicomAssociationInitiator": true,
                    "dicomAssociationAcceptor": true,
                    "dicomNetworkConnectionReference": [
                        "/dicomNetworkConnection/2"
                    ],
                    "dicomTransferCapability": [],
                    "dcmNetworkAE": {}
                }
            ],
            //dcmDevice.dcmWebApp[*].dicomAETitle
            "dcmDevice": {
                "dcmWebApp": [
                    {
                        "dcmWebAppName": "DCM4CHEE",
                        "dicomDescription": "Hide instances rejected for Quality Reasons",
                        "dcmWebServicePath": "/dcm4chee-arc/aets/DCM4CHEE/rs",
                        "dcmWebServiceClass": [
                            "STOW_RS",
                        ],
                        "dicomAETitle": "DCM4CHEE",
                        "dicomNetworkConnectionReference": [
                            "/dicomNetworkConnection/1"
                        ],
                        "testSubArray2":[
                            "DCM4CHEE",
                            "TEST"
                        ]
                    }
                ],
                "dcmAuditLogger": [
                    {
                        "cn": "Audit Logger",
                        "dcmAuditSuppressCriteria": [
                            {
                                "cn": "Suppress Query from own Archive AE",
                                "dcmAuditEventID": [
                                    "(110112, DCM, \"Query\")"
                                ],
                                "dcmAuditUserID": [
                                    "DCM4CHEE"
                                ],
                                "dcmAuditUserIsRequestor": true
                            }
                        ]
                    }
                ],
                "hl7Application": [
                    {
                        "dcmArchiveHL7Application": {
                            "dicomAETitle": "DCM4CHEE",
                            "hl7PrefetchRule":[{
                                "dicomAETitle": "DCM4CHEE"
                            }]
                        }
                    }
                ],
                "dcmArchiveDevice": {
                    "dcmRejectExpiredStudiesAETitle": "DCM4CHEE",
                    "dcmStorageVerificationAETitle": "DCM4CHEE",
                    "dcmRejectionNoteStorageAET": "DCM4CHEE",
                    "dcmCompressionAETitle": "DCM4CHEE",
                    "dcmExporter": [
                        {
                            "dicomAETitle": "DCM4CHEE"
                        }
                    ],
                },
                "testArray":[
                    "TEST",
                    "SELAM",
                    "DCM4CHEE",
                    "TEST2"
                ]
            }
        };
    });

    it("Should add addChangesToDevice() Network AE to device",()=>{
        let newDevice = Object.assign({},DEVICE);
        service.addChangesToDevice({
                cn:"dicom",
                dcmNetworkConnection: {
                    dcmBindAddress: "0.0.0.0",
                    dcmClientBindAddress: "",
                    dcmMaxOpsInvoked: 0,
                    dcmMaxOpsPerformed: 0
                },
                dicomHostname: "127.0.0.1",
                dicomPort: undefined
            },
            'dicomNetworkAE[0]',
            newDevice
        );
        expect(newDevice).toEqual({
            dicomDeviceName:"dcm4chee-arc",
            dicomInstalled:true,
            dicomManufacturer:"dcm4che.org",
            dicomManufacturerModelName:"dcm4chee-arc",
            dicomNetworkAE:[
                {
                    cn:"dicom",
                    dcmNetworkConnection: {
                        dcmBindAddress: "0.0.0.0",
                        dcmClientBindAddress: "",
                        dcmMaxOpsInvoked: 0,
                        dcmMaxOpsPerformed: 0
                    },
                    dicomHostname: "127.0.0.1",
                }
            ]
        });

    });

    it("Should try to add addChangesToDevice() empty array on the device",()=>{
        let newDevice = Object.assign({},DEVICE);
        service.addChangesToDevice({
                dicomSoftwareVersion:[]
            },
            'dicomNetworkAE[0]',
            newDevice
        );
        expect(newDevice).toEqual(newDevice);
    });

    it("Should add addChangesToDevice() exporter descriptor in the device",()=> {
        let newDevice = Object.assign({},DEVICE);
        service.addChangesToDevice(
            {
                "dcmURI": "dicom:STORESCP2",
                "dcmStgCmtSCP": "",
                "dcmQueueName": "Export2",
                "dcmExportPriority": 4,
                "dicomDescription": "",
                "dicomAETitle": "DCM4CHEE",
                "dcmExporterID": "STORESCP",
                "dcmIanDestination": [""],
                "dcmRetrieveAET": [],
                "dcmRetrieveLocationUID": "",
                "dcmInstanceAvailability": "ONLINE",
                "dcmSchedule": [""],
                "dcmProperty": [""]
            },
            `${Globalvar.EXPORTER_CONFIG_PATH}[0]`,
            newDevice
        );
        console.log("newDevice",newDevice);
        expect(newDevice).toEqual({
            dicomDeviceName:"dcm4chee-arc",
            dicomInstalled:true,
            dicomManufacturer:"dcm4che.org",
            dicomManufacturerModelName:"dcm4chee-arc",
            dcmDevice:{
                dcmArchiveDevice:{
                    dcmExporter:[{
                        dcmURI:"dicom:STORESCP2",
                        dcmQueueName:"Export2",
                        dcmExportPriority:4,
                        dicomAETitle:"DCM4CHEE",
                        dcmExporterID:"STORESCP",
                        dcmInstanceAvailability:"ONLINE"
                    }]
                }
            }
        })
    });

    it("Should update the new value according to ref",()=>{
        service.setValueToReferences("DCM4CHEE", "DCM4CHEE2",[
            "$.dicomNetworkAE[*].dicomAETitle",
            "$.dcmDevice.dcmWebApp[*].dicomAETitle",
            "$.dcmDevice.testArray[*]",
            "$.dcmDevice.dcmWebApp[*].testSubArray2[*]",
            "$.dcmDevice.hl7Application[*].dcmArchiveHL7Application.hl7PrefetchRule[*].dicomAETitle"
        ]);
        expect(service.device).toEqual({
            "dicomDeviceName": "dcm4chee-arc",
            "dicomNetworkAE": [
                {
                    "dicomAETitle": "DCM4CHEE2",
                    "dicomAssociationInitiator": true,
                    "dicomAssociationAcceptor": true,
                    "dicomNetworkConnectionReference": [
                        "/dicomNetworkConnection/2"
                    ],
                    "dicomTransferCapability": [],
                    "dcmNetworkAE": {}
                }
            ],
            //dcmDevice.dcmWebApp[*].dicomAETitle
            "dcmDevice": {
                "dcmWebApp": [
                    {
                        "dcmWebAppName": "DCM4CHEE",
                        "dicomDescription": "Hide instances rejected for Quality Reasons",
                        "dcmWebServicePath": "/dcm4chee-arc/aets/DCM4CHEE/rs",
                        "dcmWebServiceClass": [
                            "STOW_RS",
                        ],
                        "dicomAETitle": "DCM4CHEE2",
                        "dicomNetworkConnectionReference": [
                            "/dicomNetworkConnection/1"
                        ],
                        "testSubArray2":[
                            "DCM4CHEE2",
                            "TEST"
                        ]
                    }
                ],
                "dcmAuditLogger": [
                    {
                        "cn": "Audit Logger",
                        "dcmAuditSuppressCriteria": [
                            {
                                "cn": "Suppress Query from own Archive AE",
                                "dcmAuditEventID": [
                                    "(110112, DCM, \"Query\")"
                                ],
                                "dcmAuditUserID": [
                                    "DCM4CHEE"
                                ],
                                "dcmAuditUserIsRequestor": true
                            }
                        ]
                    }
                ],
                "hl7Application": [
                    {
                        "dcmArchiveHL7Application": {
                            "dicomAETitle": "DCM4CHEE",
                            "hl7PrefetchRule":[{
                                "dicomAETitle": "DCM4CHEE2"
                            }]
                        }
                    }
                ],
                "dcmArchiveDevice": {
                    "dcmRejectExpiredStudiesAETitle": "DCM4CHEE",
                    "dcmStorageVerificationAETitle": "DCM4CHEE",
                    "dcmRejectionNoteStorageAET": "DCM4CHEE",
                    "dcmCompressionAETitle": "DCM4CHEE",
                    "dcmExporter": [
                        {
                            "dicomAETitle": "DCM4CHEE"
                        }
                    ],
                },
                "testArray":[
                    "TEST",
                    "SELAM",
                    "DCM4CHEE2",
                    "TEST2"
                ]
            }
        })
    })
});