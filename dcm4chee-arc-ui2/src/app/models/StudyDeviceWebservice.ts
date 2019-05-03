import {DcmWebApp} from "./dcm-web-app";
import * as _  from "lodash";
import {SelectDropdown} from "../interfaces";
import {Device} from "./device";


export class StudyDeviceWebservice {
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

            if(object.devices){
                this.devices = object.devices;

            }
            if(object.selectedDevice){
                this._selectedDevice = object.selectedDevice;
            }
            if(object.selectedDeviceObject){
                this.selectedDeviceObject = object.selectedDeviceObject;
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
        }
        this._selectedDevice = undefined;
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
    setDcmWebAppServicesDropdown(value:DcmWebApp[]){
        if(value){
            this._dcmWebAppServices = value,
            this._dcmWebAppServicesDropdown = value.map((webApp:DcmWebApp)=>{
                return new SelectDropdown(webApp.dcmWebAppName,webApp.dcmWebServicePath,webApp.dicomDescription,undefined,undefined,webApp)
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
        this._selectedWebApp = undefined;
    }


    get selectedWebApp(): DcmWebApp {
        return this._selectedWebApp;
    }

    set selectedWebApp(value: DcmWebApp) {
        this._selectedWebApp = value;
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
}


