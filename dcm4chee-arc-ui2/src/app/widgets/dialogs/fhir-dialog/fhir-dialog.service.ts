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
      private $http:J4careHttpService
  ) { }
  createFHIRImageStudy(dcmWebApp:DcmWebApp, studyInstanceUID:string,studyWebApp:DcmWebApp, responseHeaderType:string = 'json'){
    let webAppTemp = _.cloneDeep(studyWebApp);
    webAppTemp.dcmWebServicePath = webAppTemp.dcmWebServicePath + `/studies/${studyInstanceUID}/fhir/${dcmWebApp.dcmWebAppName}`;
    let headers = new HttpHeaders()
        .set('Content-Type', 'application/fhir+'+responseHeaderType)
        .set('Accept', 'application/fhir+'+responseHeaderType);
    if(responseHeaderType === 'json'){
      return this.$http.post(
          '',
          {},
          headers,
          undefined,
          webAppTemp,
          {},
          false,
          'response'
      );
    }else{
      return this.$http.post(
          '',
          {},
          headers,
          undefined,
          webAppTemp,
          {},
          false,
          undefined,
          "text"
      );
    }
  }
}
