import { Component } from '@angular/core';
import {MdDialogRef} from "@angular/material";
import {Globalvar} from "../../../constants/globalvar";
declare var DCM4CHE: any;
import * as _ from "lodash";

@Component({
  selector: 'app-edit-patient',
  templateUrl: './edit-patient.component.html',
  styles: [`
        .md-overlay-pane{
            width:80%;
        }
    `]
})
export class EditPatientComponent {
    opendropdown = false;

    addPatientAttribut = "";
    private _dropdown
    private _patient:any;
    private _patientkey:any;
    private _iod:any;

    constructor(public dialogRef: MdDialogRef<EditPatientComponent>) {

    }
    options = Globalvar.OPTIONS;
    DCM4CHE = DCM4CHE;
    onChange(newValue, model) {
        _.set(this, model,newValue);
    }
    get iod(): any {
        return this._iod;
    }

    set iod(value: any) {
        this._iod = value;
    }

    get dropdown() {
        return this._dropdown;
    }

    set dropdown(value) {
        this._dropdown = value;
    }

    get patient(): any {
        return this._patient;
    }

    set patient(value: any) {
        this._patient = value;
    }

    get patientkey(): any {
        return this._patientkey;
    }

    set patientkey(value: any) {
        this._patientkey = value;
    }

    getKeys(obj){
        if(_.isArray(obj)){
            return obj;
        }else{
            return Object.keys(obj);
        }
    }
    addAttribute(attrcode, patient){
        console.log("in addattribut",attrcode);
        console.log("this iod",this._iod);

        if(patient.attrs[attrcode]){
            if(this._iod[attrcode].multi){
                        // this.patien.attrs[attrcode]  = this._iod.data[attrcode];
                patient.attrs[attrcode]["Value"].push("");
                this.addPatientAttribut           = "";
                this.opendropdown                 = false;
            }else{
                // DeviceService.msg($scope, {
                //     "title": "Warning",
                //     "text": "Attribute already exists!",
                //     "status": "warning"
                // });
                console.log("message attribute already exists");
            }
        }else{
            // console.log("in else", this.dialogRef.componentInstance.patient);
             patient.attrs[attrcode]  = this._iod[attrcode];
            // patient.attrs[attrcode].Value[0] = "";
            console.log("patient=",patient);
        }
        // this.dialogRef.componentInstance.patient = patient;
        this.opendropdown = false;
        console.log("patient after add ",patient);
    };
    removeAttr(attrcode){
        switch(arguments.length) {
            case 2:
                if(this.patient.attrs[arguments[0]].Value.length === 1){
                    delete  this.patient.attrs[arguments[0]];
                }else{
                    this.patient.attrs[arguments[0]].Value.splice(arguments[1], 1);
                }
                break;
            default:
                delete  this.patient.attrs[arguments[0]];
                break;
        }
    };
}
