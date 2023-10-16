import { Component } from '@angular/core';
import { MatLegacyDialogRef as MatDialogRef } from '@angular/material/legacy-dialog';
import {Globalvar} from '../../../constants/globalvar';
declare var DCM4CHE: any;
import * as _ from 'lodash-es';
import {AppService} from '../../../app.service';
import {SearchPipe} from '../../../pipes/search.pipe';
import {WindowRefService} from "../../../helpers/window-ref.service";
import {SelectDropdown, UPSModifyMode, UPSSubscribeType} from "../../../interfaces";
import {j4care} from "../../../helpers/j4care.service";
import {Aet} from "../../../models/aet";
import {J4careHttpService} from "../../../helpers/j4care-http.service";

@Component({
    selector: 'modify-ups',
    templateUrl: './modify-ups.component.html',
    styleUrls: ['./modify-ups.component.scss']
})
export class ModifyUpsComponent {


    opendropdown = false;
    private _aes;
    addPatientAttribut = '';
    lastPressedCode;
    options = Globalvar.OPTIONS;
    DCM4CHE = DCM4CHE;
    private _mode:UPSModifyMode;
    private _subscribeType:UPSSubscribeType;
    private _saveLabel;
    private _titleLabel;
    private _dropdown;
    private _ups: any;
    private _upskey: any;
    private _externalInternalAetMode;
    iod: any;
    templateParameter:string = "no_template";

    private _result = {
        subscribeMode: 'filtered',
        subscriberAET: undefined,
        deletionlock:false
    };

    constructor(public dialogRef: MatDialogRef<ModifyUpsComponent>, private $http:J4careHttpService, public mainservice: AppService) {
    }
    onChange(newValue, model) {
        _.set(this, model, newValue);
    }
    get mode():UPSModifyMode {
        return this._mode;
    }

    set mode(value:UPSModifyMode) {
        this._mode = value;
    }

    get subscribeType(): UPSSubscribeType {
        return this._subscribeType;
    }

    set subscribeType(value: UPSSubscribeType) {
        this._subscribeType = value;
    }

    get dropdown() {
        return this._dropdown;
    }

    set dropdown(value) {
        this._dropdown = value;
    }

    get ups(): any {
        return this._ups;
    }

    set ups(value: any) {
        this._ups = value;
    }

    get upskey(): any {
        return this._upskey;
    }

    set upskey(value: any) {
        this._upskey = value;
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
            dialogRef.close(this._ups);
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
            console.log('ups_attrs not undefined', this._ups.attrs[attrcode]);
            if (this._ups.attrs[attrcode] != undefined){
                if (this.iod[attrcode].multi){
                    this._ups.attrs[attrcode]['Value'].push('');
                    this.addPatientAttribut           = '';
                    this.opendropdown                 = false;
                }else{
                    this.mainservice.showWarning($localize `:@@attribute_already_exists:Attribute already exists!`);
                }
            }else{
                this.ups.attrs[attrcode]  = this.iod[attrcode];
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
    addAttribute(attrcode, ups){
        if (ups.attrs[attrcode]){
            if (this.iod[attrcode].multi){
                        // this.patien.attrs[attrcode]  = this.iod.data[attrcode];
                console.log('multi', this.iod[attrcode]);
                if (ups.attrs[attrcode].vr === 'PN'){
                    ups.attrs[attrcode]['Value'].push({Alphabetic: ''});
                }else{
                    if (ups.attrs[attrcode].vr === 'SQ'){
                        ups.attrs[attrcode]['Value'].push(_.cloneDeep(this.iod[attrcode].Value[0]));
                    }else{
                        ups.attrs[attrcode]['Value'].push('');
                    }
                }
                this.addPatientAttribut           = '';
                this.opendropdown                 = false;
            }else{
                this.mainservice.showWarning($localize `:@@attribute_already_exists:Attribute already exists!`);
                console.log('message attribute already exists');
            }
        }else{
            // console.log("in else", this.dialogRef.componentInstance.ups);
            console.log('this.iodattrcod', this.iod[attrcode]);
             ups.attrs[attrcode]  = _.cloneDeep(this.iod[attrcode]);
             delete ups.attrs[attrcode]["multi"];
             delete ups.attrs[attrcode]["required"];
            // ups.attrs[attrcode].Value[0] = "";
            console.log('ups=', ups);
        }
        // this.dialogRef.componentInstance.ups = ups;
        this.opendropdown = false;
        console.log('ups after add ', ups);
    };
    removeAttr(attrcode){
        switch (arguments.length) {
            case 2:
                if (this.ups.attrs[arguments[0]].Value.length === 1){
                    delete  this.ups.attrs[arguments[0]];
                }else{
                    this.ups.attrs[arguments[0]].Value.splice(arguments[1], 1);
                }
                break;
            default:
                delete  this.ups.attrs[arguments[0]];
                break;
        }
    };

    get aes() {
        return this._aes;
    }

    set aes(value) {
        this._aes = value;
    }

    aesOption:SelectDropdown<Aet>[];
    ngOnInit() {
        this.getAes();
    }


    get result(){
        return this._result;
    }

    set result(value: any) {
        this._result = value;
    }

    getAes(){
        let $this = this;
        this.$http.get(
            `${j4care.addLastSlash(this.mainservice.baseUrl)}aes`
        )
            // .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res; }catch (e){ resjson = [];} return resjson;})
            .subscribe((response) => {
                $this.aes = response;
                this.aesOption = this.aes.map((ae:Aet)=>{
                    return new SelectDropdown<Aet>(ae.dicomAETitle,ae.dicomAETitle,ae.dicomDescription);
                });
                $this._result.subscriberAET = $this._result.subscriberAET || $this.aes[0].dicomAETitle;
                if ($this.mainservice.global && !$this.mainservice.global.aes){
                    let global = _.cloneDeep($this.mainservice.global);
                    global.aes = response;
                    $this.mainservice.setGlobal(global);
                }else{
                    if ($this.mainservice.global && $this.mainservice.global.aes){
                        $this.mainservice.global.aes = response;
                    }else{
                        $this.mainservice.setGlobal({aes: response});
                    }
                }
            }, (response) => {
                // vex.dialog.alert("Error loading aes, please reload the page and try again!");
            });
    }
}
