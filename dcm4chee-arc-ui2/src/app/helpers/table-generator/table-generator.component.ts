import {Component, Input, OnInit} from '@angular/core';
import * as _ from 'lodash';

@Component({
    selector: 'table-generator',
    templateUrl: './table-generator.component.html',
    styleUrls: ['./table-generator.component.scss']
})
export class TableGeneratorComponent implements OnInit {

    @Input() config;
    @Input() models;
    _ = _;
    Object = Object;
    constructor() {}
    ngOnInit() {
        this.calculateWidthOfTable();
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
}
