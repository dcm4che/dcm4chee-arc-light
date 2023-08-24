import {Component, OnInit, EventEmitter, Output, Input} from '@angular/core';
import * as _ from 'lodash-es';

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
  templateUrl: './issuer-picker.component.html',
  styleUrls: ['./issuer-picker.component.css']
})
export class IssuerPickerComponent implements OnInit {


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
    localNamespaceEntityID;
    universalEntityID;
    universalEntityIDType;
    message;

    ngOnInit() {
        this.extractIssuerFromValue();
        this.onModelChange(null);
    }

    extractIssuerFromValue(){
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
    addIssuer(){
        this.onValueSet.emit(this.generateIssuer());
    }
    clear(){
        this.onValueSet.emit('empty');
    }
    generateIssuer(){
        let issuer = '';
        if (this._isset(this.localNamespaceEntityID)) {
            issuer = this.localNamespaceEntityID;
            if (this._isset(this.universalEntityID) && this._isset(this.universalEntityIDType))
                issuer += `&${this.universalEntityID}&${this.universalEntityIDType}`;
        } else if (this._isset(this.universalEntityID) && this._isset(this.universalEntityIDType)) {
            issuer = `&${this.universalEntityID}&${this.universalEntityIDType}`;
        }

        return issuer;
    }

    close(){
        this.onValueSet.emit("");
    }

    onModelChange(e){
        this.message = $localize `:@@this_period_will_last_week:The issuer configured will be `;
        if (this._isset(this.localNamespaceEntityID)) {
            this.message += `${this.localNamespaceEntityID}`;
            if (this._isset(this.universalEntityID) && this._isset(this.universalEntityIDType))
                this.message += `&${this.universalEntityID}&${this.universalEntityIDType}`;
        } else if (this._isset(this.universalEntityID) && this._isset(this.universalEntityIDType))
            this.message += `&${this.universalEntityID}&${this.universalEntityIDType}`;

        if ((this._isset(this.universalEntityID) && !this._isset(this.universalEntityIDType))
                || (!this._isset(this.universalEntityID) && this._isset(this.universalEntityIDType)))
            this.message = $localize `:@@invalid_universal_issuer:Invalid universal issuer: ` + `&${this.universalEntityID}&${this.universalEntityIDType}`;
    }

    _isset(v){
        return v && v != "" && v != null;
    }

    _clearFromEmptyValue(array, objectKey){
        return _.remove(array,(m)=>{
            return this._isset(m[objectKey]);
        })
    }
}
