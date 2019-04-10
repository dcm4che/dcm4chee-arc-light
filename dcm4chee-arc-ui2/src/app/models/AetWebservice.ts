import {Aet} from "./aet";
import {DcmWebApp} from "./dcm-web-app";
import * as _  from "lodash";
import {SelectDropdown} from "../interfaces";


export interface AetWebserviceDropdownObject {
    aets:SelectDropdown[];
    webAppservice:SelectDropdown[];
}

export class AetWebservice {
    private _deviceObject:any;
    private _aet:SelectDropdown[];
    private _dcmWebAppService:SelectDropdown[];
    private _aetWebservice:AetWebserviceDropdownObject;

    constructor(
        object:{
            deviceObject?:any,
            aet?:Aet[],
            dcmWebAppService?:DcmWebApp[]
        } = {}){
            if(object.deviceObject){
                this.deviceObject = object.deviceObject;
            }else{
                this._aet = object.aet.map((ae:Aet)=>{
                    return new SelectDropdown(ae.dicomAETitle,ae.dicomDescription,ae.dicomDescription,undefined, undefined,ae);
                }) || [];
                this._dcmWebAppService = object.dcmWebAppService.map((webApp:DcmWebApp)=>{
                    return new SelectDropdown(webApp.dcmWebAppName,webApp.dcmWebServicePath,webApp.dicomDescription,undefined,undefined,webApp)
                }) || [];
            }
    }


    get deviceObject(): any {
        return this._deviceObject;
    }

    set deviceObject(value: any) {
        this._deviceObject = value;
        if(_.hasIn(this._deviceObject,"dicomNetworkAE")){
            this.setAet(this._deviceObject.dicomNetworkAE)
        }else{
            this.setAet(undefined);
        }
        if(_.hasIn(this._deviceObject,"dcmDevice.dcmWebApp")){
            this.setDcmWebAppService(this._deviceObject.dcmDevice.dcmWebApp);
        }else{
            this.setDcmWebAppService(undefined);
        }
    }

    get aet(): SelectDropdown[] {
        return this._aet;
    }

    set aet(value: SelectDropdown[]) {
        this._aet = value;
    }
    setAet(value:Aet[]){
        if(value){
            this._aet = value.map((ae:Aet)=>{
                return new SelectDropdown(ae.dicomAETitle,ae.dicomDescription,ae.dicomDescription,undefined, undefined,ae);
            });
            this._aetWebservice = this._aetWebservice || {aets:undefined, webAppservice:undefined};
            this._aetWebservice.aets = this._aet;
        }else{
            if(this._aet){
                this._aet = undefined;
            }
        }
    }
    get dcmWebAppService(): SelectDropdown[] {
        return this._dcmWebAppService;
    }

    set dcmWebAppService(value: SelectDropdown[]) {
        this._dcmWebAppService = value;
    }
    setDcmWebAppService(value:DcmWebApp[]){
        if(value){
            this._dcmWebAppService = value.map((webApp:DcmWebApp)=>{
                return new SelectDropdown(webApp.dcmWebAppName,webApp.dcmWebServicePath,webApp.dicomDescription,undefined,undefined,webApp)
            }) || [];
            this._aetWebservice = this._aetWebservice || {aets:undefined, webAppservice:undefined};
            this._aetWebservice.webAppservice = this._dcmWebAppService;
        }else{
            if(this._aetWebservice){
                this._aetWebservice = undefined;
            }
        }
    }

    get aetWebservice() {
        return this._aetWebservice;
    }

    set aetWebservice(value) {
        this._aetWebservice = value;
    }
}


