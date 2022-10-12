import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {DicomLevel, SelectDropdown} from "../../interfaces";
import {StudyService} from "../../study/study/study.service";
import {j4care} from "../../helpers/j4care.service";
import * as _ from 'lodash-es';
import {forkJoin} from "rxjs";
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
  @Input() iodFileNames:string[];

  @Input('model')
  set model(value){
    this._model = value;
  }
  get model(){
    return this._model;
  }

  @Output() modelChange =  new EventEmitter();
  selectorOpen:boolean = false;
  filterModel = {};
  constructor(
      private studyService:StudyService
  ) { }

  allModified=false;
  modifiedAttr;
  iod:SelectDropdown<any>[];
  ngOnInit(): void {
    if(!this.iodFileNames || this.iodFileNames.length === 0){
      this.iodFileNames = [
          "patient",
          "study"
      ];
    }
    this.getIodObjects();
  }
  getIodObjects(){
    const iodServices = [];
    this.iodFileNames.forEach(iodFileName=>{
      iodServices.push(this.studyService.getIod(iodFileName));
    });
    forkJoin(iodServices).subscribe(iod=>{
      this.iod = this.iodToSelectedDropdown(iod.reduce((n0,n1)=>Object.assign(n0,n1)));
    });
  }
  iodToSelectedDropdown(iodObject):SelectDropdown<any>[]{
    return this.getAllAttributeKeyPathsFromIODObject(iodObject).map(iodKey=>{
      let label = iodKey.replace(/(\w){8}/g,(g)=>{ // get DICOM label [chain] to key [chain]
        return DCM4CHE.elementName.forTag(g);
      });
      return new SelectDropdown(iodKey,label,`${label} ( ${iodKey} )`,undefined,undefined,{
        key:iodKey,
        label:label
      });
    });
  }
  getAllAttributeKeyPathsFromIODObject(iodObject){
    return _.uniqWith(
        Object.keys(j4care.flatten(iodObject)).map(key=>{
          return key.replace(/\.items|\.enum|\.multi|\.required|\.vr|\[\w\]/g,""); //remove everything that is not a DICOM attribute
        }),
        _.isEqual
    );
  }
  toggleSelector(){
    this.selectorOpen = !this.selectorOpen;
  }
  hardClear(){
    this.allModified = false;
    this.modifiedAttr = [];
    this.stateText = "";
    this.modelChange.emit(undefined);
  }
  changeAllModified(e){
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
