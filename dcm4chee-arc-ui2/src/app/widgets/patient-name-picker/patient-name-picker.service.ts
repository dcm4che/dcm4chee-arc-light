import { Injectable } from '@angular/core';
import {AppService} from "../../app.service";
import {PersonNamePipe} from "../../pipes/person-name.pipe";
import {PATIENT_NAME_PARTS} from "../../models/patient-dicom";

@Injectable({
  providedIn: 'root'
})
export class PatientNamePickerService {

  constructor(
      private appService:AppService
  ) { }
  convertPNameFromFormattedToDicomForm(input:string, format = `{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME}, {NAME-SUFFIX}`):string{
    const nameKeys = PATIENT_NAME_PARTS;
    nameKeys.forEach(key=>{
      if(format.indexOf(`{${key}}`) > -1){

      }
    })
    return "";
  }
  convertPNameFromDicomFormToFormatted(input:string, format = `{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME}, {NAME-SUFFIX}`):string{
    const formatPipe = new PersonNamePipe();
    return formatPipe.transform(input, format);
  }
}
