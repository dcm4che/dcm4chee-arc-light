import {Component, OnInit, EventEmitter, Output, Input, ViewEncapsulation} from '@angular/core';
import {j4care} from "../../helpers/j4care.service";

@Component({
    selector: 'time-picker',
    templateUrl: './time-picker.component.html',
    styleUrls: ['./time-picker.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class TimePickerComponent implements OnInit {

    @Output() onValueSet = new EventEmitter();
    @Input() model;
    @Input() cohereMode;
    @Input() placeholder;

    constructor() {
    }

    hhArray = [];
    mmArray = [];
    ssArray = [];
    hh = '00';
    mm = '00';
    ss = '00';
    timepickerOpen = true;
    ngOnInit() {
        console.log("set time",this.model);
        if(this.cohereMode){
            this.timepickerOpen = false;
        }
        let i = 0;
        while (i < 60) {
            if (i < 25) {
                this.hhArray.push(i < 10 ? `0${i}` : i);
            }
            this.mmArray.push(i < 10 ? `0${i}` : i);
            this.ssArray.push(i < 10 ? `0${i}` : i);
            i++;
        }
    }

    addTime() {
        this.onValueSet.emit(`${this.hh}:${this.mm}:${this.ss}`);
        if(this.cohereMode){
            this.timepickerOpen = false;
        }
    }

    close() {
        if(this.cohereMode){
            this.timepickerOpen = false;
        }else{
            this.onValueSet.emit("");
        }
    }
    toggle(){
        this.timepickerOpen = !this.timepickerOpen;
    }
    setTo00(){
       this.hh =  this.mm = this.ss = '00';
    }
    setToNow(){
        let now = new Date();
        this.hh = j4care.getSingleDateTimeValueFromInt(now.getHours());
        this.mm = j4care.getSingleDateTimeValueFromInt(now.getMinutes());
        this.ss = j4care.getSingleDateTimeValueFromInt(now.getSeconds());
    }
}