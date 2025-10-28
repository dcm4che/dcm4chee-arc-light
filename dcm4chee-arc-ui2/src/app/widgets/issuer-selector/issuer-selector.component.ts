import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import * as _ from "lodash-es";
import {j4care} from "../../helpers/j4care.service";
import {CommonModule, NgClass} from '@angular/common';
import {FormsModule} from '@angular/forms';
@Component({
    selector: 'issuer-selector',
    templateUrl: './issuer-selector.component.html',
    styleUrls: ['./issuer-selector.component.scss'],
    imports: [
        NgClass,
        FormsModule,
        CommonModule
    ],
    standalone: true
})
export class IssuerSelectorComponent implements OnInit {

    private _model;

    @Input() placeholder: string;
    @Input() title: string;
    @Input() issuers: any[];
    splitters = [];
    suffixDropdownKeyConfig = {
        'dcmuiIssuerOfAccessionNumberSequence': {
            mainKey: 'AccessionNumber',
            suffixSplitChar: '^',
            mergeChar: '^',
            secondaryKeys:[
                'IssuerOfAccessionNumberSequence.LocalNamespaceEntityID',
                'IssuerOfAccessionNumberSequence.UniversalEntityID',
                'IssuerOfAccessionNumberSequence.UniversalEntityIDType'
            ]
        },
        'dcmuiIssuerOfAdmissionIDSequence': {
            mainKey: 'AdmissionID',
            suffixSplitChar: '&',
            mergeChar: '^^^',
            secondaryKeys:[
                'IssuerOfAdmissionIDSequence.LocalNamespaceEntityID',
                'IssuerOfAdmissionIDSequence.UniversalEntityID',
                'IssuerOfAdmissionIDSequence.UniversalEntityIDType'
            ]
        },
        'dcmuiIssuerOfPatientIDSequence': {
            mainKey: 'PatientID',
            suffixSplitChar: '&',
            mergeChar: '^^^',
            secondaryKeys: [
                'IssuerOfPatientID',
                'IssuerOfPatientIDQualifiersSequence.UniversalEntityID',
                'IssuerOfPatientIDQualifiersSequence.UniversalEntityIDType'
            ]
        }
    };
    @Input('model')
    set model(value){
        if(value && this.issuers && this.issuers.length > 0) {
            this.setFilterModel(value);
            //this.set(true);
        }
    }
    get model(){
        return this._model;
    }
    @Output() modelChange =  new EventEmitter();
    selectorOpen:boolean = false;
    filterModel = {};
    maiInputValid:boolean = true;
    viewLimit = 4;

    constructor() { }

    ngOnInit() {
        this.initSplitters();
    }

    setFilterModel(value){
        try {
            if(typeof value === "string"){
                this.triggerExtraction(value);
            }else{
                this.issuers.forEach(issuer => {
                    if ( value && value[issuer.key]) {
                        this.filterModel[issuer.key] = value[issuer.key];
                    } else {
                        this.filterModel[issuer.key] = '';
                    }
                });
                let hypotheticValue = '';
                Object.keys(this.suffixDropdownKeyConfig).forEach((suffixDropdownKey) => {
                    const configObject = this.suffixDropdownKeyConfig[suffixDropdownKey];
                    if(this.filterModel[configObject.mainKey]){
                        hypotheticValue = j4care.sliceArrayFromRightUntilNotEmpty(
                            configObject.secondaryKeys.map(key=>this.filterModel[key] || '')
                        ).join(configObject.suffixSplitChar);
                    }
                })
            }
        }catch (e) {
        }
    }

