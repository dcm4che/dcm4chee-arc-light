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
    @ViewChild('fromTimeCalendar') fromTimeCalendarObject;
    @ViewChild('toCalendar') toCalendarObject;
    @ViewChild('singleCalendar') singleCalendarObject;
    fromModel;
    fromTimeModel;
    toModel;
    toTimeModel;
    singleDateModel;
    singleTimeModel;
    showPicker;
    smartPickerActive = false;
    smartInput;
    HH = [];
    mm = [];
    includeTime = false;
    maiInputValid = true;
    constructor() {}
    ngOnInit(){
        this.mode = this.mode || "range";
        this.title = this.title || "Range picker";
        for(let i=0;i<60;i++){
            if(i<25){
                this.HH.push({value:i,label:(i<10)?`0${i}`:i});
            }
            this.mm.push({value:i,label:(i<10)?`0${i}`:i});
        }
    }
    closeCalendar(clanedarName){
        // console.log("inclose",e);
        this[clanedarName].overlayVisible = false;
    }
    toggleTime(){
    }
    setRange(){
        switch(this.mode){
            case 'single':
                this.model = this.getDateFromValue(this.singleDateModel) + this.getTimeFromValue(this.singleTimeModel, false);
            break;
            case 'range':
                this.model = (this.fromModel != '' || this.toModel != '') ? `${this.getDateFromValue(this.fromModel) + this.getTimeFromValue(this.fromTimeModel, false)}-${this.getDateFromValue(this.toModel) + this.getTimeFromValue(this.toTimeModel, false)}`:'';
            break;
            case 'rightOpen':
                this.model = (this.fromModel != '') ? `${this.getDateFromValue(this.fromModel) + this.getTimeFromValue(this.fromTimeModel, false)}-`:'';
                break;
            case 'leftOpen':
                this.model = (this.toModel != '') ?`-${this.getDateFromValue(this.toModel) + this.getTimeFromValue(this.toTimeModel, false)}`:'';
                break;
        }
        this.modelChange.emit(this.model);
        this.filterChanged();
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
        this.fromTimeModel = '';
        this.toModel = '';
        this.toTimeModel = '';
        this.singleDateModel = '';
        this.singleTimeModel = '';
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
        if(extractedDurationObject.Minutes || extractedDurationObject.Hours || extractedDurationObject.Seconds){
            this.includeTime = true;
            this.fromTimeModel = `${j4care.getSingleDateTimeValueFromInt(extractedDurationObject.Hours)}:${j4care.getSingleDateTimeValueFromInt(extractedDurationObject.Minutes)}:${j4care.getSingleDateTimeValueFromInt(extractedDurationObject.Seconds)}`;
            this.toTimeModel = j4care.getTimeFromDate(new Date());
        }
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
    getDateFromValue(value){
        return value || j4care.dateToString(new Date());
    }
    getDateFromObject(value){
        return value || j4care.dateToString(new Date());
    }
    getTimeFromValue(value, onEmptyNull?){
        if(this.includeTime){
            let emptyValue = (onEmptyNull) ? '000000' : '';
            return (value && value != '') ? this.removeDoubleDotsFromTime(value) : emptyValue;
        }
        return '';
    }
    removeDoubleDotsFromTime(value){
        const ptrn = /(\d{2})/g;
        let match;
        let result = '';
        if(value && value != ''){
            try {
                while ((match = ptrn.exec(value)) != null) {
                    result += match[1];
                }
                return result;
            }catch (e){
                console.error("Could not extract time values from time string",value,e);
            }
        }
        return '';
    }

    filterChanged(){
        let modifyed = j4care.extractDateTimeFromString(this.model);
        this.maiInputValid = true;
        if(modifyed){
            this.mode = modifyed.mode;
            if((this.mode === "range" || this.mode === "rightOpen" || this.mode === "single") && j4care.isSetDateObject(modifyed.firstDateTime)){
                if(j4care.validDateObject(modifyed.firstDateTime)){
                    if(this.mode === "single")
                        this.singleDateModel = j4care.getDateFromObject(modifyed.firstDateTime);
                    else
                        this.fromModel = j4care.getDateFromObject(modifyed.firstDateTime);
                    if(j4care.isSetTimeObject(modifyed.firstDateTime)){
                        if(j4care.validTimeObject(modifyed.firstDateTime)){
                            if(this.mode === "single")
                                this.singleTimeModel = j4care.getTimeFromObject(modifyed.firstDateTime);
                            else
                                this.fromTimeModel = j4care.getTimeFromObject(modifyed.firstDateTime);
                            this.includeTime = true;
                        }else
                            this.maiInputValid = false;
                    }
                }else
                    this.maiInputValid = false;
            }
            if((this.mode === "range" || this.mode === "leftOpen") && j4care.isSetDateObject(modifyed.secondDateTime)){
                if(j4care.validDateObject(modifyed.secondDateTime)){
                    this.toModel =  j4care.getDateFromObject(modifyed.secondDateTime);
                    if(j4care.isSetTimeObject(modifyed.secondDateTime)){
                        if(j4care.validTimeObject(modifyed.secondDateTime)){
                            this.toTimeModel =  j4care.getTimeFromObject(modifyed.secondDateTime);
                            this.includeTime = true;
                        }else
                            this.maiInputValid = false;
                    }
                }else
                    this.maiInputValid = false;
            }
        }else{
            if(this.model != '')
                this.maiInputValid = false;
        }

    }
}
