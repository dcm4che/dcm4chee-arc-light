import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {SelectDropdown} from "../../interfaces";

@Component({
  selector: 'size-range-picker',
  templateUrl: './size-range-picker.component.html',
  styleUrls: ['./size-range-picker.component.scss']
})
export class SizeRangePickerComponent {

    modelValue : string;
    units:SelectDropdown[] = [
        new SelectDropdown("GB","GB"),
        new SelectDropdown("MB","MB"),
        new SelectDropdown("KB","KB"),
    ];
    value = {
        min:"",
        max:"",
        unit:"MB"
    };
    showPicker:boolean = false;
    @Output()
    modelChange = new EventEmitter<string>();

    @Input()
    get model(){
        return this.modelValue;
    }

    set model(val) {
        this.modelValue = val;
        this.modelChange.emit(this.modelValue);
    }

    filterChanged(){

    }
    togglePicker(){
        this.showPicker = !this.showPicker;
    }
    clear(){
        this.value = {
            min:"",
            max:"",
            unit:"MB"
        };
    }
    setRange(){
        this.modelValue = (this.value.min || this.value.max) ? `${this.convert(this.value.min , this.value.unit)}-${this.convert(this.value.max, this.value.unit)}` : '';
        this.modelChange.emit(this.modelValue);
        this.togglePicker();
    }

    convert(value, mode){
        if(value != ""){
            let endValue;
            switch(mode) {
                case "TB":
                    endValue = value * 1000000000;
                    break;
                case "GB":
                    endValue = value * 1000000;
                    break;
                case "MB":
                    endValue = value * 1000;
                    break;
            }
            return endValue || value;
        }
        return "";
    };
}


