import * as _ from "lodash";

export class Aet {
    private _dicomAETitle:string;
    private _dicomDescription:string;
    private _dcmAcceptedUserRole:string[];
    private _dcmAllowDeletePatient:string;
    private _dcmAllowDeleteStudyPermanently:string;
    private _dicomApplicationCluster:string[];
    private _dicomAssociationAcceptor:boolean;
    private _dicomAssociationInitiator:boolean;
    private _dicomNetworkConnection:any;
    private _dcmHideNotRejectedInstances:boolean;
    private _value;
    private _text;
    private _title;

    constructor(aetObject){
        if(typeof aetObject === "string"){
            this._dicomAETitle = aetObject;
        }else{
            [
                "dicomAETitle",
                "dicomDescription",
                "dcmAcceptedUserRole",
                "dcmAllowDeletePatient",
                "dcmAllowDeleteStudyPermanently",
                "dicomApplicationCluster",
                "dicomAssociationAcceptor",
                "dicomAssociationInitiator",
                "dicomNetworkConnection"
            ].forEach(attr=>{
                if(_.hasIn(aetObject,attr))
                    this[attr] = aetObject[attr];
            });
        }
    }

    get dcmAcceptedUserRole(): string[] {
        return this._dcmAcceptedUserRole;
    }

    set dcmAcceptedUserRole(value: string[]) {
        this._dcmAcceptedUserRole = value;
    }

    get dcmAllowDeletePatient(): string {
        return this._dcmAllowDeletePatient;
    }

    set dcmAllowDeletePatient(value: string) {
        this._dcmAllowDeletePatient = value;
    }

    get dcmAllowDeleteStudyPermanently(): string {
        return this._dcmAllowDeleteStudyPermanently;
    }

    set dcmAllowDeleteStudyPermanently(value: string) {
        this._dcmAllowDeleteStudyPermanently = value;
    }

    get dicomAETitle(): string {
        return this._dicomAETitle;
    }

    set dicomAETitle(value: string) {
        this._dicomAETitle = value;
    }

    get dicomDescription(): string {
        return this._dicomDescription;
    }

    set dicomDescription(value: string) {
        this._dicomDescription = value;
    }

    get dicomApplicationCluster(): string[] {
        return this._dicomApplicationCluster;
    }

    set dicomApplicationCluster(value: string[]) {
        this._dicomApplicationCluster = value;
    }

    get dicomAssociationAcceptor(): boolean {
        return this._dicomAssociationAcceptor;
    }

    set dicomAssociationAcceptor(value: boolean) {
        this._dicomAssociationAcceptor = value;
    }

    get dicomAssociationInitiator(): boolean {
        return this._dicomAssociationInitiator;
    }

    set dicomAssociationInitiator(value: boolean) {
        this._dicomAssociationInitiator = value;
    }

    get dicomNetworkConnection(): any {
        return this._dicomNetworkConnection;
    }

    set dicomNetworkConnection(value: any) {
        this._dicomNetworkConnection = value;
    }

    get dcmHideNotRejectedInstances(): boolean {
        return this._dcmHideNotRejectedInstances;
    }

    set dcmHideNotRejectedInstances(value: boolean) {
        this._dcmHideNotRejectedInstances = value;
    }

    get value() {
        return this._dicomAETitle;
    }

    get text() {
        return this._dicomAETitle;
    }

    get title() {
        return this._dicomDescription || this._dicomAETitle;
    }
}
