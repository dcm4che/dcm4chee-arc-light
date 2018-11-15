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
    })
})