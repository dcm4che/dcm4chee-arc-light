import {Component, OnInit, EventEmitter, Output, Input} from '@angular/core';
import * as _ from 'lodash';

const WEEK = {
    plural:$localize `:@@week_plural:weeks`,
    singular:$localize `:@@week_singular:week`
};
const YEAR = {
    plural:$localize `:@@year_plural:years`,
    singular:$localize `:@@year_singular:year`
};
const DAY = {
    plural:$localize `:@@day_plural:days`,
    singular:$localize `:@@day_singular:day`
};
const HOUR = {
    plural:$localize `:@@hour_plural:hours`,
    singular:$localize `:@@hour_singular:hour`
};
const MINUTE = {
    plural:$localize `:@@minute_plural:minutes`,
    singular:$localize `:@@minute_singular:minute`
};
const SECOND = {
    plural:$localize `:@@second_plural:seconds`,
    singular:$localize `:@@second_singular:second`
};
const MONTH = {
    plural:$localize `:@@month_plural:months`,
    singular:$localize `:@@month_singular:month`
};

@Component({
  selector: 'duration-picker',
  templateUrl: './duration-picker.component.html',
  styleUrls: ['./duration-picker.component.css']
})
export class DurationPickerComponent implements OnInit {


    @Output() onValueSet = new EventEmitter();
    @Input() mode;
    @Input() value;
    constructor() { }
    y; //yeaar
    d; //day
    month;
    h; //hour
    m; //minute
    s; //second
    week;
    message;

    ngOnInit() {
        this.extractDurationFromValue();
        this.onModelChange(null);
    }

    extractDurationFromValue(){
        let match;
        let ptrn = /(\d)(\w)/g;
        try {
            while ((match = ptrn.exec(this.value)) != null) {
                if(this.mode === "dcmDuration"){
                    switch(match[2]) {
                        case 'D':
                            this.d = parseInt(match[1]);
                            break;
                        case 'H':
                            this.h = parseInt(match[1]);
                            break;
                        case 'M':
                            this.m = parseInt(match[1]);
                            break;
                        case 'S':
                            this.s = parseInt(match[1]);
                            break;
                    }
                }else{
                    if(this.mode === "datePicker"){
                        switch(match[2]) {
                            case 'D':
                                this.d = parseInt(match[1]);
                                break;
                            case 'H':
                                this.h = parseInt(match[1]);
                                break;
                            case 'M':
                                this.m = parseInt(match[1]);
                                break;
                        }
                    }else{
                        switch(match[2]) {
                            case 'Y':
                                this.y = parseInt(match[1]);
                                break;
                            case 'W':
                                this.week = parseInt(match[1]);
                                break;
                            case 'M':
                                this.month = parseInt(match[1]);
                                break;
                            case 'D':
                                this.d = parseInt(match[1]);
                                break;
                        }
                    }
                }
            }
        }catch (e){
            console.error("error parsing data!",e);
        }
    }
    addDuration(){
        this.onValueSet.emit(this.generateDuration());
    }
    clear(){
        this.onValueSet.emit('empty');
    }
    generateDuration(){
        let duration = 'P';
        if(this.mode === "dcmPeriod" && this._isset(this.week) ){
            return `P${this.week}W`;
        }else {
            if (this.y) {
                duration = duration + `${this.y}Y`;
            }
            if (this.month) {
                duration = duration + `${this.month}M`;
            }
            if (this.d) {
                duration = duration + `${this.d}D`;
            }
            if (this.h || this.m || this.s) {
                duration = duration + "T";
            }
            if (this.h) {
                duration = duration + `${this.h}H`;
            }
            if (this.m) {
                duration = duration + `${this.m}M`;
            }
            if (this.s) {
                duration = duration + `${this.s}S`;
            }
        }
        if(duration === "P"){
            return "";
        }else  return duration;
    }
    close(){
        this.onValueSet.emit("");
    }
    noWeekChange(){
        this.week = "";
    }
    weekChanged(){
        this.y = "";
        this.month = "";
        this.d = "";
    }
    onModelChange(e){
        if(this.mode === 'dcmPeriod'){
            if(this._isset(this.week)){
                this.message = $localize `:@@this_period_will_last_week:This period will last ${this.week}:@@week: ${(this.week > 1?WEEK.plural:WEEK.singular)}@@:word_for_week:`;
            }else{
                this.message = this._generateSentenceWithCountableWords({
                    start: $localize `:@@duration-picker.this_period_will_last:This period will last`,
                    words:[
                        {
                            value:this.y,
                            word:YEAR.singular,
                            wordPlural:YEAR.plural
                        },
                        {
                            value:this.month,
                            word:MONTH.singular,
                            wordPlural:MONTH.plural
                        },
                        {
                            value:this.d,
                            word:DAY.singular,
                            wordPlural:DAY.plural
                        }
                    ]
                })
            }

        }
        if(this.mode === 'dcmDuration'){
            this.message = this._generateSentenceWithCountableWords({
                start: $localize `:@@duration-picker.this_duration_will_last:This duration will last`,
                words:[
                    {
                        value:this.d,
                        word:DAY.singular,
                        wordPlural:DAY.plural
                    },
                    {
                        value:this.h,
                        word:HOUR.singular,
                        wordPlural:HOUR.plural
                    },
                    {
                        value:this.m,
                        word:MINUTE.singular,
                        wordPlural:MINUTE.plural
                    },
                    {
                        value:this.s,
                        word:SECOND.singular,
                        wordPlural:SECOND.plural
                    }
                ]
            });
        }

    }
    _isset(v){
        if(v && v != "" && v != null && v != undefined){
            return true;
        }else{
            return false;
        }
    }
    _clearFromEmptyValue(array, objectKey){
        return _.remove(array,(m)=>{
            return this._isset(m[objectKey]);
        })
    }
    _generateSentenceWithCountableWords(o){
        o.words = this._clearFromEmptyValue(o.words, 'value');
        if(o.words.length > 0){
            if(o.words.length === 1){
                return `${o.start} ${o.words[0].value} ${(o.words[0].value > 1 ? o.words[0].wordPlural : o.words[0].word)}`
            }else{
                let msg = `${o.start}`;
                let firstPart = "";
                let middlePart = "";
                let lastPart = "";
                _.forEach(o.words,(m,i)=>{
                    if(parseInt(i) == o.words.length-1){
                        lastPart = `and ${o.words[i].value} ${(o.words[i].value > 1 ? o.words[i].wordPlural : o.words[i].word)}`;
                    }else{
                        if(middlePart === "" && firstPart === ""){
                            firstPart = `${o.words[i].value} ${(o.words[i].value > 1 ? o.words[i].wordPlural : o.words[i].word)} `;
                        }else{
                            middlePart = middlePart + `, ${o.words[i].value} ${(o.words[i].value > 1 ? o.words[i].wordPlural : o.words[i].word)} `;
                        }
                    }
                });
                return `${msg} ${firstPart}${middlePart} ${lastPart}`;
            }
        }else{
            return "";
        }
    }

}
