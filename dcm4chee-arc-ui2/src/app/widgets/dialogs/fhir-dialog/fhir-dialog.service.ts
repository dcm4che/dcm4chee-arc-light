import { Injectable } from '@angular/core';
import {DcmWebApp} from "../../../models/dcm-web-app";
import {HttpHeaders} from "@angular/common/http";
import {J4careHttpService} from "../../../helpers/j4care-http.service";
import {AppService} from "../../../app.service";
import * as _ from 'lodash-es'

@Injectable({
  providedIn: 'root'
})
export class FhirDialogService {

  constructor(
      private $http:J4careHttpService,
      private appService:AppService,
  ) { }
  createFHIRImageStudy(dcmWebApp:DcmWebApp, studyInstanceUID:string,studyWebApp:DcmWebApp){
    let webAppTemp = _.cloneDeep(studyWebApp);
    webAppTemp.dcmWebServicePath = webAppTemp.dcmWebServicePath + `/studies/${studyInstanceUID}/fhir/${dcmWebApp.dcmWebAppName}`;
    return this.$http.post(
        '',
        {},
        new HttpHeaders({'Content-Type': 'application/fhir+json', 'Accept': 'application/fhir+json'}),
        undefined,
        webAppTemp,
        {},
        false,
        'response'
    );
  }
}
