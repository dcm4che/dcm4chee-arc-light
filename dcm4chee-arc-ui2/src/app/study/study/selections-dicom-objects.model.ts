import {DicomLevel, UniqueSelectIdObject} from "../../interfaces";
import * as _ from "lodash";

export class SelectionsDicomObjects {
    private _patient:any;
    private _study:any;
    private _series:any;
    private _instance:any;
    size:number;

    constructor(object:{
        patient?:any,
        study?:any,
        series?:any,
        instance?:any
    }={}){
        this.patient = object.patient;
        this.study = object.study;
        this.series = object.series;
        this.instance = object.instance;
    }


    toggle(dicomLevel:DicomLevel,uniqueSelectIdObject:UniqueSelectIdObject, object){
        if(_.hasIn(this,`${dicomLevel}["${uniqueSelectIdObject.id}"]`)){
            delete this[dicomLevel][uniqueSelectIdObject.id];
            object.selected = false;
        }else{
            this[dicomLevel][uniqueSelectIdObject.id] = {
                uniqueSelectIdObject:uniqueSelectIdObject,
                object:object,
                dicomLevel:dicomLevel
            };
            object.selected = true;
        }
    }

    get patient(): any {
        return this._patient;
    }

    set patient(value: any) {
        this._patient = value;
    }

    get study(): any {
        return this._study;
    }

    set study(value: any) {
        this._study = value;
    }

    get series(): any {
        return this._series;
    }

    set series(value: any) {
        this._series = value;
    }

    get instance(): any {
        return this._instance;
    }

    set instance(value: any) {
        this._instance = value;
    }
}