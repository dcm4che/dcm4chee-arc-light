import { Injectable } from '@angular/core';
import {J4careHttpService} from "../../../helpers/j4care-http.service";
import {AppService} from "../../../app.service";
import {j4care} from "../../../helpers/j4care.service";

@Injectable()
export class CsvUploadService {

  constructor(
      private $http:J4careHttpService,
      public mainservice:AppService
  ) { }

  uploadCSV(url, file, semicolon, onloadend, onerror){
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
        let contentTyp = "text/csv";
        if(semicolon){
            contentTyp += ';delimiter=semicolon';
        }
        xmlHttpRequest.setRequestHeader("Content-Type",contentTyp);
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
