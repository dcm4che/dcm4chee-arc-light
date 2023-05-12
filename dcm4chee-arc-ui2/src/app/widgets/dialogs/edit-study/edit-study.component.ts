import {Component, EventEmitter, Input, Output} from '@angular/core';
import {AppService} from '../../../app.service';
import { MatDialogRef } from '@angular/material/dialog';
import {Globalvar} from '../../../constants/globalvar';
import {SearchPipe} from '../../../pipes/search.pipe';
declare var DCM4CHE: any;
import * as _ from 'lodash-es';
import {WindowRefService} from "../../../helpers/window-ref.service";
import {SelectDropdown} from "../../../interfaces";


@Component({
    selector: 'edit-study',
    templateUrl: './edit-study.component.html',
    styles: [`

    `]
})
export class EditStudyComponent{


    opendropdown = false;

    addStudyAttribut = '';
    lastPressedCode;
    private _saveLabel;
    private _titleLabel;
    private _dropdown;
   // private _study: any;
    private _studykey: any;
    private _iod: any;
    private _mode;
    reasonForModification:SelectDropdown<any>[] = [
        new SelectDropdown("COERCE", "COERCE"),
        new SelectDropdown("CORRECT", "CORRECT"),
    ]

    private _studyResult = {
        study: undefined,
        sourceOfPrevVals: '',
        reasonForModificationResult: undefined
    }

    @Output() onChange = new EventEmitter();

    options = Globalvar.OPTIONS;

    DCM4CHE = DCM4CHE;
    constructor(public dialogRef: MatDialogRef<EditStudyComponent>, public mainservice: AppService) {
        console.log("this.study",this._studyResult.study);
/*
        setTimeout(function(){
            if(this._mode === "create"){
                $(".edit-patient .0020000D").attr("title","To generate it automatically leave it blank");
                $(".edit-patient .0020000D").attr("placeholder","To generate it automatically leave it blank");
            }
            if(this._mode === "_edit"){
                $(".edit-patient .0020000D").attr("disabled","disabled");
                $(".edit-patient span.0020000D").remove();
            }
            $(".editform .schema-form-fieldset > legend").append('<span class="glyphicon glyphicon-triangle-right"></span>');
            $(".editform .schema-form-fieldset > legend").bind("click",function(){
                $(this).siblings("sf-decorator").toggle();
                var icon = $(this).find(".glyphicon");
                if(icon.hasClass('glyphicon-triangle-right')){
                    icon.removeClass('glyphicon-triangle-right').addClass('glyphicon-triangle-bottom');
                }else{
                    icon.removeClass('glyphicon-triangle-bottom').addClass('glyphicon-triangle-right');
                }
            });
            //Click event handling
            /!*
             $(".addPatientAttribut").bind("keydown",function(e){

             });
             $(".editform .schema-form-fieldset > sf-decorator").hide();*!/
        },1000);*/
    }

    change(){
        this.onChange.emit(this.studyResult.study);
    }

    get mode() {
        return this._mode;
    }

    @Input()
    set mode(value) {
        this._mode = value;
    }
    get saveLabel() {
        return this._saveLabel;
    }

    set saveLabel(value) {
        this._saveLabel = value;
    }

    get titleLabel() {
        return this._titleLabel;
    }

    set titleLabel(value) {
        this._titleLabel = value;
    }

    get dropdown() {
        return this._dropdown;
    }

    @Input()
    set dropdown(value) {
        this._dropdown = value;
    }

    // get study(): any {
    //     return this._study;
    // }
    //
    // @Input()
    // set study(value: any) {
    //     this._study = value;
    // }

    get studykey(): any {
        return this._studykey;
    }

    set studykey(value: any) {
        this._studykey = value;
    }

    get studyResult(): any {
        return this._studyResult;
    }

    set studyResult(value: any) {
        this._studyResult = value;
    }

    get iod(): any {
        return this._iod;
    }

    @Input()
    set iod(value: any) {
        this._iod = value;
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
    dialogKeyHandler(e, dialogRef){
        let code = (e.keyCode ? e.keyCode : e.which);
        console.log('in modality keyhandler', code);
        if (code === 13){
            dialogRef.close(this._studyResult.study);
        }
        if (code === 27){
            if (this.opendropdown){
                this.opendropdown = false;
            }else{
                dialogRef.close(null);
            }
        }
    };

    addAttribute(attrcode){
        if (this._studyResult.study.attrs[attrcode] != undefined){
            if (this._iod[attrcode].multi){
                this._studyResult.study.attrs[attrcode]['Value'].push('');
                this.addStudyAttribut           = '';
                this.opendropdown                 = false;
            }else{
                this.mainservice.showWarning($localize `:@@attribute_already_exists:Attribute already exists!`);
            }
        }else{
            this._studyResult.study.attrs[attrcode]  = this._iod[attrcode];
        }
    };

    pressedKey(e){
        console.log('in pressedkey');
        this.opendropdown = true;
        let code = (e.keyCode ? e.keyCode : e.which);
        this.lastPressedCode = code;
        let attrcode: any;
        if (code === 13){
            let filtered =  new SearchPipe().transform(this.dropdown, this.addStudyAttribut);
            if (filtered){
                this.opendropdown = true;
            }
            if (WindowRefService.nativeWindow.document.getElementsByClassName('dropdown_element selected').length > 0){
                attrcode = window.document.getElementsByClassName("dropdown_element selected")[0].getAttribute("name");;
            }else{
                attrcode = filtered[0].code;
            }
            if (this._studyResult.study.attrs[attrcode] != undefined){
                if (this._iod[attrcode].multi){
                    this._studyResult.study.attrs[attrcode]['Value'].push('');
                    this.addStudyAttribut           = '';
                    this.opendropdown                 = false;
                }else{
                    this.mainservice.showWarning($localize `:@@attribute_already_exists:Attribute already exists!`);
                }
            }else{
                this._studyResult.study.attrs[attrcode]  = this._iod[attrcode];
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
    };
    removeAttr(attrcode){
        switch (arguments.length) {
            case 2:
                if (this._studyResult.study.attrs[arguments[0]].Value.length === 1){
                    delete  this._studyResult.study.attrs[arguments[0]];
                }else{
                    this._studyResult.study.attrs[arguments[0]].Value.splice(arguments[1], 1);
                }
                break;
            default:
                delete  this._studyResult.study.attrs[arguments[0]];
                break;
        }
    };
}
