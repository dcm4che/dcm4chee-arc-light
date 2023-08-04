import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {AppService} from "../../app.service";
import {PersonNamePickerService} from "./person-name-picker.service";
import * as _ from "lodash-es";

@Component({
  selector: 'person-name-picker',
  templateUrl: './person-name-picker.component.html',
  styleUrls: ['./person-name-picker.component.scss']
})
export class PersonNamePickerComponent implements OnInit {


  @Input() placeholder;
  @Input() title;
  @Input() familyName;
  @Input() givenName;
  @Input() middleName;
  @Input() namePrefix;
  @Input() nameSuffix;
  dialogOpen:boolean = false;
  private _internModel:string;
  private _asFilterModel = "";
  format:string = `{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME}, {NAME-SUFFIX}`;
  private _model:string;

  get model(): string {
    return this._model;
  }

  @Input()
  set model(value: string) {
    this._model = value;
    this.asFilterModel = value;
    if(!this._internModel){
      this.internModel = value;
    }
  }
  @Output() modelChange = new EventEmitter();
  constructor(
      private appService:AppService,
      private personNameService:PersonNamePickerService
  ) { }

  ngOnInit(): void {
    this._internModel = this.model;
    if(_.hasIn(this.appService,"global.personNameFormat")){
      this.format = this.appService.global.personNameFormat;
    }
  }

  onInternModelChange(e){
    if(this._internModel && this._internModel.indexOf(" ") > -1){
      this.asFilterModel = this.personNameService.convertPNameFromFormattedToDicomForm(this._internModel, this.format);
      [
        this.familyName,
        this.givenName,
        this.middleName,
        this.namePrefix,
        this.nameSuffix
      ] = this._asFilterModel.split("^");
    }else{
      if(this._internModel != "" && this._internModel.indexOf(" ") === -1){
        this.asFilterModel = this._internModel;
        this.familyName = "";
        this.givenName = "";
        this.middleName = "";
        this.namePrefix = "";
        this.nameSuffix = "";
      }else{
        this.asFilterModel = "";
        this.familyName = "";
        this.givenName = "";
        this.middleName = "";
        this.namePrefix = "";
        this.nameSuffix = "";
      }
    }
  }
  toggleDialog(){
    this.dialogOpen = !this.dialogOpen;
  }

  get internModel(): string {
    return this._internModel;
  }

  set internModel(value: string) {
    this._internModel = value;
  }
  onComponentChange(){
    let collected = [
      this.familyName,
      this.givenName,
      this.middleName,
      this.namePrefix,
      this.nameSuffix
    ];
    if(collected.join("") != ""){
      this.asFilterModel = this.personNameService.addCarets(this.familyName, this.givenName, this.middleName, this.namePrefix, this.nameSuffix);
      this._internModel = this.personNameService.convertPNameFromDicomFormToFormatted(this._asFilterModel, this.format)
    }else{
      this.asFilterModel = "";
      this._internModel = "";
    }
  }

  get asFilterModel(): string {
    return this._asFilterModel;
  }

  set asFilterModel(value: string) {
    this._asFilterModel = value;
/*    if(value.indexOf("=") > -1){
      this.modelChange.emit(value.replace("=","%3D"));
    }*/
    this.modelChange.emit(value);
  }
  clear(){
    this._internModel = "";
    this.onInternModelChange(undefined);
  }
}
