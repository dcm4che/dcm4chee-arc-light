import {Component, Input, OnInit} from '@angular/core';
import * as _ from 'lodash-es';
import {NgClass, NgStyle} from '@angular/common';

@Component({
    selector: 'stacked-progress',
    templateUrl: './stacked-progress.component.html',
    styleUrls: ['./stacked-progress.component.scss'],
    imports: [
        NgClass,
        NgStyle
    ],
    standalone: true
})
export class StackedProgressComponent implements OnInit {

    @Input() model;
    @Input() diffModel;
    progress = [];
    totalCount: number = 0;
    constructor() { }

    ngOnInit() {
        this.getTotalCount();
        try{
            this.progress = this.model.map(part=>{
                let key = Object.keys(part)[0];
                console.log("diffModel",this.diffModel);
                return {
                   cssClass:key,
                   width:(parseInt(part[key].toString())*100)/this.totalCount,
                   title:`${key}: ${part[key]}${this.setDiffTitle(key)}`
                }
            })
        }catch (e) {
            console.warn(e);
        }
    }
    setDiffTitle(key){
        if(this.diffModel){
            if(key === 'completed'){
                return $localize `:@@no_diffs: ( No diffs )`;
            }
            if(key === 'warning' && this.extractDiffInformation()){
                return ` ( ${this.extractDiffInformation()} )`;
            }
        }
        return '';
    }
    extractDiffInformation(){
        return [
            "different",
            "matches",
            "missing"
        ]
        .map(key=> this.getIfExist(key, this.diffModel))
        .filter(m => m)
        .join(', ');
    }

    getIfExist(key,model){
        if(_.hasIn(model, key)){
            return `${key}: ${model[key]}`;
        }else{
            return '';
        }
    }
    getTotalCount(){
        if(this.model){
            this.model.forEach(part=>{
                this.totalCount += parseInt(part[Object.keys(part)[0]].toString());
            })
        }
    }
}
