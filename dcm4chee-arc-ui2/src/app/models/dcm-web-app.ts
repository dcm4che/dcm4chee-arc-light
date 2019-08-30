import {DicomNetworkConnection} from "../interfaces";
import {Aet} from "./aet";

export type WebServiceClass = "QIDO_RS" | "STOW_RS" | "WADO_RS" | "WADO_URI" | "UPS_RS" | "DCM4CHEE_ARC" | "DCM4CHEE_ARC_AET"|string;

export class DcmWebApp{
    private _dcmWebAppName:string;
    private _dicomNetworkConnectionReference:any[];
    private _dicomNetworkConnection:any[];
    private _dicomDescription:string;
    private _dcmWebServicePath:string;
    private _dcmWebServiceClass:WebServiceClass[];
    private _dicomAETitle:string;
    private _dicomApplicationCluster:any[]
    private _dicomInstalled:boolean;
    private _dcmKeycloakClientID:string;
    private _dicomDeviceName:string;
    private _dicomAETitleObject:Aet;

    constructor(
        webApp:{
            dcmWebAppName?:string;
            dicomDescription?:string;
            dcmWebServicePath?:string;
            dcmWebServiceClass?:WebServiceClass[];
            dicomNetworkConnectionReference?:any[];
            dicomNetworkConnection?:any[];
            dicomAETitle?:string;
            dicomApplicationCluster?:any[];
            dicomInstalled?:boolean;
            dcmKeycloakClientID?:string;
            dcmHideNotRejectedInstances?:boolean;
            dicomDeviceName?:string;
        } = {}
    ){
        this._dcmWebAppName = webApp.dcmWebAppName;
        this._dicomNetworkConnectionReference = webApp.dicomNetworkConnectionReference;
        this._dicomNetworkConnection = webApp.dicomNetworkConnection;
        this._dicomDescription = webApp.dicomDescription;
        this._dcmWebServicePath = webApp.dcmWebServicePath;
        this._dcmWebServiceClass = webApp.dcmWebServiceClass;
        this._dicomAETitle = webApp.dicomAETitle;
        this._dicomApplicationCluster = webApp.dicomApplicationCluster;
        this._dicomInstalled = webApp.dicomInstalled;
        this._dcmKeycloakClientID = webApp.dcmKeycloakClientID;
        this._dicomDeviceName = webApp.dicomDeviceName;
    }

    get dcmWebAppName(): string {
        return this._dcmWebAppName;
    }

    set dcmWebAppName(value: string) {
        this._dcmWebAppName = value;
    }

    get dicomNetworkConnectionReference(): any[] {
        return this._dicomNetworkConnectionReference;
    }

    set dicomNetworkConnectionReference(value: any[]) {
        this._dicomNetworkConnectionReference = value;
    }

    get dicomNetworkConnection(): any[] {
        return this._dicomNetworkConnection;
    }

    set dicomNetworkConnection(value: any[]) {
        this._dicomNetworkConnection = value;
    }

    get dicomDescription(): string {
        return this._dicomDescription;
    }

    set dicomDescription(value: string) {
        this._dicomDescription = value;
    }

    get dcmWebServicePath(): string {
        return this._dcmWebServicePath;
    }

    set dcmWebServicePath(value: string) {
        this._dcmWebServicePath = value;
    }

    get dcmWebServiceClass(): WebServiceClass[] {
        return this._dcmWebServiceClass;
    }

    set dcmWebServiceClass(value: WebServiceClass[]) {
        this._dcmWebServiceClass = value;
    }

    get dicomAETitle(): string {
        return this._dicomAETitle;
    }

    set dicomAETitle(value: string) {
        this._dicomAETitle = value;
    }

    get dicomApplicationCluster(): any[] {
        return this._dicomApplicationCluster;
    }

    set dicomApplicationCluster(value: any[]) {
        this._dicomApplicationCluster = value;
    }

    get dicomInstalled(): boolean {
        return this._dicomInstalled;
    }

    set dicomInstalled(value: boolean) {
        this._dicomInstalled = value;
    }

    get dcmKeycloakClientID(): string {
        return this._dcmKeycloakClientID;
    }

    set dcmKeycloakClientID(value: string) {
        this._dcmKeycloakClientID = value;
    }

    get dicomDeviceName(): string {
        return this._dicomDeviceName;
    }

    set dicomDeviceName(value: string) {
        this._dicomDeviceName = value;
    }

    get dicomAETitleObject(): Aet {
        return this._dicomAETitleObject;
    }

    set dicomAETitleObject(value: Aet) {
        this._dicomAETitleObject = value;
    }
}