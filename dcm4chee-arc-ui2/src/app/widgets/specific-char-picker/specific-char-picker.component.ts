import {Component, OnInit, Input, Output, EventEmitter} from '@angular/core';
import {Globalvar} from "../../constants/globalvar";

@Component({
  selector: 'specific-char-picker',
  templateUrl: './specific-char-picker.component.html'
})
export class SpecificCharPickerComponent implements OnInit {
    localModel;
    @Output() modelChange: EventEmitter<string> = new EventEmitter<string>();
    @Input() set model(value) {
        this.localModel = value;
    }
    specificChar;
    constructor() { }

    ngOnInit() {
        this.specificChar = Globalvar.DICOM_SPECIFIC_CHAR;
    }
    modelChanged($event){
        this.localModel = $event;
        this.modelChange.emit(this.localModel);
    }
}
