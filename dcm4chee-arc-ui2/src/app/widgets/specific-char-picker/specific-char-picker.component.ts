import {Component, OnInit, Input, Output, EventEmitter} from '@angular/core';
import {Globalvar} from "../../constants/globalvar";
import {SearchPipe} from "../../pipes/search.pipe";

@Component({
    selector: 'specific-char-picker',
    templateUrl: './specific-char-picker.component.html',
    styleUrls: ['./specific-char-picker.component.css'],
    standalone: false
})
export class SpecificCharPickerComponent implements OnInit {
    localModel;
    @Output() modelChange: EventEmitter<string> = new EventEmitter<string>();
    @Input() set model(value) {
        this.localModel = value;
    }
    @Output() onValueSet = new EventEmitter();
    @Input() value;
    @Input() mode;
    @Input() format;
    specificChar;
    filter = "";
    constructor() { }

    ngOnInit() {
        if(this.format && this.format === "hl7Charset"){
            this.specificChar = Globalvar.HL7_SPECIFIC_CHAR;
        }else{
            this.specificChar = Globalvar.DICOM_SPECIFIC_CHAR;
        }
    }
    addSelectedElement(element){
        this.onValueSet.emit(element);
    }
    modelChanged($event){
        this.localModel = $event;
        this.modelChange.emit(this.localModel);
        this.onValueSet.emit(this.localModel);
    }
    close(){
        this.onValueSet.emit("");
    }
    keyDown(e){
        if(e.keyCode === 13){
            let filtered = new SearchPipe().transform(this.specificChar, this.filter);
            if(filtered.length > 0){
                this.onValueSet.emit(filtered[0].value);
            }
        }
    }
}
