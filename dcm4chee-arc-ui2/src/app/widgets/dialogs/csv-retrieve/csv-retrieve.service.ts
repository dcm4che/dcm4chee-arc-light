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

  uploadCSV(filters, file){
    let clonedFilters = {};
    if(filters['priority']) clonedFilters['priority'] = filters['priority'];
    if(filters['batchID']) clonedFilters['batchID'] = filters['batchID'];
    let url = `../aets/${filters.aet}/dimse/${filters.externalAET}/studies/csv:${filters.field}/export/dicom:${filters.destinationAET}${j4care.getUrlParams(clonedFilters)}`;
    let xmlHttpRequest = new XMLHttpRequest();
    let boundary = Math.random().toString().substr(2);
    xmlHttpRequest.open('POST', url, true);
    let dashes = '--';
    let crlf = '\r\n';
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
        xmlHttpRequest.setRequestHeader("Content-Type","application/zip");
        xmlHttpRequest.upload.onprogress = (e)=>{
            if (e.lengthComputable) {
                console.log("e",e);
            }
        };
        if(!this.mainservice.global.notSecure) {
            xmlHttpRequest.setRequestHeader('Authorization', `Bearer ${token}`);
        }
        xmlHttpRequest.upload.onloadend = (e)=>{
            // dialogRef.close('ok');
            console.log("load end");
        };
        xmlHttpRequest.send(file);
    });

  }
}
