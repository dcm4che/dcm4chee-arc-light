import {Component, ViewEncapsulation} from '@angular/core';
//import { MatLegacyDialogRef as MatDialogRef } from '@angular/material/legacy-dialog';
import {Globalvar} from '../../../constants/globalvar';
declare var DCM4CHE: any;
import * as _ from 'lodash-es';
import {AppService} from '../../../app.service';
import {SearchPipe} from '../../../pipes/search.pipe';
import {WindowRefService} from "../../../helpers/window-ref.service";
import {j4care} from "../../../helpers/j4care.service";
import {MatDialogRef} from "@angular/material/dialog";
import {EditPatientService} from "./edit-patient.service";

@Component({
    selector: 'app-edit-patient',
    templateUrl: './edit-patient.component.html',
    styleUrls: ['./edit-patient.component.scss'],
    encapsulation: ViewEncapsulation.None
})
export class EditPatientComponent {


    formMode= localStorage.getItem('patient_edit_mode') || "complex";
    opendropdown = false;
    addPatientAttribut = '';
    lastPressedCode;
    options = Globalvar.OPTIONS;
    DCM4CHE = DCM4CHE;
    private _mode;
    private _saveLabel;
    private _titleLabel;
    private _dropdown;
    private _patient: any;
    private _patientkey: any;
    private _externalInternalAetMode;
    private _iod: any;

    simpleForm = {
        schema:undefined,
        model:{}
    }
    constructor(
        public dialogRef: MatDialogRef<EditPatientComponent>,
        public mainservice: AppService,
        private service:EditPatientService
    ) {
        setTimeout(()=>{
            this.simpleForm.schema = this.service.getSimpleFormSchema();
            this.formMode = localStorage.getItem('patient_edit_mode') || "simple";
        },10)
    }
    onChange(newValue, model) {
        _.set(this, model, newValue);
    }

    get iod(): any {
        return this._iod;
    }

    set iod(value: any) {
        this._iod = value;
        this.service.iod = value;
    }
    get mode() {
        return this._mode;
    }

