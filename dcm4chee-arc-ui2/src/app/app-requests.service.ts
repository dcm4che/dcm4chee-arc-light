import { Injectable } from '@angular/core';
import {Observable} from "rxjs/index";
import {J4careHttpService} from "./helpers/j4care-http.service";
import {AppService} from "./app.service";
import {j4care} from "./helpers/j4care.service";

@Injectable({
  providedIn: 'root'
})
export class AppRequestsService {

  constructor(
      private $http:J4careHttpService,
      private appService:AppService
  ) { }

    getServerTime(url?:string){
        return this.$http.get(`${url ? j4care.addLastSlash(url) : j4care.addLastSlash(this.appService.baseUrl)}monitor/serverTime`)
    }

    getPDQServices(url?:string):Observable<any[]>{
        return this.$http.get(`${url ? j4care.addLastSlash(url) : j4care.addLastSlash(this.appService.baseUrl)}pdq`)
    }

    getDeviceName(){
        return this.appService.getDcm4cheeArc();
    }
    getDeviceInfo(dicomDeviceName:string, url?:string){
        return this.$http.get(`${url ? j4care.addLastSlash(url) : j4care.addLastSlash(this.appService.baseUrl)}devices?dicomDeviceName=${dicomDeviceName}`)
    }
}
