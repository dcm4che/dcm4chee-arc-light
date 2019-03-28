import {Aet} from "./aet";
import {DcmWebApp} from "./dcm-web-app";
import * as _ from "lodash";


export class Device{
    private _dicomDeviceName:string;
    private _dicomDeviceDescription?:string;
    private _dicomNetworkAE?:Aet[];
    private _dcmWebApp?:DcmWebApp[];
    private _dicomNetworkConnection:any[];
    private _wholeObject:any;

    constructor(
        device:{
                dicomDeviceName?:string;
                dicomDeviceDescription?:string;
                dicomNetworkAE?:Aet[];
                dicomNetworkConnection ?:any[];
                dcmWebApp?:DcmWebApp[];
            } = {}
        ){
            this._wholeObject = device;
            this._dicomDeviceName = this._wholeObject.dicomDeviceName;
            this._dicomDeviceDescription = this._wholeObject.dicomDeviceDescription || '';
            this._dicomNetworkAE = this._wholeObject.dicomNetworkAE;
            this._dicomNetworkConnection = this._wholeObject.dicomNetworkConnection  || undefined;
            this._dcmWebApp = _.get(this._wholeObject,"dcmDevice.dcmWebApp") || undefined;
        }


    get dicomDeviceName(): string {
        return this._dicomDeviceName;
    }

    set dicomDeviceName(value: string) {
        this._dicomDeviceName = value;
    }

    get dicomDeviceDescription(): string {
        return this._dicomDeviceDescription;
    }

    set dicomDeviceDescription(value: string) {
        this._dicomDeviceDescription = value;
    }

    get dicomNetworkAE(): Aet[] {
        return this._dicomNetworkAE;
    }

    set dicomNetworkAE(value: Aet[]) {
        this._dicomNetworkAE = value;
    }

    get dcmWebApp(): DcmWebApp[] {
        return this._dcmWebApp;
    }

    set dcmWebApp(value: DcmWebApp[]) {
        this._dcmWebApp = value;
    }


    get dicomNetworkConnection(): any[] {
        return this._dicomNetworkConnection;
    }

    set dicomNetworkConnection(value: any[]) {
        this._dicomNetworkConnection = value;
    }

    get wholeObject(): any {
        return this._wholeObject;
    }

    set wholeObject(value: any) {
        this._wholeObject = value;
    }
}