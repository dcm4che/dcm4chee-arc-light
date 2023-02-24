import { Injectable } from '@angular/core';
import {AppService} from "../../app.service";
import {PersonNamePipe} from "../../pipes/person-name.pipe";
import {
  PATIENT_NAME_PARTS,
  PERSON_NAME_PARTS_BY_LES_PROBABILITY
} from "../../models/patient-dicom";
import * as _ from "lodash-es";
import {j4care} from "../../helpers/j4care.service";
@Injectable({
  providedIn: 'root'
})
export class PersonNamePickerService {
  nameKeys = PATIENT_NAME_PARTS;

  constructor(
      private appService:AppService
  ) { }
  convertPNameFromFormattedToDicomForm(inputPersonName:string, format = `{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME}, {NAME-SUFFIX}`):string{
    if(inputPersonName.indexOf("^") > -1 ){
      return inputPersonName;
    }
    let splittedName = this.splitToModules(inputPersonName);
    let splittedFormat = this.splitToModules(format);
    let collectedParts = {};
    if(splittedFormat.length === splittedName.length){
      this.mapOnEqual(splittedFormat,splittedName, collectedParts);
    }else{
      let usedText = [];
      splittedFormat = splittedFormat.filter((format,i)=>{
        //extract prefix and suffix by checking if any of the strings have .
        let check = true;
        if((format.indexOf("PREFIX") > -1 || format.indexOf("SUFFIX") > -1)){
          splittedName = splittedName.filter(name=>{
            if(name && name.indexOf(".") > -1){
              let cleanedFormatPart = this.nameKeys.filter(e=>format.indexOf(e) > -1)[0];
              const extractedString = this.extractPatientComponent(splittedFormat[i],cleanedFormatPart, name);
              if(cleanedFormatPart && !collectedParts[cleanedFormatPart] && usedText.indexOf(extractedString) === -1){
                collectedParts[cleanedFormatPart] = extractedString;
                usedText.push(extractedString)
                check = false;
                return false;
              }
            }
            return true;
          })
        }
        return check;
      });
      if(splittedFormat.length > splittedName.length){
        [splittedFormat, splittedName] = this.equalizeFormatWithNameComponents(splittedFormat,splittedName);
        this.mapOnEqual(splittedFormat, splittedName,collectedParts);
      }
    }
    return `${
      collectedParts["FAMILY-NAME"] || ""
    }^${
      collectedParts["GIVEN-NAME"] || ""
    }^${
      collectedParts["MIDDLE-NAME"] || ""
    }^${
      collectedParts["NAME-PREFIX"] || ""
    }^${
      collectedParts["NAME-SUFFIX"] || ""
    }`;
  }
  mapOnEqual(formats, names, collectedParts){
    for(let i = 0;i < formats.length; i++){
      let cleanedFormatPart = this.nameKeys.filter(e=>formats[i].indexOf(e) > -1)[0];
      if(cleanedFormatPart){
        collectedParts[cleanedFormatPart] = this.extractPatientComponent(formats[i],cleanedFormatPart, names[i]);
      }
    }
  }
  equalizeFormatWithNameComponents(formats, names){
    try{
      if(formats.length > names.length){
        formats = this.removeFormatComponentByProbability(formats);
      }else{
        if(formats.length === names.length){
          return [formats,names];
        }else{
          names = j4care.mergeArrayAtPosition(names,0);
        }
      }
      return this.equalizeFormatWithNameComponents(formats, names);
    }catch (e) {}
  }

  removeFormatComponentByProbability(formats:string[]){
    try {
      let removed = 0;
      let filtered;
      PERSON_NAME_PARTS_BY_LES_PROBABILITY.every(nameComponent=>{
        filtered = formats.filter(format=>{
          if(format.indexOf(nameComponent) > -1){
            removed++;
            return false;
          }else{
            return true;
          }
        });
        if(removed > 0){
          return false;
        }
        return true;
      });
      return filtered;
    }catch (e) {
      return formats;
    }
  }
  extractPatientComponent(formatterKey, key,string){
    try{
      const regexString = formatterKey.replace(`{${key}}`,`([\\w. -]+)`);
      const regex = new RegExp(regexString);
      let extracted = regex.exec(string);
      return extracted[1];
    }catch (e) {
      return string;
    }
  }
  splitToModules(string){
    const splitted = string.split(" ");
    let prev = "";
    let newArray = [];
    splitted.forEach((el,i)=>{
      if(prev.indexOf(".") > -1 && el.indexOf(".") > -1){
        if(newArray.length > 0){
          newArray.splice(newArray.length-1,1);
        }
        newArray.push(prev + " " + el);
      }else{
        newArray.push(el);
      }
      prev = el;
    });
    return newArray;
  }
  convertPNameFromDicomFormToFormatted(input:string, format = `{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME}, {NAME-SUFFIX}`):string{
    const formatPipe = new PersonNamePipe();
    return formatPipe.transform(input, format);
  }
}
