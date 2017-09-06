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
        let match;
        let matchArray = [];
        let match2;
        let ptrn = /(hour)=([\d|\d-\d|\*|\,]*)|(dayOfWeek)=([\d|\d-\d|\*|\,]*)/g;
        let ptrn2 = /(\d+)-(\d+)|(\d+)|\*/g;
        try {
            while ((match = ptrn.exec(this.value)) != null) {
                matchArray.push(match);
            }
            if(_.hasIn(matchArray,'[0][1]') && matchArray[0][1]){
                while ((match2 = ptrn2.exec(matchArray[0][2])) != null) {
                    if(match2[1] && match2[2]){
                        this.draggedElements.push({
                            mode:((matchArray[0][1]==='hour')?'hour_range':'day_range'),
                            model:{
                                model1:(match2[1]),
                                model2:(match2[2])
                            }
                        });
                    }
                    if(match2[3] && !match2[1] && !match2[2]){
                        this.draggedElements.push({
                            mode:((matchArray[0][1]==='hour')?'single_hour':'single_day'),
                            model:(match2[3])
                        });
                    }
                }
            }
            if(_.hasIn(matchArray,'[1][3]') && matchArray[1][3]){
                while ((match2 = ptrn2.exec(matchArray[1][4])) != null) {
                    if(match2[1] && match2[2]){
                        this.draggedElements.push({
                            mode:((matchArray[1][3]==='hour')?'hour_range':'day_range'),
                            model:{
                                model1:(match2[1]),
                                model2:(match2[2])
                            }
                        });
                    }
                    if(match2[3] && !match2[1] && !match2[2]){
                        this.draggedElements.push({
                            mode:((matchArray[1][3]==='hour')?'single_hour':'single_day'),
                            model:(match2[3])
                        });
                    }
                }
            }
        }catch (e){
            console.error("error parsing data!",e);
        }
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
