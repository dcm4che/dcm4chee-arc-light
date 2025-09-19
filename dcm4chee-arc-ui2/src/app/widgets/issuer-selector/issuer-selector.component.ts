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

    @Input() placeholder:string;
    @Input() title:string;
    @Input() issuers:any[];
    splitters = [];
    @Input('model')
    set model(value){
        if(value && this.issuers && this.issuers.length > 0) {
            this.issuers.forEach(issuer => {
                if ( value && value[issuer.key]) {
                    this.filterModel[issuer.key] = value[issuer.key];
                } else {
                    this.filterModel[issuer.key] = '';
                }
            });
            this.set();
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
    }
    set(){
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
    togglePicker(){
        this.selectorOpen = !this.selectorOpen;
    }
    hardClear(){
        this.clearInnerModels();
        this.model = "";
        this.modelChange.emit(undefined);
    }
    filterChanged(){
        this.extractModelsFromString();
        this.modelChange.emit(this.filterModel);
    }

    initSplitters(){
        this.splitters = [];
        this.issuers.forEach((value,i)=>{
            if(i === 0 && _.hasIn(this.issuers,"0.key") && this.issuers[0].key === "PatientID"){
                this.splitters[i] = "^^^";
            }else{
                this.splitters[i] = "&";
            }
        });
    }
    extractModelsFromString(){
        try{
            if(this.model){
                let modelTemp = this.model;
                this.initSplitters();
                this.issuers.forEach((value,i)=>{
                    const split = this.splitters[i] || "&";
                    let splitted = modelTemp.split(split);
                    this.filterModel[this.issuers[i].key] = splitted[0].replace(/\&/g,"").replace(/\^/g,"") || "";
                    splitted.shift();
                    modelTemp = splitted.join(split);
                });
            }else{
                this.clearInnerModels();
            }
        }catch (e) {

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
