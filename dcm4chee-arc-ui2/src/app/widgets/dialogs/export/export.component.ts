import {Component, OnDestroy, OnInit} from '@angular/core';
//import { MatLegacyDialogRef as MatDialogRef } from '@angular/material/legacy-dialog';
import {AppService} from '../../../app.service';
import * as _ from 'lodash-es';
import {J4careHttpService} from "../../../helpers/j4care-http.service";
import {DropdownList} from "../../../helpers/form/dropdown-list";
import {SelectDropdown} from "../../../interfaces";
import {Aet} from "../../../models/aet";
import {DcmWebApp} from "../../../models/dcm-web-app";
import {j4care} from "../../../helpers/j4care.service";
import {MatDialogRef} from "@angular/material/dialog";
import {FormsModule} from '@angular/forms';
import {RangePickerComponent} from '../../range-picker/range-picker.component';
import {MatOption, MatSelect} from '@angular/material/select';
import {DcmDropDownComponent} from '../../dcm-drop-down/dcm-drop-down.component';
import {CommonModule} from '@angular/common';

@Component({
    selector: 'app-export',
    templateUrl: './export.component.html',
    imports: [
        MatSelect,
        FormsModule,
        RangePickerComponent,
        MatOption,
        DcmDropDownComponent,
        CommonModule
    ],
    standalone: true
})
export class ExportDialogComponent implements OnInit, OnDestroy{

    private _noDicomExporters;
    private _aes;
    private _webapps;
    private _dicomPrefixes;
    _ = _;
    private _title;
    private _okButtonLabel;
    private _externalAetMode;
    private _mode;
    private _count;
    private _subTitle;
    private _quantity;
    newStudyPage = false;
    queues;
    private _result = {
        exportType: 'dicom',
        selectedAet: undefined,
        destinationAET: undefined,
        selectedStowWebapp: undefined,
        selectedExporter: undefined,
        queue:false,
        externalAET:undefined,
        dicomPrefix: undefined,
        checkboxes: {
            'only-stgcmt': undefined,
            'only-ian': undefined
        }
    };
    private _preselectedAet;
    constructor(public dialogRef: MatDialogRef<ExportDialogComponent>, private $http:J4careHttpService, private mainservice: AppService) {
    }
    ngOnInit() {
        this.getAes();
        this.getStowWebApps();
    }

    get subTitle() {
        return this._subTitle;
    }

    set subTitle(value) {
        this._subTitle = value;
    }

    get preselectedAet() {
        return this._preselectedAet;
    }

    set preselectedAet(value) {
        this._result.selectedAet = value;
        this._preselectedAet = value;
    }
    set preselectedExternalAET(value){
        this._result.externalAET = value;
    }
    get result(){
        return this._result;
    }

    set result(value: any) {
        this._result = value;
    }

    get okButtonLabel() {
        return this._okButtonLabel;
    }

    set okButtonLabel(value) {
        this._okButtonLabel = value;
    }

    get title() {
        return this._title;
    }

    set title(value) {
        this._title = value;
    }

    get dicomPrefixes() {
        return this._dicomPrefixes;
    }

    set dicomPrefixes(value) {
        this._dicomPrefixes = value;
    }

    get noDicomExporters() {
        return this._noDicomExporters;
    }

    set noDicomExporters(value) {
        this._noDicomExporters = value;
    }

    get aes() {
        return this._aes;
    }

    set aes(value) {
        this._aes = value;
    }

    get webapps() {
        return this._webapps;
    }

    set webapps(value) {
        this._webapps = value;
    }

    get externalInternalAetMode() {
        return this._externalAetMode;
    }

    set externalInternalAetMode(value) {
        this._externalAetMode = value;
    }
    get mode() {
        return this._mode;
    }

    set mode(value) {
        this._mode = value;
    }

    get count() {
        return this._count;
    }

    set count(value) {
        this._count = value;
    }

    aesOption:SelectDropdown<Aet>[];
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
            let resultTemp = JSON.parse(localStorage.getItem('export_result'));
            if(resultTemp){
                $this._result = resultTemp;
            }
            $this._result.selectedAet = $this._result.selectedAet || $this.aes[0].dicomAETitle;
            $this._result.destinationAET = $this._result.destinationAET || $this.aes[0].dicomAETitle;

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

    stowWebAppsOption:SelectDropdown<DcmWebApp>[];
    getStowWebApps(){
        let $this = this;
        this.$http.get(
            `${j4care.addLastSlash(this.mainservice.baseUrl)}webapps?dcmWebServiceClass=STOW_RS`
        ).subscribe((response) => {
                $this.webapps = response;
                this.stowWebAppsOption = this.webapps.map((webapp:DcmWebApp)=>{
                    return new SelectDropdown<DcmWebApp>(webapp.dcmWebAppName, webapp.dcmWebAppName, webapp.dicomDescription);
                });
                let resultTemp = JSON.parse(localStorage.getItem('export_result'));
                if(resultTemp){
                    $this._result = resultTemp;
                }
                $this._result.selectedStowWebapp = $this._result.selectedStowWebapp || $this.webapps[0].dcmWebAppName;

                if ($this.mainservice.global && !$this.mainservice.global.webapps){
                    let global = _.cloneDeep($this.mainservice.global);
                    global.webapps = response;
                    $this.mainservice.setGlobal(global);
                }else{
                    if ($this.mainservice.global && $this.mainservice.global.webapps){
                        $this.mainservice.global.webapps = response;
                    }else{
                        $this.mainservice.setGlobal({webapps: response});
                    }
                }
            }, (response) => {
                // vex.dialog.alert("Error loading aes, please reload the page and try again!");
            });
    }

    validForm(){
        if(this._mode === "reschedule"){
            return true;
        }
        if(this._mode === "multipleExport"){
            return _.hasIn(this._result,"scheduledTime") && _.hasIn(this._result,"selectedExporter");
        }
        if (this._result && _.hasIn(this._result,"exportType") && this._result.exportType === 'dicom'){
           // if (this._result.dicomPrefix && this._result.selectedAet){
            if (this._result.selectedAet){
                return true;
            }else{
                return false;
            }
        }
        if (this._result && _.hasIn(this._result,"exportType") && this._result.exportType === 'stow'){
            if (this._result.selectedStowWebapp){
                return true;
            }else{
                return false;
            }
        }
        else {
            if (this._result && this._result.selectedExporter){
                return true;
            }else{
                return false;
            }
        }
    }
    dialogKeyHandler(e, dialogRef){
        let code = (e.keyCode ? e.keyCode : e.which);
        if (code === 13){
            dialogRef.close('ok');
        }
        if (code === 27){
            dialogRef.close(null);
        }
    };

    get quantity() {
        return this._quantity;
    }

    set quantity(value) {
        this._quantity = value;
    }
    ngOnDestroy(){
        localStorage.setItem('export_result', JSON.stringify(this._result));
    }
}
