import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {AppService} from "../../app.service";

@Component({
  selector: 'app-patient-name-picker',
  templateUrl: './patient-name-picker.component.html',
  styleUrls: ['./patient-name-picker.component.scss']
})
export class PatientNamePickerComponent implements OnInit {

  @Input() placeholder;
  @Input() title;
  @Input() familyName;
  @Input() givenName;
  @Input() middleName;
  @Input() namePrefix;
  @Input() nameSuffix;

  internModel:string;

  format:string = `{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME}, {NAME-SUFFIX}`;
  private _model:string;

  get model(): string {
    return this._model;
  }

  @Input()
  set model(value: string) {
    this._model = value;
  }
  @Output() modelChange = new EventEmitter();
  constructor(
      private appService:AppService
  ) { }

  ngOnInit(): void {

  }

  onInternModelChange(e){

  }

  convertPNameFromFormattedToDicomForm(input:string){

  }
  convertPNameFromDicomFormToFormatted(input:string){

  }
}
