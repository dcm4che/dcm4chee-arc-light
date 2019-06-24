import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import * as _ from 'lodash';
import {j4care} from "../j4care.service";

@Component({
    selector: 'table-generator',
    templateUrl: './table-generator.component.html',
    styleUrls: ['./table-generator.component.scss']
})
export class TableGeneratorComponent implements OnInit {

    @Input() config;
    @Input() models;
    @Input() stringifyDetailAttributes;
    @Output() tableMouseEnter = new EventEmitter();
    @Output() tableMouseLeave = new EventEmitter();
    _ = _;
    Object = Object;
    constructor() {
        console.log("model",this.models);
    }
    ngOnInit() {
        if(this.stringifyDetailAttributes){
            this.models.map(model=>{
                model.tableGeneratorDetailAttributes = Object.assign({},model);
                j4care.stringifyArrayOrObject(model.tableGeneratorDetailAttributes, []);
                return model;
            });
        }
        if(!_.hasIn(this.config,"calculate") || this.config.calculate){
            this.calculateWidthOfTable();
        }
    }
    calculateWidthOfTable(){
        let summ = 0;
        this.config.table.forEach((m)=>{
            summ += m.widthWeight;
        });
        this.config.table.forEach((m)=>{
            m.calculatedWidth =  ((m.widthWeight * 100)/summ)+"%";
        });
    };
    tMousEnter(){
        this.tableMouseEnter.emit();
    }
    tMousLeave(){
        this.tableMouseLeave.emit();
    }
    onProgressClicked(table_element, model){
        if(table_element.onClick){
            table_element.onClick(model)
        }
    }
    selectOnClick(str){
        const el = document.createElement('textarea');
        el.value = str;
        document.body.appendChild(el);
        el.select();
        document.execCommand('copy');
        document.body.removeChild(el);
    }
}
