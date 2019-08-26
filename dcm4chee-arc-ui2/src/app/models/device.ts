import {Aet} from "./aet";
import {DcmWebApp} from "./dcm-web-app";
import * as _ from "lodash";


export class Device{
    private _dicomDeviceName:string;
    private _dicomDeviceDescription?:string;
    private _dicomDescription?:string;
    private _dicomNetworkAE?:Aet[];
    private _dcmWebApp?:DcmWebApp[];
    private _dicomInstalled:boolean;
    private _hasArcDevExt:boolean;
    private _dicomNetworkConnection:any[];
    private _wholeObject:any;
    private _dicomManufacturer;
    private _dicomManufacturerModelName;
    private _dicomSoftwareVersion:string[];
    private _dicomPrimaryDeviceType:string[];

    constructor(
        device:{
                dicomDeviceName?:string;
                dicomDeviceDescription?:string;
                dicomDescription?:string,
                dicomInstalled?:boolean;
                hasArcDevExt?:boolean;
                dicomManufacturer?:string;
                dicomManufacturerModelName?:string;
                dicomNetworkAE?:Aet[];
                dicomNetworkConnection ?:any[];
                dicomSoftwareVersion?:string[];
                dicomPrimaryDeviceType?:string[];
                dcmWebApp?:DcmWebApp[];
            } = {}
        ){
            this._wholeObject = device;
            this._dicomDeviceName = this._wholeObject.dicomDeviceName;
            this._dicomDeviceDescription = this._wholeObject.dicomDeviceDescription || this._wholeObject.dicomDescription  || '';
            this._dicomInstalled = device.dicomInstalled;
            this._hasArcDevExt = device.hasArcDevExt;
            this._dicomManufacturer = device.dicomManufacturer;
            this._dicomManufacturerModelName = device.dicomManufacturerModelName;
            this._dicomSoftwareVersion = device.dicomSoftwareVersion;
            this._dicomPrimaryDeviceType = device.dicomPrimaryDeviceType;
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

    get dicomDescription(): string {
        return this._dicomDescription;
    }

    set dicomDescription(value: string) {
        this._dicomDescription = value;
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


    get dicomInstalled(): boolean {
        return this._dicomInstalled;
    }

    set dicomInstalled(value: boolean) {
        this._dicomInstalled = value;
    }

    get hasArcDevExt(): boolean {
        return this._hasArcDevExt;
    }

    set hasArcDevExt(value: boolean) {
        this._hasArcDevExt = value;
    }

    get dicomNetworkConnection(): any[] {
        return this._dicomNetworkConnection;
    }

    set dicomNetworkConnection(value: any[]) {
        this._dicomNetworkConnection = value;
    }


    get dicomManufacturer() {
        return this._dicomManufacturer;
    }

    set dicomManufacturer(value) {
        this._dicomManufacturer = value;
    }

    get dicomManufacturerModelName() {
        return this._dicomManufacturerModelName;
    }

    set dicomManufacturerModelName(value) {
        this._dicomManufacturerModelName = value;
    }

    get dicomSoftwareVersion(): string[] {
        return this._dicomSoftwareVersion;
    }

    set dicomSoftwareVersion(value: string[]) {
        this._dicomSoftwareVersion = value;
    }

    get dicomPrimaryDeviceType(): string[] {
        return this._dicomPrimaryDeviceType;
    }

    set dicomPrimaryDeviceType(value: string[]) {
        this._dicomPrimaryDeviceType = value;
    }

    get wholeObject(): any {
        return this._wholeObject;
    }

    set wholeObject(value: any) {
        this._wholeObject = value;
    }
}