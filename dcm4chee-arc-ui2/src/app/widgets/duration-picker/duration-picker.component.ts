import {Component, OnInit, EventEmitter, Output, Input} from '@angular/core';
import * as _ from 'lodash';

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
                this.message = `This period will last ${this.week} week${(this.week > 1?'s':'')}`;
            }else{
                this.message = this._generateSentenceWithCountableWords({
                    start: 'This period will last',
                    words:[
                        {
                            value:this.y,
                            word:'year',
                            wordPlural:"years"
                        },
                        {
                            value:this.month,
                            word:'month',
                            wordPlural:"months"
                        },
                        {
                            value:this.d,
                            word:'day',
                            wordPlural:"days"
                        }
                    ]
                })
            }

        }
        if(this.mode === 'dcmDuration'){
            this.message = this._generateSentenceWithCountableWords({
                start: 'This duration will last',
                words:[
                    {
                        value:this.d,
                        word:'day',
                        wordPlural:"days"
                    },
                    {
                        value:this.h,
                        word:'hour',
                        wordPlural:"hours"
                    },
                    {
                        value:this.m,
                        word:'minute',
                        wordPlural:"minutes"
                    },
                    {
                        value:this.s,
                        word:'second',
                        wordPlural:"seconds"
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
