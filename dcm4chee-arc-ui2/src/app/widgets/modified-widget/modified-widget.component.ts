import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {DicomLevel, SelectDropdown} from "../../interfaces";
import {StudyService} from "../../study/study/study.service";
import {j4care} from "../../helpers/j4care.service";
import * as _ from 'lodash-es';
import {forkJoin} from "rxjs";
import {TrimPipe} from "../../pipes/trim.pipe";
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
  Object = Object;
  Array = Array;
  allModified=false;
  modifiedAttr = new Set();
  iod:SelectDropdown<any>[];
  stateTextHover = "";
  ngOnInit(): void {
    if(!this.iodFileNames || this.iodFileNames.length === 0){
      this.iodFileNames = [
          "patient",
          "study"
      ];
    }
    if(!this.placeholder){
      this.placeholder = "Modified";
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
      let label = this.getLabelFromIODTag(iodKey);
      return new SelectDropdown(iodKey,label,`${label} ( ${iodKey} )`,undefined,undefined,{
        key:iodKey,
        label:label
      });
    });
  }
  getLabelFromIODTag(dicomTagPath){
      return dicomTagPath.replace(/(\w){8}/g,(g)=>{ // get DICOM label [chain] to key [chain]
      return DCM4CHE.elementName.forTag(g);
    });
  }
  remove(attr){
    try {
      this.modifiedAttr.delete(attr);
    }catch (e) {

    }
  }
  newAttribute;
  trim = new TrimPipe();
  addAttribute(e){
    try{
      console.log("newAttribute",this.newAttribute)
      console.log("toggleattr",e);
      if(!this.modifiedAttr.has(e) && this.newAttribute){
        this.modifiedAttr.add(e);
      }
      this.newAttribute = undefined;
    }catch (e) {(e)

    }
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
    this.modifiedAttr.clear();
    this.stateText = "";
    this.stateTextHover = "";
    this.modelChange.emit(undefined);
  }
  changeAllModified(e){
    this.allModified = e.target.checked;
    if(this.allModified){
      this.stateText = "All modified";
      this.stateTextHover = "All modified";
    }else{
      this.stateText = "";
      this.stateTextHover = "";
    }
  }
  filterChanged(){}
  setFilter(){
    if(this.allModified){
      this.modelChange.emit({
        allmodified:true
      })
    }else{
      if(this.modifiedAttr && this.modifiedAttr.size > 0){
        this.modelChange.emit({
          modified:Array.from(this.modifiedAttr.values())
        });
        if(this.modifiedAttr && this.modifiedAttr.size > 0  && this.modifiedAttr.size){
          this.stateText = this.trim.transform(Array.from(this.modifiedAttr.values()).map(kode=>this.getLabelFromIODTag(kode)).join(", "),18);
        }
        //this.stateText = Array.from(this.modifiedAttr.values()).map(kode=>this.getLabelFromIODTag(kode)).join(", ");
        this.stateTextHover = Array.from(this.modifiedAttr.values()).map(kode=>this.getLabelFromIODTag(kode)).join(", ");
      }
    }
    this.selectorOpen = false;
  }
  modifiedAttrChanged(e){
    if(this.modifiedAttr && this.modifiedAttr.size > 0){
      this.stateText = `( ${this.modifiedAttr.size} ) selected`;
    }else{
      this.stateText = "";
    }
  }
}
