import { Injectable } from '@angular/core';
import {J4careHttpService} from "../../../helpers/j4care-http.service";
import {AppService} from "../../../app.service";
import {j4care} from "../../../helpers/j4care.service";
import {KeycloakService} from "../../../helpers/keycloak-service/keycloak.service";

@Injectable()
export class CsvUploadService {

  constructor(
      private $http:J4careHttpService,
      public mainservice:AppService,
      private _keycloakService: KeycloakService
  ) { }

  uploadCSV(url, file, semicolon, onloadend, onerror){
    let xmlHttpRequest = new XMLHttpRequest();
    xmlHttpRequest.open('POST', url, true);
    let token;
    this._keycloakService.getToken().subscribe((response) => {
        if(!this.mainservice.global.notSecure){
            token = response.token;
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
