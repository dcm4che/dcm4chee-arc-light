import {DicomLevel, UniqueSelectIdObject} from "../../interfaces";
import * as _ from "lodash";

export class SelectionsDicomObjects {
    private _patient:any;
    private _study:any;
    private _series:any;
    private _instance:any;
    private _currentIndexes;
    size:number;

    constructor(object:{
        patient?:any,
        study?:any,
        series?:any,
        instance?:any
    }={}){
        this.size = 0;
        if(object.patient){
            this.patient = object.patient;
            this.size += Object.keys(object.patient).length
        }else{
            this._patient = {};
        }
        if(object.study){
            this.study = object.study;
            this.size += Object.keys(object.study).length
        }else{
            this._study = {};
        }
        if(object.series){
            this.series = object.series;
            this.size += Object.keys(object.series).length
        }else{
            this._series = {};
        }
        if(object.instance){
            this.instance = object.instance;
            this.size += Object.keys(object.instance).length
        }else{
            this._instance = {};
        }
    }


    toggle(dicomLevel:DicomLevel,uniqueSelectIdObject:UniqueSelectIdObject, object){
        if(_.hasIn(this,`${dicomLevel}["${uniqueSelectIdObject.id}"]`)){
            delete this[dicomLevel][uniqueSelectIdObject.id];
            object.selected = false;
            this.size--;
            this.currentIndexes.splice(this.currentIndexes.indexOf(uniqueSelectIdObject.id), 1);
        }else{
            this[dicomLevel][uniqueSelectIdObject.id] = {
                uniqueSelectIdObject:uniqueSelectIdObject,
                object:object,
                dicomLevel:dicomLevel
            };
            this.currentIndexes = this.currentIndexes || [];
            this.currentIndexes.push(uniqueSelectIdObject.id);
            object.selected = true;
            this.size++;
        }
    }


    getAttrs(dicomLevel:DicomLevel){
        this[dicomLevel].map(o=>o.object.attrs);
    }
    getAllAsArray(){
        return [
            ..._.values(this._patient),
            ..._.values(this._study),
            ..._.values(this._series),
            ..._.values(this._instance)
        ]
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

    get currentIndexes() {
        return this._currentIndexes;
    }

    set currentIndexes(value) {
        this._currentIndexes = value;
    }
}