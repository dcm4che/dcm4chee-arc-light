import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import * as _ from "lodash-es";
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
        this.model = `${_.values(this.filterModel).join('&')}`
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
