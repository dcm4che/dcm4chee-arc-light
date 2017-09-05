import {Component, OnInit, EventEmitter, Output, Input} from '@angular/core';
import * as _ from 'lodash';

@Component({
  selector: 'schedule-picker',
  templateUrl: './schedule-picker.component.html',
  styleUrls: ['./schedule-picker.component.css']
})
export class SchedulePickerComponent implements OnInit {

    @Output() onValueSet = new EventEmitter();
    @Input() value;
    draggedElements = [];
    currentDraggedElement;
    constructor() { }

    ngOnInit() {
/*        let match;
        let ptrn = /(hour)=([\d|\d-\d|\*|\,]*)|(dayOfWeek)=([\d|\d-\d|\*|\,]*)/g;
        try {
            while ((match = ptrn.exec(this.value)) != null) {
                    console.log("match",match);
            }
        }catch (e){
            console.error("error parsing data!",e);
        }*/
    }
    addSchedule(){
        this.onValueSet.emit(this.generateSchedule());
    }
    close(){
        this.onValueSet.emit("");
    }

    private generateSchedule() {
        let scheduler = {
            hour:"",
            dayOfWeek:""
        };
        _.forEach(this.draggedElements,(m,i)=>{
            switch(m.mode) {
                case 'single_hour':
                    if(_.isString(m.model))
                        scheduler.hour = scheduler.hour + ((scheduler.hour != "")?',':'') + m.model;
                    break;
                case 'single_day':
                    if(_.isString(m.model))
                        scheduler.dayOfWeek = scheduler.dayOfWeek + ((scheduler.dayOfWeek != "")?',':'') + m.model;
                    break;
                case 'hour_range':
                    if(_.isString(m.model.model1) && _.isString(m.model.model2))
                        scheduler.hour = scheduler.hour + ((scheduler.hour != "")?',':'') + m.model.model1 + '-' + m.model.model2;
                    break;
                case 'day_range':
                    if(_.isString(m.model.model1) && _.isString(m.model.model2))
                        scheduler.dayOfWeek = scheduler.dayOfWeek + ((scheduler.dayOfWeek != "")?',':'') + m.model.model1 + '-' + m.model.model2;
                    break;
            }
        });
        return ((scheduler.hour)?`hour=${scheduler.hour}`:'') + ((scheduler.hour && scheduler.dayOfWeek)? ' ':'') + ((scheduler.dayOfWeek)?`dayOfWeek=${scheduler.dayOfWeek}`:'');
    }
    dragleave(ev){
        if(this.currentDraggedElement != ""){
            this.draggedElements.push(this.currentDraggedElement);
            this.currentDraggedElement = "";
        }
    }
    dropstart(ev){
        this.currentDraggedElement = {
            mode:ev,
            model:{}
        };
    }
    deleteDropped(item){
        this.draggedElements.splice(item, 1);
    }
}
