import {DicomLevel, SelectionAction, UniqueSelectIdObject} from "../../interfaces";
import {SelectionsDicomObjects} from "./selections-dicom-objects.model";

export class SelectionActionElement {
    preActionElements?:SelectionsDicomObjects;
    action?:SelectionAction;
    postActionElements?:SelectionsDicomObjects;

    constructor(object:{
        patient?:any,
        study?:any,
        series?:any,
        instance?:any
    }){
        if(this.action){
            this.postActionElements = this.postActionElements || new SelectionsDicomObjects(object);
        }else{
            this.preActionElements = this.preActionElements || new SelectionsDicomObjects(object);
        }
    }

    toggle(dicomLevel:DicomLevel,uniqueSelectIdObject:UniqueSelectIdObject, object){
        if(this.action){
            this.postActionElements.toggle(dicomLevel,uniqueSelectIdObject, object)
        }else{
            this.preActionElements.toggle(dicomLevel,uniqueSelectIdObject, object)
        }
    }
    get size() {
        if(this.action)
            return this.postActionElements.size;
        return this.preActionElements.size;
    }

    getAllAsArray(){
        if(this.action)
            return this.postActionElements.getAllAsArray();
        return this.preActionElements.getAllAsArray();
    }


}