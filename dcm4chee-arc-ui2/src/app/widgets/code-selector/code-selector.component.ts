import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';

@Component({
  selector: 'code-selector',
  templateUrl: './code-selector.component.html',
  styleUrls: ['./code-selector.component.scss']
})
export class CodeSelectorComponent implements OnInit {

    @Input() title:string;
    @Input() codes:string[];
    @Input('model')
    set model(value){
        console.log("value",value);
    }
    @Output() modelChange =  new EventEmitter();
    placeholder:string;
    selectorOpen:boolean = false;
    filterModel = {};
    maiInputValid:boolean = true;
    constructor() { }

    ngOnInit() {
    }

    togglePicker(){
        this.selectorOpen = !this.selectorOpen;
    }
    hardClear(){}
    filterChanged(){}
}
