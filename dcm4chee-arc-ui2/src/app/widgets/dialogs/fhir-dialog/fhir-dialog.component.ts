import { Component } from '@angular/core';
import {MatOption} from "@angular/material/core";
import {MatSelect} from "@angular/material/select";
import {MatDialogRef} from "@angular/material/dialog";
import {FhirDialogService} from "./fhir-dialog.service";
import {StudyWebService} from "../../../study/study/study-web-service.model";
import {AppService} from "../../../app.service";
import {FormsModule} from "@angular/forms";
import {StudyService} from "../../../study/study/study.service";
import {DcmWebApp} from "../../../models/dcm-web-app";
import {JsonPipe, NgClass} from "@angular/common";
import {DcmDropDownComponent} from "../../dcm-drop-down/dcm-drop-down.component";
import {j4care} from "../../../helpers/j4care.service";
import {SelectDropdown} from "../../../interfaces";

@Component({
  selector: 'app-fhir-dialog',
  imports: [
    FormsModule,
    DcmDropDownComponent,
    NgClass,
    JsonPipe
  ],
  templateUrl: './fhir-dialog.component.html',
  styleUrl: './fhir-dialog.component.scss',
  standalone: true
})
export class FhirDialogComponent {
  fhirWebAppsSelectDropdowns:StudyWebService;
  selectedWebService:DcmWebApp;
  study:any;
  response:any;
  responseButton = 'headers';
  headers;
  responseTypeHeaderDropdowns:SelectDropdown<string>[] = [
    new SelectDropdown('application/fhir+json', 'application/fhir+json'),
    new SelectDropdown('application/fhir+xml', 'application/fhir+xml'),
    new SelectDropdown('*/*', '*/*'),
  ];
  responseHeaderType:string = '*/*';
  responseType = 'json';
  constructor(
      public dialogRef: MatDialogRef<FhirDialogComponent>,
      private service:FhirDialogService,
      private studyService:StudyService,
      private appService:AppService
  ) { }
  save(){
    if(!this.response){
      this.service.createFHIRImageStudy(
          this.fhirWebAppsSelectDropdowns.selectedWebService,
          this.studyService.getStudyInstanceUID(this.study),
          this.selectedWebService,
          this.responseHeaderType,
          this.responseType
      ).subscribe((res)=>{
        try{
          let extractedType:string = '';
          this.headers = res.headers.keys().map(key=>{
            const value = res.headers.get(key);
            if(key.toLowerCase() === 'content-type'){
              if(value.includes('json')){
                extractedType = 'json';
              }else if(value.includes('xml')){
                extractedType = 'xml';
              }
            }
            return {key:key,value:value};
          });
          extractedType = this.extractTypeFromResponse(extractedType, res) || this.responseType;
          this.responseType = extractedType;
          if(this.responseType === 'json'){
            if(typeof res.body === 'string' && (res.body.indexOf('\n') === -1 || res.body.indexOf(' ') === -1)){
              this.response = JSON.stringify(res.body, null, 2);
            }else{
              this.response = res.body;
            }
          }else{
            this.response = res.body;
          }
          this.appService.showMsg($localize `:@@fhir_imaging_successfully:FHIR Imaging Study created successfully`);
        }catch (e) {
          this.response = res;
        }
      },err=>{
        console.error(err);
        this.appService.showError($localize `:@@fhir_imaging_fail:Create FHIR Imaging Study failed`);
      });
    }
  }

  extractTypeFromResponse(extractedType:string, res:any){
    try{
      if(extractedType === ''){
        const regex = /<[\w.<\/>="" :\.\\]*>[\n \.]*<[\w.<\/>="" :\.\\]*>/; // Detect xml Regex;
        if(regex.test(res.body)){
          extractedType = 'xml';
        }else{
          extractedType = 'json';
        }
      }
      return extractedType
    }catch (e) {
      return extractedType;
    }
  }
  protected webAppModelChange($event: any) {
    if(typeof $event === 'string'){
      this.fhirWebAppsSelectDropdowns.seletWebAppFromWebAppName($event);
    }else{
      this.fhirWebAppsSelectDropdowns.selectedWebService = $event;
    }
    const properties = j4care.extractPropertiesFromWebApp(this.fhirWebAppsSelectDropdowns.selectedWebService);
    if(j4care.hasSet(properties,"ImagingStudy")){
      if(properties['ImagingStudy'] === 'FHIR_R5_XML' || properties['ImagingStudy'] === 'LTNHR_V1_XML' || properties['ImagingStudy'].includes("XML")){
        this.responseType = 'xml';
        //this.responseHeaderType = 'application/fhir+xml';
      }else{
        this.responseType = 'json';
        //this.responseHeaderType = 'application/fhir+json';
      }
    }
  }
}