    set(templateInit){
        if (templateInit) {
            if (this.filterModel[this.issuers[0].key]) {
                this._model = this.filterModel[this.issuers[0].key];
                this.modelChange.emit(this.filterModel);
            } else {
                this._model = '';
                this.modelChange.emit(undefined);
            }
        } else {
            if(this.filterModel && this.filterModel["AccessionNumber"]) {
                let issuerPart  = _.values(_.pickBy(this.filterModel,(value,key)=>key != "AccessionNumber"));
                issuerPart = j4care.removeLastEmptyStringsFromArray(issuerPart).join('^');
                if(issuerPart){
                    this._model = `${j4care.appendStringIfExist(this.filterModel["AccessionNumber"], "^")}${issuerPart}`;
                }else{
                    this._model = `${this.filterModel?.["AccessionNumber"] || ''}`;
                }
                this.modelChange.emit(this.filterModel);
            } else if(this.filterModel && this.filterModel["ScheduledStepAttributesSequence.AccessionNumber"]) {
                let issuerPart  = _.values(_.pickBy(this.filterModel,(value,key)=>key != "ScheduledStepAttributesSequence.AccessionNumber"));
                issuerPart = j4care.removeLastEmptyStringsFromArray(issuerPart).join('^');
                if(issuerPart){
                    this._model = `${j4care.appendStringIfExist(this.filterModel["ScheduledStepAttributesSequence.AccessionNumber"], "^")}${issuerPart}`;
                }else{
                    this._model = `${this.filterModel?.["ScheduledStepAttributesSequence.AccessionNumber"] || ''}`;
                }
                this.modelChange.emit(this.filterModel);
            } else if(this.filterModel && this.filterModel["PatientID"]) {
                let issuerPart  = _.values(_.pickBy(this.filterModel,(value,key)=>key != "PatientID"));
                issuerPart = j4care.removeLastEmptyStringsFromArray(issuerPart).join('&');
                if(issuerPart){
                    this._model = `${j4care.appendStringIfExist(this.filterModel["PatientID"], "^^^")}${issuerPart}`;
                }else{
                    this._model = `${this.filterModel?.["PatientID"] || ''}`;
                }
                this.modelChange.emit(this.filterModel);
            } else if(this.filterModel && this.filterModel["AdmissionID"]) {
                let issuerPart  = _.values(_.pickBy(this.filterModel,(value,key)=>key != "AdmissionID"));
                issuerPart = j4care.removeLastEmptyStringsFromArray(issuerPart).join('&');
                if(issuerPart){
                    this._model = `${j4care.appendStringIfExist(this.filterModel["AdmissionID"], "^^^")}${issuerPart}`;
                }else{
                    this._model = `${this.filterModel?.["AdmissionID"] || ''}`;
                }
                this.modelChange.emit(this.filterModel);
            }
        }
    }
    togglePicker(){
        this.selectorOpen = !this.selectorOpen;
    }
    hardClear(){
        this.clearInnerModels();
        this.model = "";
        this.modelChange.emit(undefined);
    }
    filterChanged(){
        this.triggerExtraction();
        this.modelChange.emit(this.filterModel);
    }

    initSplitters(){
        this.splitters = [];
        if( this.issuers && this.issuers.length > 0 ) {
            Object.keys(this.suffixDropdownKeyConfig).forEach((suffixDropdownKey) => {
                if(this.splitters.length === 0){
                    const configObject = this.suffixDropdownKeyConfig[suffixDropdownKey];
                    if(_.hasIn(this.filterModel, configObject.mainKey)){
                        this.splitters[0] = configObject.mergeChar;
                        this.issuers.forEach((issuer,i)=>{
                            if(i > 0 && i < (this.issuers.length-1)){
                                this.splitters[i] = configObject.suffixSplitChar;
                            }
                        })
                    }
                }
            })
        }
    }
    triggerExtraction(value?:string){
        try{
            if(value || this._model){
                value = value || this._model;
                let modelTemp = value;
                this.initSplitters();
                this.filterModel = this.extractModelsFromString(modelTemp, this.filterModel, this.issuers, this.splitters);
                this.set(false);
                this.modelChange.emit(
                    this.filterModel
                )
            }else{
                this.clearInnerModels();
            }
        }catch (e) {

        }
    }
    extractModelsFromString(model, filterModel = this.filterModel, issuers = this.issuers, splitters = this.splitters){
        try{
            if(model){
                console.log("suffixDropdownKeyConfig",this.suffixDropdownKeyConfig);
                issuers.forEach((value,i)=>{
                    const split = splitters[i] || "&";
                    let splitted = model.split(split);
                    filterModel[issuers[i].key] = splitted[0].replace(/\&/g,"").replace(/\^/g,"") || "";
                    splitted.shift();
                    model = splitted.join(split);
                });
                return filterModel;
            }
        }catch (e) {
            return filterModel;
        }
    }
    trackById(index: number, item: any) {
        return item.key || index;
    }
    clearInnerModels(){
        this.issuers.forEach(issue=>{
            this.filterModel[issue.key] = '';
        })
        this._model = '';
    }
}
