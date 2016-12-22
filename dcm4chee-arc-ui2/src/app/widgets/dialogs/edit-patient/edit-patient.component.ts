import { Component } from '@angular/core';
import {MdDialogRef} from "@angular/material";
import {Globalvar} from "../../../constants/globalvar";
declare var DCM4CHE: any;
import * as _ from "lodash";
import {AppService} from "../../../app.service";
import {SearchPipe} from "../../../pipes/search.pipe";

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
    lastPressedCode;
    private _saveLabel;
    private _titleLabel;
    private _dropdown
    private _patient:any;
    private _patientkey:any;
    private _iod:any;

    constructor(public dialogRef: MdDialogRef<EditPatientComponent>, public mainservice:AppService) {

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
    get saveLabel(): string {
        return this._saveLabel;
    }

    set saveLabel(value: string) {
        this._saveLabel = value;
    }

    get titleLabel(): string {
        return this._titleLabel;
    }

    set titleLabel(value: string) {
        this._titleLabel = value;
    }
    getKeys(obj){
        if(_.isArray(obj)){
            return obj;
        }else{
            return Object.keys(obj);
        }
    }
    pressedKey(e){
        this.opendropdown = true;
        var code = (e.keyCode ? e.keyCode : e.which);
        this.lastPressedCode = code;
        if(code === 13){
            // var filter = $filter("filter");
            // var filtered = filter(this.dropdown, this.addPatientAttribut);
            let filtered = new SearchPipe().transform(this.dropdown,this.addPatientAttribut)
            if(filtered){
                this.opendropdown = true;
            }
            console.log("filtered",filtered);
            let attrcode:any;
            if($(".dropdown_element.selected").length){
                attrcode = $(".dropdown_element.selected").attr("name");
            }else{
                attrcode = filtered[0].code;
            }
            if(this._patient.attrs[attrcode] != undefined){
                if(this._iod[attrcode].multi){
                    this._patient.attrs[attrcode]["Value"].push("");
                    this.addPatientAttribut           = "";
                    this.opendropdown                 = false;
                }else{
                    this.mainservice.setMessage({
                        "title": "Warning",
                        "text": "Attribute already exists!",
                        "status": "warning"
                    });
                }
            }else{
                this.patient.attrs[attrcode]  = this.iod[attrcode];
            }
            setTimeout(function(){
                this.lastPressedCode = 0;
            },1000);
        }
        //Arrow down pressed
        if(code === 40){
            this.opendropdown = true;
            if(!$(".dropdown_element.selected").length){
                $(".dropdown_element").first().addClass('selected');
            }else{
                if($(".dropdown_element.selected").next().length){
                    $(".dropdown_element.selected").removeClass('selected').next().addClass('selected');
                }else{
                    $(".dropdown_element.selected").removeClass('selected');
                    $(".dropdown_element").first().addClass('selected');
                }
            }

            if($(".dropdown_element.selected").position()){
                $('.dropdown').scrollTop($('.dropdown').scrollTop() + $(".dropdown_element.selected").position().top - $('.dropdown').height()/2 + $(".dropdown_element.selected").height()/2);
            }
        }
        //Arrow up pressed
        if(code === 38){
            this.opendropdown = true;
            if(!$(".dropdown_element.selected").length){
                $(".dropdown_element").prev().addClass('selected');
            }else{
                if($(".dropdown_element.selected").index() === 0){
                    $(".dropdown_element.selected").removeClass('selected');
                    $(".dropdown_element").last().addClass('selected');
                }else{
                    $(".dropdown_element.selected").removeClass('selected').prev().addClass('selected');
                }
            }
            $('.dropdown').scrollTop($('.dropdown').scrollTop() + $(".dropdown_element.selected").position().top - $('.dropdown').height()/2 + $(".dropdown_element.selected").height()/2);
        }
        if(code === 27){
            this.opendropdown = false;
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
                this.mainservice.setMessage({
                    "title": "Warning",
                    "text": "Attribute already exists!",
                    "status": "warning"
                });
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
