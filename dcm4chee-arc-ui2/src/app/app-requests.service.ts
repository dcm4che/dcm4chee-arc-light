import { Injectable } from '@angular/core';
import {Observable} from "rxjs/index";
import {J4careHttpService} from "./helpers/j4care-http.service";

@Injectable({
  providedIn: 'root'
})
export class AppRequestsService {

  constructor(
      private $http:J4careHttpService,
  ) { }

    getServerTime(url?:string){
        return this.$http.get(`${url || '..'}/monitor/serverTime`)
    }

    getPDQServices(url?:string):Observable<any[]>{
        return this.$http.get(`${url || '..'}/pdq`)
    }

    getDeviceName(url?:string){
        return this.$http.get(`${url || '..'}/devicename`)
    }
    getDeviceInfo(dicomDeviceName:string, url?:string){
        return this.$http.get(`${url || '..'}/devices?dicomDeviceName=${dicomDeviceName}`)
    }
}
