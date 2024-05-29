import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import * as _ from "lodash-es";
import {j4care} from "../../helpers/j4care.service";
@Component({
  selector: 'issuer-selector',
  templateUrl: './issuer-selector.component.html',
  styleUrls: ['./issuer-selector.component.scss']
})
export class IssuerSelectorComponent implements OnInit {

    private _model;

    @Input() placeholder:string;
    @Input() title:string;
    @Input() issuers:string[];
    @Input('model')
    set model(value){
        console.log("value",value);
        this._model = value;
    }
    get model(){
        return this._model;
    }
    @Output() modelChange =  new EventEmitter();
    selectorOpen:boolean = false;
    filterModel = {};
    maiInputValid:boolean = true;
    constructor() { }

    ngOnInit() {
    }

    set(){
        const issuerPart  = _.values(_.pickBy(this.filterModel,(value,key)=>key != "PatientID")).join('&');
        if(issuerPart){
            this.model = `${j4care.appendStringIfExist(this.filterModel["PatientID"], "^^^")}${issuerPart}`;
        }else{
            this.model = `${this.filterModel?.["PatientID"] || ''}`;
        }
        this.modelChange.emit(this.filterModel);
        this.selectorOpen = false;
    }
    togglePicker(){
        this.selectorOpen = !this.selectorOpen;
    }
    hardClear(){
        this.model = "";
        this.modelChange.emit(undefined);
    }
    filterChanged(){

    }
}
