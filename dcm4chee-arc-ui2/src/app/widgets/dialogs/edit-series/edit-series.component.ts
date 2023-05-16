import {Component, EventEmitter, Input, Output} from '@angular/core';
import {AppService} from '../../../app.service';
import { MatDialogRef } from '@angular/material/dialog';
import {Globalvar} from '../../../constants/globalvar';
import {SearchPipe} from '../../../pipes/search.pipe';
declare var DCM4CHE: any;
import * as _ from 'lodash-es';
import {WindowRefService} from "../../../helpers/window-ref.service";


@Component({
    selector: 'edit-series',
    templateUrl: './edit-series.component.html',
    styles: [`

    `]
})
export class EditSeriesComponent{


    opendropdown = false;

    addSeriesAttribut = '';
    lastPressedCode;
    sourceOfPrevVals = '';
    private _saveLabel;
    private _titleLabel;
    private _dropdown;
    private _series: any;
    private _serieskey: any;
    private _iod: any;
    private _mode;

    @Output() onChange = new EventEmitter();

    options = Globalvar.OPTIONS;

    DCM4CHE = DCM4CHE;
    constructor(public dialogRef: MatDialogRef<EditSeriesComponent>, public mainservice: AppService) {
        console.log("this.series",this._series);
    }

    change(){
        this.onChange.emit(this.series);
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

    get series(): any {
        return this._series;
    }

    @Input()
    set series(value: any) {
        this._series = value;
    }

    get serieskey(): any {
        return this._serieskey;
    }

    set serieskey(value: any) {
        this._serieskey = value;
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
            dialogRef.close(this._series);
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
        if (this._series.attrs[attrcode] != undefined){
            if (this._iod[attrcode].multi){
                this._series.attrs[attrcode]['Value'].push('');
                this.addSeriesAttribut           = '';
                this.opendropdown                 = false;
            }else{
                this.mainservice.showWarning($localize `:@@attribute_already_exists:Attribute already exists!`);
            }
        }else{
            this._series.attrs[attrcode]  = this._iod[attrcode];
        }
    };

    pressedKey(e){
        console.log('in pressedkey');
        this.opendropdown = true;
        let code = (e.keyCode ? e.keyCode : e.which);
        this.lastPressedCode = code;
        let attrcode: any;
        if (code === 13){
            let filtered =  new SearchPipe().transform(this.dropdown, this.addSeriesAttribut);
            if (filtered){
                this.opendropdown = true;
            }
            if (WindowRefService.nativeWindow.document.getElementsByClassName('dropdown_element selected').length > 0){
                attrcode = window.document.getElementsByClassName("dropdown_element selected")[0].getAttribute("name");;
            }else{
                attrcode = filtered[0].code;
            }
            if (this._series.attrs[attrcode] != undefined){
                if (this._iod[attrcode].multi){
                    this._series.attrs[attrcode]['Value'].push('');
                    this.addSeriesAttribut           = '';
                    this.opendropdown                 = false;
                }else{
                    this.mainservice.showWarning($localize `:@@attribute_already_exists:Attribute already exists!`);
                }
            }else{
                this._series.attrs[attrcode]  = this._iod[attrcode];
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
                if (this._series.attrs[arguments[0]].Value.length === 1){
                    delete  this._series.attrs[arguments[0]];
                }else{
                    this._series.attrs[arguments[0]].Value.splice(arguments[1], 1);
                }
                break;
            default:
                delete  this._series.attrs[arguments[0]];
                break;
        }
    };
}