    set mode(value) {
        this._mode = value;
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

    get externalInternalAetMode() {
        return this._externalInternalAetMode;
    }

    set externalInternalAetMode(value) {
        this._externalInternalAetMode = value;
    }

    dialogKeyHandler(e, dialogRef){
        let code = (e.keyCode ? e.keyCode : e.which);
        console.log('in dialogkeyhandler', code);
        if (code === 13){
            dialogRef.close(this._patient);
        }
        if (code === 27){
            if (this.opendropdown){
                this.opendropdown = false;
            }else{
                dialogRef.close(null);
            }
        }
    }
    getKeys(obj){
        if (_.isArray(obj)){
            return obj;
        }else{
            return Object.keys(obj);
        }
    }
    checkClick(e){
        console.log('e', e);
        let code = (e.keyCode ? e.keyCode : e.which);
        console.log('code in checkclick');
        if (!(e.target.id === 'dropdown' || e.target.id === 'addPatientAttribut')){
            this.opendropdown = false;
        }
    }
    pressedKey(e){
        this.opendropdown = true;
        let code = (e.keyCode ? e.keyCode : e.which);
        console.log('in pressedkey', code);
        this.lastPressedCode = code;
        //Tab clicked
        if (code === 9){
            this.opendropdown = false;
        }
        //Enter clicked
        if (code === 13){
            // var filter = $filter("filter");
            // var filtered = filter(this.dropdown, this.addPatientAttribut);
            let filtered = new SearchPipe().transform(this.dropdown, this.addPatientAttribut);
            if (filtered){
                this.opendropdown = true;
            }
            console.log('filtered', filtered);
            let attrcode: any;
            if (WindowRefService.nativeWindow.document.getElementsByClassName('dropdown_element selected').length > 0){
                attrcode = window.document.getElementsByClassName("dropdown_element selected")[0].getAttribute("name");
            }else{
                attrcode = filtered[0].code;
            }
            console.log('patient_attrs not undefined', this._patient.attrs[attrcode]);
            if (this._patient.attrs[attrcode] != undefined){
                if (this._iod[attrcode].multi){
                    this._patient.attrs[attrcode]['Value'].push('');
                    this.addPatientAttribut           = '';
                    this.opendropdown                 = false;
                }else{
                    this.mainservice.showWarning($localize `:@@attribute_already_exists:Attribute already exists!`);
                }
            }else{
                this.patient.attrs[attrcode]  = this._iod[attrcode];
                this.opendropdown = false;
            }
            setTimeout(function(){
                this.lastPressedCode = 0;
            }, 1000);
        }
        //Arrow down pressed
        if (code === 40){
            this.opendropdown = true;
            let i = 0;
            while(i < this.dropdown.length){
                if(this.dropdown[i].selected){
                    this.dropdown[i].selected = false;
                    if(i === this.dropdown.length-1){
                        this.dropdown[0].selected = true;
                    }else{
                        this.dropdown[i+1].selected = true;
                    }
                    i = this.dropdown.length;
                }else{
                    if(i === this.dropdown.length-1){
                        this.dropdown[0].selected = true;
                    }
                    i++;
                }
            }
            let element = WindowRefService.nativeWindow.document.getElementsByClassName('dropdown_element selected')[0];
            let dropdownElement = WindowRefService.nativeWindow.document.getElementsByClassName('dropdown')[0];
            try{
                setTimeout(()=>{
                    element = WindowRefService.nativeWindow.document.getElementsByClassName('dropdown_element selected')[0];
                    dropdownElement = WindowRefService.nativeWindow.document.getElementsByClassName('dropdown')[0];
                    WindowRefService.nativeWindow.document.getElementsByClassName('dropdown_element selected')[0].scrollIntoView({
                        behavior: "smooth",
                        block: "start"
                    });
                },10)

            }catch (e) {

            }
        }
        //Arrow up pressed
        if (code === 38){
            this.opendropdown = true;
            let i = 0;
            while(i < this.dropdown.length){
                if(this.dropdown[i].selected){
                    this.dropdown[i].selected = false;
                    if(i === 0){
                        this.dropdown[this.dropdown.length-1].selected = true;
                    }else{
                        this.dropdown[i-1].selected = true;
                    }
                    break;
                }else{
                    if(i === this.dropdown.length-1){
                        this.dropdown[this.dropdown.length-1].selected = true;
                    }
                }
                i++;
            }
            let element = WindowRefService.nativeWindow.document.getElementsByClassName('dropdown_element selected')[0];
            let dropdownElement = WindowRefService.nativeWindow.document.getElementsByClassName('dropdown')[0];
            try{
                setTimeout(()=>{
                    element = WindowRefService.nativeWindow.document.getElementsByClassName('dropdown_element selected')[0];
                    dropdownElement = WindowRefService.nativeWindow.document.getElementsByClassName('dropdown')[0];
                    WindowRefService.nativeWindow.document.getElementsByClassName('dropdown_element selected')[0].scrollIntoView({
                        behavior: "smooth",
                        block: "start"
                    });
                },10)

            }catch (e) {

            }
        }
        if (code === 27 || code === 9){
            this.opendropdown = false;
        }
    }
    addAttribute(attrcode, patient){
        if (patient.attrs[attrcode]){
            if (this._iod[attrcode].multi){
                        // this.patien.attrs[attrcode]  = this.iod.data[attrcode];
                console.log('multi', this._iod[attrcode]);
                if (patient.attrs[attrcode].vr === 'PN'){
                    patient.attrs[attrcode]['Value'].push({Alphabetic: ''});
                }else{
                    if (patient.attrs[attrcode].vr === 'SQ'){
                        patient.attrs[attrcode]['Value'].push(_.cloneDeep(this._iod[attrcode].Value[0]));
                    }else{
                        patient.attrs[attrcode]['Value'].push('');
                    }
                }
                this.addPatientAttribut           = '';
                this.opendropdown                 = false;
            }else{
                this.mainservice.showWarning($localize `:@@attribute_already_exists:Attribute already exists!`);
                console.log('message attribute already exists');
            }
        }else{
            // console.log("in else", this.dialogRef.componentInstance.patient);
            console.log('this.iodattrcod', this._iod[attrcode]);
             patient.attrs[attrcode]  = _.cloneDeep(this._iod[attrcode]);
             j4care.removeKeyFromObject(patient.attrs[attrcode],"multi");
             j4care.removeKeyFromObject(patient.attrs[attrcode],"required");

  /*           delete patient.attrs[attrcode]["multi"];
             delete patient.attrs[attrcode]["required"];*/
            // patient.attrs[attrcode].Value[0] = "";
            console.log('patient=', patient);
        }
        // this.dialogRef.componentInstance.patient = patient;
        this.opendropdown = false;
        console.log('patient after add ', patient);
    };
    removeAttr(attrcode){
        switch (arguments.length) {
            case 2:
                if (this.patient.attrs[arguments[0]].Value.length === 1){
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

    onSimpleFormChange(event: any) {
        console.log("event",event);
        console.log("mo",this.simpleForm.model)
        return undefined;
    }


    changeFormMode(mode: string) {
        localStorage.setItem('patient_edit_mode',mode);
        this.formMode = mode;
    }
}
