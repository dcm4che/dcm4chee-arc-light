import { Component } from '@angular/core';
import {MatDialogRef} from '@angular/material';
import {AppService} from '../../../app.service';
import {Globalvar} from '../../../constants/globalvar';
declare var DCM4CHE: any;
import * as _ from 'lodash';
import {SearchPipe} from '../../../pipes/search.pipe';

@Component({
    selector: 'app-edit-mwl',
    templateUrl: './edit-mwl.component.html',
    styles: [`

    `]
})
export class EditMwlComponent {


    opendropdown = false;

    addmwlAttribut = '';
    lastPressedCode;
    private _saveLabel;
    private _titleLabel;
    private _dropdown;
    private _mwl: any;
    private _mwlkey: any;
    private _iod: any;
    private _mode;

    constructor(public dialogRef: MatDialogRef<EditMwlComponent>, public mainservice: AppService) {

    }
    options = Globalvar.OPTIONS;
    DCM4CHE = DCM4CHE;
    onChange(newValue, model) {
        _.set(this, model, newValue);
    }
    get mode() {
        return this._mode;
    }

    set mode(value) {
        this._mode = value;
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

    get mwl(): any {
        return this._mwl;
    }

    set mwl(value: any) {
        this._mwl = value;
    }

    get mwlkey(): any {
        return this._mwlkey;
    }

    set mwlkey(value: any) {
        this._mwlkey = value;
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
        if (_.isArray(obj)){
            return obj;
        }else{
            return Object.keys(obj);
        }
    }
    dialogKeyHandler(e, dialogRef){
        let code = (e.keyCode ? e.keyCode : e.which);
        console.log('in modality keyhandler', code);
        if (code === 13){
            dialogRef.close(this._mwl);
        }
        if (code === 27){
            if (this.opendropdown){
                this.opendropdown = false;
            }else{
                dialogRef.close(null);
            }
        }
    }
    pressedKey(e){
        console.log('pressedkey');
        let code = (e.keyCode ? e.keyCode : e.which);
        this.lastPressedCode = code;
        if (code === 9 || code === 27){
            this.opendropdown = false;
        }else{
            this.opendropdown = true;
        }
        if (code === 13){
            // var filter = $filter("filter");
            // var filtered = filter(this.dropdown, this.addmwlAttribut);
            let filtered = new SearchPipe().transform(this.dropdown, this.addmwlAttribut);
            if (filtered){
                this.opendropdown = true;
            }
            console.log('filtered', filtered);
            let attrcode: any;
            if ($('.dropdown_element.selected').length){
                attrcode = $('.dropdown_element.selected').attr('name');
            }else{
                attrcode = filtered[0].code;
            }
            if (this._mwl.attrs[attrcode] != undefined){
                if (this._iod[attrcode].multi){
                    this._mwl.attrs[attrcode]['Value'].push('');
                    this.addmwlAttribut           = '';
                    this.opendropdown                 = false;
                }else{
                    this.mainservice.setMessage({
                        'title': 'Warning',
                        'text': 'Attribute already exists!',
                        'status': 'warning'
                    });
                }
            }else{
                this._mwl.attrs[attrcode]  = this.iod[attrcode];
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
    }
    addAttribute(attrcode){
        if (attrcode.indexOf(':') > -1){
            let codes =  attrcode.split(':');
            if (codes[0] === '00400100'){
                if (this._mwl.attrs[codes[0]].Value[0][codes[1]] != undefined){
                    if (this.iod[codes[0]].Value[0][codes[1]].multi){
                        // this._mwl.attrs[attrcode]  = this.iod[attrcode];
                        // console.log("this.iod",this.iod);
                        // console.log("this._mwl",this._mwl);
                        if (this.iod[codes[0]].Value[0][codes[1]].vr === 'SQ'){
                            // this._mwl.attrs[codes[0]].Value[0][codes[1]]["Value"] = this._mwl.attrs[codes[0]].Value[0][codes[1]]["Value"] || this.iod[codes[0]].Value[0][codes[1]].Value;
                            // console.log("this.iod[codes[0]].Value[0][codes[1]].Value",this.iod[codes[0]].Value[0][codes[1]].Value);
                            this._mwl.attrs[codes[0]].Value[0][codes[1]]['Value'].push(this.iod[codes[0]].Value[0][codes[1]].Value[0]);
                        }else{
                            this._mwl.attrs[codes[0]].Value[0][codes[1]]['Value'] = this._mwl.attrs[codes[0]].Value[0][codes[1]]['Value'] || [];
                            this._mwl.attrs[codes[0]].Value[0][codes[1]]['Value'].push('');
                        }
                        this.addmwlAttribut           = '';
                        this.opendropdown                 = false;
                    }else{
                        this.mainservice.setMessage({
                            'title': 'Warning',
                            'text': 'Attribute already exists!',
                            'status': 'warning'
                        });
                    }
                }else{
                    this._mwl.attrs[codes[0]].Value[0][codes[1]]  = this.iod[codes[0]].Value[0][codes[1]];
                }
            }else{
                console.error('error, code 00400100 not found on the 0 position');
            }
        }else{
            if (this._mwl.attrs[attrcode] != undefined){
                if (this.iod[attrcode].multi){
                    // this._mwl.attrs[attrcode]  = this.iod[attrcode];
                    this._mwl.attrs[attrcode]['Value'].push('');
                    this.addmwlAttribut  = '';
                    this.opendropdown    = false;
                }else{
                    this.mainservice.setMessage({
                        'title': 'Warning',
                        'text': 'Attribute already exists!',
                        'status': 'warning'
                    });
                }
            }else{
                        this._mwl.attrs[attrcode]  = this.iod[attrcode];
            }
        }
        // this.items = $filter("mwl")(this._mwl.attrs,$scope.iod);
    };
    removeAttr(attrcode){
        switch (arguments.length) {
            case 2:
                if (this._mwl.attrs[arguments[0]].Value.length === 1){
                    delete  this._mwl.attrs[arguments[0]];
                }else{
                    this._mwl.attrs[arguments[0]].Value.splice(arguments[1], 1);
                }
                break;
            default:
                delete  this._mwl.attrs[arguments[0]];
                break;
        }
    };
}
