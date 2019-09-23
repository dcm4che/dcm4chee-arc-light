import {Component, EventEmitter, Input, Output} from '@angular/core';
import {AppService} from '../../../app.service';
import {MatDialogRef} from '@angular/material';
import {Globalvar} from '../../../constants/globalvar';
import {SearchPipe} from '../../../pipes/search.pipe';
declare var DCM4CHE: any;
import * as _ from 'lodash';


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
    private _study: any;
    private _studykey: any;
    private _iod: any;
    private _mode;

    @Output() onChange = new EventEmitter();

    options = Globalvar.OPTIONS;

    DCM4CHE = DCM4CHE;
    constructor(public dialogRef: MatDialogRef<EditStudyComponent>, public mainservice: AppService) {
        console.log("this.study",this._study);
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
        this.onChange.emit(this.study);
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

    get study(): any {
        return this._study;
    }

    @Input()
    set study(value: any) {
        this._study = value;
    }

    get studykey(): any {
        return this._studykey;
    }

    set studykey(value: any) {
        this._studykey = value;
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
            dialogRef.close(this._study);
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
        if (this._study.attrs[attrcode] != undefined){
            if (this._iod[attrcode].multi){
                this._study.attrs[attrcode]['Value'].push('');
                this.addStudyAttribut           = '';
                this.opendropdown                 = false;
            }else{
                this.mainservice.setMessage({
                    'title': 'Warning',
                    'text': 'Attribute already exists!',
                    'status': 'warning'
                });
            }
        }else{
            this._study.attrs[attrcode]  = this._iod[attrcode];
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
            if ($('.dropdown_element.selected').length){
                attrcode = $('.dropdown_element.selected').attr('name');
            }else{
                attrcode = filtered[0].code;
            }
            if (this._study.attrs[attrcode] != undefined){
                if (this._iod[attrcode].multi){
                    this._study.attrs[attrcode]['Value'].push('');
                    this.addStudyAttribut           = '';
                    this.opendropdown                 = false;
                }else{
                    this.mainservice.setMessage({
                        'title': 'Warning',
                        'text': 'Attribute already exists!',
                        'status': 'warning'
                    });
                }
            }else{
                this._study.attrs[attrcode]  = this._iod[attrcode];
                this.opendropdown = false;
            }
            setTimeout(function(){
                this.lastPressedCode = 0;
            }, 1000);
        }
        //Arrow down pressed
        if (code === 40){
            this.opendropdown = true;
            if (!$('.dropdown_element.selected').length){
                $('.dropdown_element').first().addClass('selected');
            }else{
                if ($('.dropdown_element.selected').next().length){
                    $('.dropdown_element.selected').removeClass('selected').next().addClass('selected');
                }else{
                    $('.dropdown_element.selected').removeClass('selected');
                    $('.dropdown_element').first().addClass('selected');
                }
            }

            if ($('.dropdown_element.selected').position()){
                $('.dropdown').scrollTop($('.dropdown').scrollTop() + $('.dropdown_element.selected').position().top - $('.dropdown').height() / 2 + $('.dropdown_element.selected').height() / 2);
            }
        }
        //Arrow up pressed
        if (code === 38){
            this.opendropdown = true;
            if (!$('.dropdown_element.selected').length){
                $('.dropdown_element').prev().addClass('selected');
            }else{
                if ($('.dropdown_element.selected').index() === 0){
                    $('.dropdown_element.selected').removeClass('selected');
                    $('.dropdown_element').last().addClass('selected');
                }else{
                    $('.dropdown_element.selected').removeClass('selected').prev().addClass('selected');
                }
            }
            $('.dropdown').scrollTop($('.dropdown').scrollTop() + $('.dropdown_element.selected').position().top - $('.dropdown').height() / 2 + $('.dropdown_element.selected').height() / 2);
        }
        if (code === 27 || code === 9){
            this.opendropdown = false;
        }
    };
    removeAttr(attrcode){
        switch (arguments.length) {
            case 2:
                if (this._study.attrs[arguments[0]].Value.length === 1){
                    delete  this._study.attrs[arguments[0]];
                }else{
                    this._study.attrs[arguments[0]].Value.splice(arguments[1], 1);
                }
                break;
            default:
                delete  this._study.attrs[arguments[0]];
                break;
        }
    };
}
