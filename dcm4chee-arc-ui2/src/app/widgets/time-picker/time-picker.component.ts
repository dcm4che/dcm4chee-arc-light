import {Component, OnInit, EventEmitter, Output} from '@angular/core';

@Component({
  selector: 'time-picker',
  templateUrl: './time-picker.component.html',
  styleUrls: ['./time-picker.component.css']
})
export class TimePickerComponent implements OnInit {

    @Output() onValueSet = new EventEmitter();
    constructor() { }

    hhArray = [];
    mmArray = [];
    ssArray = [];
    hh = '00';
    mm = '00';
    ss = '00';
    ngOnInit() {
        let i = 0;
        while(i < 61){
            if(i < 25){
                this.hhArray.push(i < 10 ? `0${i}`:i);
            }
            this.mmArray.push(i < 10 ? `0${i}`:i);
            this.ssArray.push(i < 10 ? `0${i}`:i);
            i++;
        }
    }

    addTime(){
        this.onValueSet.emit(`${this.hh}:${this.mm}:${this.ss}`);
    }
}
