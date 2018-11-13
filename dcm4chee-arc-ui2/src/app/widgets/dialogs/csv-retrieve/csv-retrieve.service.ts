import { Injectable } from '@angular/core';
import {J4careHttpService} from "../../../helpers/j4care-http.service";
import {AppService} from "../../../app.service";
import {j4care} from "../../../helpers/j4care.service";

@Injectable()
export class CsvRetrieveService {

  constructor(
      private $http:J4careHttpService,
      public mainservice:AppService
  ) { }

  uploadCSV(filters, file, onloadend, onerror){
    let clonedFilters = {};
    if(filters['priority']) clonedFilters['priority'] = filters['priority'];
    if(filters['batchID']) clonedFilters['batchID'] = filters['batchID'];
    let url = `../aets/${filters.aet}/dimse/${filters.externalAET}/studies/csv:${filters.field}/export/dicom:${filters.destinationAET}${j4care.getUrlParams(clonedFilters)}`;
    let xmlHttpRequest = new XMLHttpRequest();
    xmlHttpRequest.open('POST', url, true);
    let token;
    this.$http.refreshToken().subscribe((response) => {
        if(!this.mainservice.global.notSecure){
            if (response && response.length != 0) {
                this.$http.resetAuthenticationInfo(response);
                token = response['token'];
            } else {
                token = this.mainservice.global.authentication.token;
            }
        }
        xmlHttpRequest.setRequestHeader("Content-Type","text/csv");
        if(!this.mainservice.global.notSecure) {
            xmlHttpRequest.setRequestHeader('Authorization', `Bearer ${token}`);
        }
        xmlHttpRequest.onreadystatechange = function() {
            if (xmlHttpRequest.readyState == XMLHttpRequest.DONE) {
                onloadend.call(this, xmlHttpRequest);
            }
        };
        xmlHttpRequest.onerror = (e)=>{
            onerror.call(this, e);
        };
        xmlHttpRequest.send(file);
    });

  }
}
