import {DcmWebApp} from "../../models/dcm-web-app";
import * as _  from "lodash";
import {SelectDropdown} from "../../interfaces";
import {Device} from "../../models/device";
import {j4care} from "../../helpers/j4care.service";


export class StudyDeviceWebserviceModel {
    private _devices:Device[];
    private _selectedDevice?:Device;
    private _selectedDeviceObject:any;
    private _dcmWebAppServices:DcmWebApp[];
    private _selectedWebApp?:DcmWebApp;
    private _dcmWebAppServicesDropdown:SelectDropdown<DcmWebApp>[];
    private _devicesDropdown:SelectDropdown<Device>[];

    constructor(
        object:{
            devices?:Device[],
            selectedDevice?:Device,
            selectedDeviceObject?:any,
            dcmWebAppServices?:DcmWebApp[],
            selectedWebApp?:DcmWebApp
        } = {}){

            if(object.selectedDeviceObject){
                this.selectedDeviceObject = object.selectedDeviceObject;
            }
            if(object.devices){
                this.devices = object.devices;
            }
            if(object.selectedDevice){
                this._selectedDevice = object.selectedDevice;
            }
            if(object.dcmWebAppServices){
                this.dcmWebAppServices = object.dcmWebAppServices;
            }
            if(object.selectedWebApp){
                this._selectedWebApp = object.selectedWebApp;
            }
    }

    get devices(): any[] {
        return this._devices;
    }

    set devices(value: any[]) {
        this._devices = value;
        this._devicesDropdown = value.map((device:Device)=>{
            return new SelectDropdown(device.dicomDeviceName,device.dicomDeviceName,device.dicomDeviceDescription,undefined,undefined, device);
        }) || [];
        if(this._selectedDeviceObject && _.hasIn(this._selectedDeviceObject,"dicomDeviceName")){
           this._devicesDropdown.forEach((device:SelectDropdown<Device>)=>{
               if(device.wholeObject.dicomDeviceName === this._selectedDeviceObject.dicomDeviceName){
                   this._selectedDevice = device.wholeObject;
                   device.selected = true;
               }
           })
        }else{
            this._selectedDevice = undefined;
        }
        this._selectedWebApp = undefined;
    }
    get selectedDeviceObject(): any {
        return this._selectedDeviceObject;
    }

    set selectedDeviceObject(value: any) {
        this._selectedDeviceObject = value;
        if(_.hasIn(this._selectedDeviceObject,"dcmDevice.dcmWebApp")){
            this.setDcmWebAppServicesDropdown(this._selectedDeviceObject.dcmDevice.dcmWebApp);
        }else{
            this.setDcmWebAppServicesDropdown(undefined);
        }
    }

    get dcmWebAppServicesDropdown(): SelectDropdown<DcmWebApp>[] {
        return this._dcmWebAppServicesDropdown;
    }

    set dcmWebAppServicesDropdown(value: SelectDropdown<DcmWebApp>[]) {
        this._dcmWebAppServicesDropdown = value;
    }

    getDcmWebAppServicesDropdown(dcmWebServiceClass:string[]){
        if(dcmWebServiceClass){
            return (this._dcmWebAppServicesDropdown || []).filter(webServiceDropdwon=>{
                let check:boolean = false;
                dcmWebServiceClass.forEach(serviceClass=>{
                    if(webServiceDropdwon.wholeObject.dcmWebServiceClass.indexOf(serviceClass) > -1){
                        check = true;
                    }
                });
                return check;
            });
        }else{
            return this._dcmWebAppServicesDropdown;
        }
    }
    setDcmWebAppServicesDropdown(value:DcmWebApp[]){
        if(value){
            this._dcmWebAppServices = value,
            this._dcmWebAppServicesDropdown = value.map((webApp:DcmWebApp)=>{
                return new SelectDropdown(webApp.dcmWebServicePath,webApp.dcmWebAppName,webApp.dicomDescription,undefined,undefined,webApp)
            }) || [];
        }else{
            this._dcmWebAppServicesDropdown = undefined;
        }
    }

    get selectedDevice(): Device {
        return this._selectedDevice;
    }

    set selectedDevice(value: Device) {
        this._selectedDevice = value;
        this.resetSelectedWebApp()
    }


    get selectedWebApp(): DcmWebApp {
        return this._selectedWebApp;
    }

    set selectedWebApp(value: DcmWebApp) {
        this._selectedWebApp = value;
    }

    setSelectedWebAppByString(value:string){
        try{
            if(this._dcmWebAppServicesDropdown){
                this._dcmWebAppServicesDropdown.forEach(webApp=>{
                    if(webApp.text === value || webApp.value === value || webApp.htmlLabel === value || webApp.label === value){
                        webApp.selected = true;
                        this._selectedWebApp = webApp.wholeObject;
                    }else{
                        webApp.selected = false;
                    }
                });
            }
        }catch(e){
            j4care.log("Something went wrong on setting selected webapp by string",e);
            this.resetSelectedWebApp()
        }
    }

    get dcmWebAppServices(): DcmWebApp[] {
        return this._dcmWebAppServices;
    }

    set dcmWebAppServices(value: DcmWebApp[]) {
        this._dcmWebAppServices = value;
        this._dcmWebAppServicesDropdown = value.map((webApp:DcmWebApp)=>{
            return new SelectDropdown(webApp.dcmWebAppName,webApp.dcmWebServicePath,webApp.dicomDescription,undefined,undefined,webApp)
        }) || [];
    }

    get devicesDropdown(): SelectDropdown<Device>[] {
        return this._devicesDropdown;
    }

    set devicesDropdown(value: SelectDropdown<Device>[]) {
        this._devicesDropdown = value;
    }
    resetSelectedWebApp(){
        this._selectedWebApp = undefined;
        if(this._dcmWebAppServicesDropdown){
            this._dcmWebAppServicesDropdown.forEach(service=>{
                service.selected = false;
            })
        }
    }
}


