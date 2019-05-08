import {StudyDeviceWebserviceModel} from "./study-device-webservice.model";
import {Device} from "../../models/device";
import {SelectDropdown} from "../../interfaces";

describe('Service: Auth', () => {
    let service: StudyDeviceWebserviceModel;

    const devices:Device[] = [
        new Device({
            dicomDeviceName:"TEST3"
        }),
        new Device({
            dicomDeviceName:"dcm4chee-arc"
        })
    ];

    const dcm4cheeArcObject = {
        dicomDeviceName: "dcm4chee-arc",
        dcmDevice:{
            dcmWebApp:[
                {
                    "dcmWebAppName": "DCM4CHEE-RS",
                    "dicomDescription": "Hide instances rejected for Quality Reasons",
                    "dcmWebServicePath": "/dcm4chee-arc/aets/DCM4CHEE/rs",
                    "dcmWebServiceClass": [
                        "WADO_RS",
                        "STOW_RS",
                        "QIDO_RS"
                    ],
                    "dicomAETitle": "DCM4CHEE",
                    "dicomNetworkConnectionReference": [
                        "/dicomNetworkConnection/1"
                    ]
                },
                {
                    "dcmWebAppName": "AS_RECEIVED-WADO",
                    "dicomDescription": "Retrieve instances as received",
                    "dcmWebServicePath": "/dcm4chee-arc/aets/AS_RECEIVED/wado",
                    "dcmWebServiceClass": [
                        "WADO_URI"
                    ],
                    "dicomAETitle": "AS_RECEIVED",
                    "dicomNetworkConnectionReference": [
                        "/dicomNetworkConnection/1"
                    ]
                }            ]
        }
    };

    const test3 = {
        dicomDeviceName: "TEST3"
    };

    beforeEach(() => {
        service = new StudyDeviceWebserviceModel({devices:devices});
    });

    afterEach(() => {
        service = null;
    });

    it('Should set the webservices from the device object', () => {
        service.selectedDeviceObject = dcm4cheeArcObject;
        expect(service.dcmWebAppServices).toEqual(dcm4cheeArcObject.dcmDevice.dcmWebApp);
        expect(service.dcmWebAppServicesDropdown).toEqual(dcm4cheeArcObject.dcmDevice.dcmWebApp.map(webApp=>{
            return new SelectDropdown(webApp.dcmWebServicePath,webApp.dcmWebAppName,webApp.dicomDescription,undefined,undefined,webApp)
        }));
    });
    it('Should set selectedWebapp by string', () => {
        const value = "DCM4CHEE-RS";
        service.selectedDeviceObject = dcm4cheeArcObject;
        service.setSelectedWebAppByString(value);
        expect(service.selectedWebApp).toEqual(dcm4cheeArcObject.dcmDevice.dcmWebApp[0]);
        expect(service.dcmWebAppServicesDropdown).toEqual(dcm4cheeArcObject.dcmDevice.dcmWebApp.map(webApp=>{
            if(webApp.dcmWebAppName === value){
                return new SelectDropdown(webApp.dcmWebServicePath,webApp.dcmWebAppName,webApp.dicomDescription,undefined,undefined,webApp, true)
            }else{
                return new SelectDropdown(webApp.dcmWebServicePath,webApp.dcmWebAppName,webApp.dicomDescription,undefined,undefined,webApp, false)
            }
        }));
    });
    it('Should reset selectedWebapp', () => {
        const value = "DCM4CHEE-RS";
        service.selectedDeviceObject = dcm4cheeArcObject;
        service.setSelectedWebAppByString(value);
        service.resetSelectedWebApp();
        expect(service.selectedWebApp).toBe(undefined);
        expect(service.dcmWebAppServicesDropdown.filter(option=>{
            option.selected
        }).length).toBe(0);
    });
    it("Check if setting of undefined value caused error",()=>{
        service.selectedDeviceObject = test3;
        // service.dcmWebAppServices = undefined;
        expect(service.dcmWebAppServices).toBe(undefined);
        expect(service.dcmWebAppServicesDropdown).toEqual(undefined);
    });
    it("Should set selected device on deviceobject set",()=>{
        service.devices = devices;
        service.selectedDeviceObject = dcm4cheeArcObject;
        // service.dcmWebAppServices = undefined;
        expect(service.selectedDevice.dicomDeviceName).toBe(dcm4cheeArcObject.dicomDeviceName);
    });
});