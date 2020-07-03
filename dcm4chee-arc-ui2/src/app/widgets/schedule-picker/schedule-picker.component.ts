import {Component, OnInit, EventEmitter, Output, Input} from '@angular/core';
import * as _ from 'lodash-es';

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
                        let newObject = this.generateDraggableObject(matchArray[0][1], match2);
                        if(newObject)
                            this.draggedElements.push(newObject);
                    }
                    if(match2[3] && !match2[1] && !match2[2]){
                        let newObject = this.generateDraggableObject(matchArray[0][1], match2);
                        if(newObject)
                            this.draggedElements.push(newObject);
                    }
                }
            }
            if(_.hasIn(matchArray,'[1][3]') && matchArray[1][3]){
                while ((match2 = ptrn2.exec(matchArray[1][4])) != null) {
                    if(match2[1] && match2[2]){
                       let newObject = this.generateDraggableObject(matchArray[1][3], match2);
                        if(newObject)
                            this.draggedElements.push(newObject);
                    }
                    if(match2[3] && !match2[1] && !match2[2]){
                        let newObject = this.generateDraggableObject(matchArray[1][3], match2);
                        if(newObject)
                            this.draggedElements.push(newObject);
                    }
                }
            }
        }catch (e){
            console.error("error parsing data!",e);
        }
        document.addEventListener("dragend", function( event ) {
            // store a ref. on the dragged elem
            console.log("in addevent dragend");
        }, false)
/*        var dragItems = document.querySelectorAll('[draggable=true]');

        for (var i = 0; i < dragItems.length; i++) {
/!*            addEvent(dragItems[i], 'dragstart', function (event) {
                // store the ID of the element, and collect it on the drop later on

                event.dataTransfer.setData('Text', this.id);
            });*!/
        }*/
    }
    generateDraggableObject(mode, match2){
        if(match2[1] && match2[2]){
            return {
                mode:((mode==='hour')?'hour_range':'day_range'),
                model:{
                    model1:(match2[1]),
                    model2:(match2[2])
                }
            };
        }
        if(match2[3] && !match2[1] && !match2[2]){
            return {
                mode:((mode==='hour')?'single_hour':'single_day'),
                model:(match2[3])
            };
        }
        return null;
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
        console.log("dropleave",ev);
        if(this.currentDraggedElement != ""){
            this.draggedElements.push(this.currentDraggedElement);
            this.currentDraggedElement = "";
        }
    }
    dragstart(ev,mode){
        ev.dataTransfer.setData('text', 'foo');
        this.currentDraggedElement = {
            mode:mode,
            model:{}
        };
    }
    deleteDropped(item){
        this.draggedElements.splice(item, 1);
    }
/*    testDrag(){
        console.log("ontestdrag");
    }
    testOnDragStart(){
        console.log("testOnDragStart");
    }
    testDragStart(ev){
        console.log("testDragStart",ev);
        // ev.dataTransfer.dropEffect = "copy";
        ev.dataTransfer.setData('text', 'foo');
    }
    testDragEnd(event){
        console.log("testdragend",event);
    }*/
}
