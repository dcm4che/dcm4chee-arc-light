import {Component, Input, OnInit} from '@angular/core';

@Component({
  selector: 'stacked-progress',
  templateUrl: './stacked-progress.component.html',
  styleUrls: ['./stacked-progress.component.scss']
})
export class StackedProgressComponent implements OnInit {

    @Input() model;
    progress = [];
    totalCount:number = 0;
    constructor() { }

    ngOnInit() {
        this.getTotalCount();
        this.progress = this.model.map(part=>{
            let key = Object.keys(part)[0];
           return {
               cssClass:key,
               width:(parseInt(part[key].toString())*100)/this.totalCount,
               title:`${key}:${part[key]}`
           }
        })
    }
    getTotalCount(){
        this.model.forEach(part=>{
            this.totalCount += parseInt(part[Object.keys(part)[0]].toString());
        })
    }
}
