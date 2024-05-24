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
  @Input() i_familyName;
  @Input() i_givenName;
  @Input() i_middleName;
  @Input() i_namePrefix;
  @Input() i_nameSuffix;
  @Input() p_familyName;
  @Input() p_givenName;
  @Input() p_middleName;
  @Input() p_namePrefix;
  @Input() p_nameSuffix;
  dialogOpen:boolean = false;
  private _internModel:string;
  private _asFilterModel = "";
  inputMode:("alphabetic"|"ideographic"|"phonetic") = "alphabetic";
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

    let collected:string[];
    let filterPrefix = "";
    if(this.inputMode === "ideographic"){
      collected = [
        this.i_familyName,
        this.i_givenName,
        this.i_middleName,
        this.i_namePrefix,
        this.i_nameSuffix
      ];
      filterPrefix = "=";
    } else if(this.inputMode === "phonetic"){
      collected = [
        this.p_familyName,
        this.p_givenName,
        this.p_middleName,
        this.p_namePrefix,
        this.p_nameSuffix
      ];
      filterPrefix = "==";
    }else{
      collected = [
        this.familyName,
        this.givenName,
        this.middleName,
        this.namePrefix,
        this.nameSuffix
      ];
    }
    if(collected.join("") != ""){
      this.asFilterModel = filterPrefix + this.personNameService.addCarets(collected[0], collected[1], collected[2], collected[3], collected[4]);
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

  mode(mode: ("alphabetic"|"ideographic"|"phonetic")) {
      this.inputMode = mode;
      this.onComponentChange();
  }
}
