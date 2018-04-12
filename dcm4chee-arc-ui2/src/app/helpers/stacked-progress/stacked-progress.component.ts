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
        this.progress = Object.keys(this.model).map(part=>{
           return {
               cssClass:part,
               width:(parseInt(this.model[part].toString())*100)/this.totalCount,
               title:`${part}:${this.model[part]}`
           }
        })
    }
    getTotalCount(){
        Object.keys(this.model).forEach(part=>{
            this.totalCount += parseInt(this.model[part].toString());
        })
    }
}
