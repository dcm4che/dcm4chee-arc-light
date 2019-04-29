import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {SelectDropdown} from "../../interfaces";

@Component({
  selector: 'size-range-picker',
  templateUrl: './size-range-picker.component.html',
  styleUrls: ['./size-range-picker.component.scss']
})
export class SizeRangePickerComponent implements OnInit{

    modelValue : string;
    units:SelectDropdown<any>[] = [
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

    ngOnInit(){
        this.extractValueFromMainInput();
    }

    keyUpMainInput(){
        this.extractValueFromMainInput();
    }
    extractValueFromMainInput(){
        const regex = /(\d*) *(-) *(\d*)/;
        let m;
        if ((m = regex.exec(this.model)) !== null && m[2] === "-") {
            this.value.unit = "KB";
            this.value.min = m[1] || "";
            this.value.max = m[3] || ""
        }
    }

    togglePicker(){
        this.showPicker = !this.showPicker;
    }
    closeFromOutside(){
        if(this.showPicker)
            this.showPicker = false;
    }
    clear(){
        this.value = {
            min:"",
            max:"",
            unit:"MB"
        };
        this.updateValue();
    }
    setRange(){
        this.updateValue();
        this.togglePicker();
    }
    updateValue(){
        this.modelValue = (this.value.min || this.value.max) ? `${this.convert(this.value.min , this.value.unit)}-${this.convert(this.value.max, this.value.unit)}` : '';
        this.modelChange.emit(this.modelValue);
    }
    onInputChange(){
        this.updateValue();
    }
    convert(value, mode){
        if(value != ""){
            let endValue;
            switch(mode) {
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


