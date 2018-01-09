import {Component, EventEmitter, Input, OnInit, Output, ViewChild} from '@angular/core';
import {j4care} from "../../helpers/j4care.service";

@Component({
  selector: 'range-picker',
  templateUrl: './range-picker.component.html',
  styleUrls: ['./range-picker.component.scss']
})
export class RangePickerComponent implements OnInit {
    @Input() model;
    @Input() placeholder;
    @Input() title;
    @Input() dateFormat;
    @Input() mode:("leftOpen"|"rightOpen"|"range"|"single");
    @Output() modelChange = new EventEmitter();
    @ViewChild('fromCalendar') fromCalendarObject;
    @ViewChild('toCalendar') toCalendarObject;
    @ViewChild('singleCalendar') singleCalendarObject;
    fromModel;
    toModel;
    singleDateModel;
    showPicker;
    smartPickerActive = false;
    smartInput;
    constructor() {}
    ngOnInit(){
        console.log("this.model",this.model);
        console.log("this.placeholder",this.placeholder);
        this.mode = this.mode || "range";
        this.title = this.title || "Range picker";
    }
    closeCalendar(clanedarName){
        // console.log("inclose",e);
        this[clanedarName].overlayVisible = false;
    }
    setRange(){
        switch(this.mode){
            case 'single':
                this.model = this.singleDateModel;
            break;
            case 'range':
                this.model = (this.fromModel != '' || this.toModel != '') ? `${this.fromModel}-${this.toModel}`:'';
            break;
            case 'rightOpen':
                this.model = (this.fromModel != '') ? `${this.fromModel}-`:'';
                break;
            case 'leftOpen':
                this.model = (this.toModel != '') ?`-${this.toModel}`:'';
                break;
        }
        this.modelChange.emit(this.model);
        this.showPicker = false;
    }
    togglePicker(){
        this.showPicker = !this.showPicker;
    }
    close(){
        this.showPicker = false;
    }
    clear(){
        this.fromModel = '';
        this.toModel = '';
        this.singleDateModel = '';
    }
    today(){
        this.modelChange.emit(j4care.convertDateToString(new Date()));
        this.showPicker = false;
    }
    lastMonth(){
        let todayDate = new Date();
        todayDate.setMonth(todayDate.getMonth()-1);
        this.modelChange.emit(`${j4care.convertDateToString(todayDate)}-${j4care.convertDateToString(new Date())}`);
        this.showPicker = false;
    }
    lastYear(){
        let todayDate = new Date();
        todayDate.setFullYear(todayDate.getFullYear()-1);
        this.modelChange.emit(`${j4care.convertDateToString(todayDate)}-${j4care.convertDateToString(new Date())}`);
        this.showPicker = false;
    }
    smartPicker(){
        this.mode = 'range';
        this.smartPickerActive = !this.smartPickerActive;
        this.toModel = j4care.convertDateToString(new Date());
    }
    changeMode(newMode){
        this.mode = newMode;
        if(this.smartPickerActive){
            switch(this.mode){
                case 'single':
                    if(this.fromModel)
                        this.singleDateModel = this.fromModel;
                    break;
                case 'range':
                    if(this.fromModel)
                        this.toModel = new Date();
                    break;
                case 'leftOpen':
                    if(this.fromModel)
                        this.toModel = this.fromModel;
                    break;
            }
        }
    }
    smartInputChange(e){
        let date = new Date();
        date.getFullYear()
        let extractedDurationObject = j4care.extractDurationFromValue(e);
        this.fromModel = j4care.convertDateToString(this.createDateFromDuration(extractedDurationObject));
    }
    createDateFromDuration(durationObject){
        let today = new Date();
        let newDate = new Date();
        Object.keys(durationObject).forEach(key => {
            if(durationObject[key]){
                switch (key){
                    case 'Week':
                        newDate.setDate(today.getDate()-(7*durationObject[key]));
                    break;
                    case 'FullYear':
                        newDate.setFullYear(today.getFullYear()-durationObject[key]);
                    break;
                    case 'Date':
                        newDate.setDate(today.getDate()-durationObject[key]);
                    break;
                    case 'Hours':
                        newDate.setHours(today.getHours()-durationObject[key]);
                    break;
                    case 'Minutes':
                        newDate.setMinutes(today.getMinutes()-durationObject[key]);
                    break;
                    case 'Month':
                        newDate.setMonth(today.getMonth()-durationObject[key]);
                    break;
                    case 'Seconds':
                        newDate.setSeconds(today.getSeconds()-durationObject[key]);
                    break;
                }
            }
        });
        return newDate;
    }
}
