import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {DicomLevel, SelectDropdown} from "../../interfaces";
import {StudyService} from "../../study/study/study.service";
import {j4care} from "../../helpers/j4care.service";
import * as _ from 'lodash-es';
declare var DCM4CHE: any;

@Component({
  selector: 'modified-widget',
  templateUrl: './modified-widget.component.html',
  styleUrls: ['./modified-widget.component.scss']
})
export class ModifiedWidgetComponent implements OnInit {
  stateText="";
  private _model;

  @Input() placeholder:string;
  @Input() title:string;
  @Input() dicomLevel:DicomLevel;

  @Input('model')
  set model(value){
    console.log("value",value);
    this._model = value;
  }
  get model(){
    return this._model;
  }

  @Output() modelChange =  new EventEmitter();
  selectorOpen:boolean = false;
  filterModel = {};
  maiInputValid:boolean = true;
  constructor(
      private studyService:StudyService
  ) { }

  allModified=false;
  modifiedAttr;
  iod:SelectDropdown<any>[];
  ngOnInit(): void {
    if(!this.dicomLevel){
      this.dicomLevel = "study";
    }
    this.studyService.getIod(this.dicomLevel).subscribe(iod=>{
      this.iod = this.iodToSelectedDropdown(iod);
    });
  }
  iodToSelectedDropdown(iodObject):SelectDropdown<any>[]{
    let iodKeys = _.uniqWith(Object.keys(j4care.flatten(iodObject)).map(key=>{
      return key
          .replace(".items","")
          .replace(".enum","")
          .replace(".multi","")
          .replace(".required","")
          .replace(".vr","")
          .replace("[0]","")
          .replace("[1]","")
    }), _.isEqual);
    let iod = iodKeys.map(iodKey=>{
      let label = iodKey.replace(/(\w){8}/g,(g)=>{
        return DCM4CHE.elementName.forTag(g);
      });
      return new SelectDropdown(iodKey,label,`${label} ( ${iodKey} )`,undefined,undefined,{
        key:iodKey,
        label:label
      });
    });
    return iod;
  }

  togglePicker(){
    this.selectorOpen = !this.selectorOpen;
  }
  hardClear(){
    this.allModified = false;
    this.modifiedAttr = [];
    this.stateText = "";
    this.modelChange.emit(undefined);
  }
  changeAllModified(e){
    console.log("e",e.target.checked);
    this.allModified = e.target.checked;
    if(this.allModified){
      this.stateText = "All modified";
    }else{
      this.stateText = "";
    }
  }
  filterChanged(){}
  setFilter(){
    if(this.allModified){
      this.modelChange.emit({
        allmodified:true
      })
    }else{
      if(this.modifiedAttr && this.modifiedAttr.length > 0){
        this.modelChange.emit({
          allmodified:false,
          modified:this.modifiedAttr
        });
      }
    }
    this.selectorOpen = false;
  }
  modifiedAttrChanged(e){
    if(this.modifiedAttr && this.modifiedAttr.length > 0){
      this.stateText = `( ${this.modifiedAttr.length} ) selected`;
    }else{
      this.stateText = "";
    }
  }
}
