import {Component, ElementRef, EventEmitter, Input, OnInit, Output, ViewChild} from '@angular/core';
import {j4care} from "../../helpers/j4care.service";
import {Moment} from "moment/moment";
import {RangeObject} from "../../interfaces";


@Component({
  selector: 'date-picker',
  templateUrl: './date-picker.component.html',
  styleUrls: ['./date-picker.component.scss']
})
export class DatePickerComponent implements OnInit{
  private _model;
  get model() {
    return this._model;
  }

  @Input()
  set model(value) {
    this._model = value;
    this.setValues(value);
  }
  inputMask;
  originalInput:string;
  @ViewChild('datePicker') datePicker: ElementRef<HTMLInputElement>;
  @Input() placeholder;
  @Input() title;
  @Input() format:string;
  @Output() onValueSet = new EventEmitter();
  @Input() returnAsDateType:boolean = false;
  @Input() datePickerMode:boolean = false;
  dialogOpen:boolean;
  private _showPicker:boolean;
  get showPicker(): boolean {
    return this._showPicker;
  }
  @Input()
  set showPicker(value: boolean) {
    console.log("in showpickerset",value);
    this._showPicker = value;
  }
  ngOnInit(): void {
    console.log("init date picker",this._model);
    if(this.datePickerMode){
      this.dialogOpen = true;
      this.togglePicker(true);
    }
  }
  setValues(value){
    try{
      const extractedDate:RangeObject = j4care.extractDateTimeFromString(value);
      this.originalInput = j4care.formatDate(extractedDate.firstDateTime.dateObject, "yyyy-MM-dd");
      this.inputMask = j4care.formatDate(extractedDate.firstDateTime.dateObject, this.format);
    }catch (e) {}
  }
  onOriginalChange(){
    try{
      const originalInputDate:Date = new Date(this.originalInput);
      this.inputMask = j4care.formatDate(originalInputDate, this.format);
      if(this.returnAsDateType){
        this.onValueSet.emit(originalInputDate);
      }else{
        this.onValueSet.emit(this.inputMask);
      }
    }catch (e) {}
  }

  togglePicker(wait?:boolean) {
    //console.log("this.pciker",this.datePicker);
    this.dialogOpen = !this.dialogOpen;
    if(wait){
      setTimeout(()=>{
        this.datePicker.nativeElement.showPicker()
      },100)
    }else{
      this.datePicker.nativeElement.showPicker();
    }
  }

}
