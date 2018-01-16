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
    @Input() onlyTime;
    @Input() onlyDate;
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
    showSelectOptions = false;
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
                this.model = (this.fromModel != '' || this.toModel != '' || this.onlyTime) ? `${this.getDateFromValue(this.fromModel) + this.getTimeFromValue(this.fromTimeModel, false)}-${this.getDateFromValue(this.toModel) + this.getTimeFromValue(this.toTimeModel, false)}`:'';
            break;
            case 'rightOpen':
                this.model = (this.fromModel != '' || this.onlyTime) ? `${this.getDateFromValue(this.fromModel) + this.getTimeFromValue(this.fromTimeModel, false)}-`:'';
                break;
            case 'leftOpen':
                this.model = (this.toModel != '' || this.onlyTime) ?`-${this.getDateFromValue(this.toModel) + this.getTimeFromValue(this.toTimeModel, false)}`:'';
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
        let result = value || (this.onlyTime ? '': j4care.dateToString(new Date()));
        return result;
    }
    getDateFromObject(value){
        return value || j4care.dateToString(new Date());
    }
    getTimeFromValue(value, onEmptyNull?){
        if(this.includeTime || this.onlyTime){
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
            if((this.mode === "range" || this.mode === "rightOpen" || this.mode === "single") && (j4care.isSetDateObject(modifyed.firstDateTime) || j4care.isSetTimeObject(modifyed.firstDateTime))){
                if(j4care.validDateObject(modifyed.firstDateTime) || j4care.validTimeObject(modifyed.firstDateTime)){
                    if(this.mode === "single")
                        this.singleDateModel = j4care.getDateFromObject(modifyed.firstDateTime) || '';
                    else
                        this.fromModel = j4care.getDateFromObject(modifyed.firstDateTime) || '';
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
            if((this.mode === "range" || this.mode === "leftOpen") && (j4care.isSetDateObject(modifyed.secondDateTime) || j4care.isSetTimeObject(modifyed.secondDateTime))){
                if(j4care.validDateObject(modifyed.secondDateTime) || j4care.validTimeObject(modifyed.secondDateTime)){
                    this.toModel =  j4care.getDateFromObject(modifyed.secondDateTime) || '';
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

    toggleSelectOption(){
        this.showSelectOptions = !this.showSelectOptions;
    }
    fastPicker(mode){
        let firstDate = new Date();
        let secondDate = new Date();
        let quarterRangeMonth;
        let todayDay;
        this.showSelectOptions = false;
        switch (mode){
            case 'yesterday':
                firstDate.setDate(firstDate.getDate()-1);
                this.model = j4care.convertDateToString(firstDate);
            break;
            case 'this_week':
                todayDay = firstDate.getDay();
                if(todayDay === 0){
                    todayDay = 7;
                }
                firstDate.setDate(firstDate.getDate()-(todayDay-1));
                if(this.eqDate(firstDate, secondDate))
                    this.model = j4care.convertDateToString(firstDate);
                else
                    this.model = `${j4care.convertDateToString(firstDate)}-${j4care.convertDateToString(secondDate)}`;
            break;
            case 'last_week':
                todayDay = firstDate.getDay();
                if(todayDay === 0){
                    todayDay = 7;
                }
                firstDate.setDate(firstDate.getDate()-(todayDay-1));
                firstDate.setDate(firstDate.getDate()-7);
                secondDate.setDate(firstDate.getDate()+6);
                if(this.eqDate(firstDate, secondDate))
                    this.model = j4care.convertDateToString(firstDate);
                else
                    this.model = `${j4care.convertDateToString(firstDate)}-${j4care.convertDateToString(secondDate)}`;
            break;
            case 'last_month':
                firstDate.setMonth(firstDate.getMonth()-1);
                firstDate.setDate(1);
                secondDate.setDate(1);
                secondDate.setDate(secondDate.getDate()-1);
                this.model = `${j4care.convertDateToString(firstDate)}-${j4care.convertDateToString(secondDate)}`;
                break;
            case 'this_quarter':
                quarterRangeMonth = this.getQuarterRange(this.getQuarterIndex(firstDate.getMonth()));
                this.model = `${j4care.convertDateToString(this.getStartOfMonth(quarterRangeMonth.start))}-${j4care.convertDateToString(this.getEndOfMonth(quarterRangeMonth.end))}`;
            break;
            case 'last_quarter':
                quarterRangeMonth = this.getQuarterRange(this.getQuarterIndex(firstDate.getMonth())-1);
                this.model = `${j4care.convertDateToString(this.getStartOfMonth(quarterRangeMonth.start))}-${j4care.convertDateToString(this.getEndOfMonth(quarterRangeMonth.end))}`;
                break;
            case 'this_year':
                firstDate.setDate(1);
                firstDate.setMonth(0);
                this.model = `${j4care.convertDateToString(firstDate)}-${j4care.convertDateToString(secondDate)}`;
                break;
            case 'last_year':
                firstDate.setFullYear(firstDate.getFullYear()-1);
                firstDate.setDate(1);
                firstDate.setMonth(0);
                firstDate.setDate(firstDate.getDate()-1);
                secondDate.setDate(1);
                secondDate.setMonth(0);
                secondDate.setDate(secondDate.getDate()-1);
                this.model = `${j4care.convertDateToString(firstDate)}-${j4care.convertDateToString(secondDate)}`;
            break;
        }
        this.modelChange.emit(this.model);
        this.filterChanged();
        this.showPicker = false;
    }
    eqDate(date1,date2){
        return (date1.getFullYear() === date2.getFullYear() && date1.getMonth() === date2.getMonth() && date1.getDate() === date2.getDate());
    }
    getQuarterIndex(month){
        return parseInt((month/3).toString());
    }
    getQuarterRange(quarterIndex){
        let quarterStart = ((quarterIndex*3)+1);
        let quarterEnd = quarterStart + 2;
        return {
            start:quarterStart,
            end:quarterEnd
        }
    }
    getStartOfMonth(month){
        let newDate = new Date();
        newDate.setMonth(month-1);
        newDate.setDate(1);
        return newDate;
    }
    getEndOfMonth(month){
        let newDate = new Date();
        newDate.setMonth(month);
        newDate.setDate(1);
        newDate.setDate(newDate.getDate()-1);
        return newDate;
    }
    today(){
        this.model = j4care.convertDateToString(new Date());
        this.modelChange.emit(this.model);
        this.showPicker = false;
        this.filterChanged();
    }
    thisMonth(){
        let todayDate = new Date();
        todayDate.setDate(1);
        let secondDate = new Date();
        if(this.eqDate(todayDate,secondDate))
            this.model = j4care.convertDateToString(todayDate);
        else
            this.model = `${j4care.convertDateToString(todayDate)}-${j4care.convertDateToString(new Date())}`;
        this.modelChange.emit(this.model);
        this.showPicker = false;
        this.filterChanged();
    }
    lastYear(){
        let todayDate = new Date();
        todayDate.setFullYear(todayDate.getFullYear()-1);
        this.model(`${j4care.convertDateToString(todayDate)}-${j4care.convertDateToString(new Date())}`);
        this.modelChange.emit(this.model);
        this.showPicker = false;
        this.filterChanged();
    }
}
