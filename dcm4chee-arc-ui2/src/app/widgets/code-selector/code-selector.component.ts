import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import * as _ from 'lodash-es';
import {CommonModule, NgClass} from '@angular/common';
import {FormsModule} from '@angular/forms';
@Component({
    selector: 'code-selector',
    templateUrl: './code-selector.component.html',
    styleUrls: ['./code-selector.component.scss'],
    imports: [
        NgClass,
        FormsModule,
        CommonModule
    ],
    standalone: true
})
export class CodeSelectorComponent implements OnInit {

    private _model;

    @Input() placeholder:string;
    @Input() title:string;
    @Input() codes:string[];
    @Input('model')
    set model(value){
        console.log('value',value);
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
        this.model = `(${_.values(this.filterModel).join(', ')})`
        this.modelChange.emit(this.filterModel);
        this.selectorOpen = false;
    }
    togglePicker(){
        this.selectorOpen = !this.selectorOpen;
    }
    hardClear(){
        this.model = '';
        this.modelChange.emit(undefined);
    }
    filterChanged(){

    }
}
