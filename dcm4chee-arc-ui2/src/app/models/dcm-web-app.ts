import {DicomNetworkConnection} from "../interfaces";

export type WebServiceClass = "QIDO_RS" | "STOW_RS" | "WADO_RS" | "WADO_URI" | "UPS_RS" | "DCM4CHEE_ARC" | "DCM4CHEE_ARC_AET"|string;

export class DcmWebApp{
    private _dcmWebAppName:string;
    private _dicomNetworkConnectionReference:any[];
    private _dicomDescription:string;
    private _dcmWebServicePath:string;
    private _dcmWebServiceClass:WebServiceClass[];
    private _dicomAETitle:string;
    private _dicomApplicationCluster:any[]
    private _dicomInstalled:boolean;
    private _dcmKeycloakClientID:string;
    private _dcmHideNotRejectedInstances:boolean;
    private _dicomDeviceName:string;

    constructor(
        webApp:{
            dcmWebAppName?:string;
            dicomDescription?:string;
            dcmWebServicePath?:string;
            dcmWebServiceClass?:WebServiceClass[];
            dicomNetworkConnectionReference?:any[];
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
        this._dicomDescription = webApp.dicomDescription;
        this._dcmWebServicePath = webApp.dcmWebServicePath;
        this._dcmWebServiceClass = webApp.dcmWebServiceClass;
        this._dicomAETitle = webApp.dicomAETitle;
        this._dicomApplicationCluster = webApp.dicomApplicationCluster;
        this._dicomInstalled = webApp.dicomInstalled;
        this._dcmKeycloakClientID = webApp.dcmKeycloakClientID;
        this._dcmHideNotRejectedInstances = webApp.dcmHideNotRejectedInstances;
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


    get dcmHideNotRejectedInstances(): boolean {
        return this._dcmHideNotRejectedInstances;
    }

    set dcmHideNotRejectedInstances(value: boolean) {
        this._dcmHideNotRejectedInstances = value;
    }

    get dicomDeviceName(): string {
        return this._dicomDeviceName;
    }

    set dicomDeviceName(value: string) {
        this._dicomDeviceName = value;
    }
}