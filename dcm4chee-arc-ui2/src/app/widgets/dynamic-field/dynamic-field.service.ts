import { Injectable } from '@angular/core';
import {DevicesService} from "../../configuration/devices/devices.service";
import {AppService} from "../../app.service";
import * as _ from 'lodash-es';
import {Hl7ApplicationsService} from "../../configuration/hl7-applications/hl7-applications.service";
import {AeListService} from "../../configuration/ae-list/ae-list.service";
import {WebAppsListService} from "../../configuration/web-apps-list/web-apps-list.service";
import {of} from "rxjs";

@Injectable()
export class DynamicFieldService {

  constructor(
      private mainservice:AppService,
      private deviceService:DevicesService,
      private aeListService:AeListService,
      private hl7service:Hl7ApplicationsService,
      private webAppListService:WebAppsListService
  ) { }
    getDevice(){
        if(_.hasIn(this.mainservice.global,'devices')){
            return of(this.mainservice.global.devices);
        }else{
            return this.deviceService.getDevices();
        }
    }
    getAets(){
        if(_.hasIn(this.mainservice.global,'aes')){
            return of(this.mainservice.global.aes);
        }else{
            return this.aeListService.getAes();
        }
    }
    getHl7(){
        if(_.hasIn(this.mainservice.global,'hl7')){
            return of(this.mainservice.global.hl7);
        }else{
            return this.hl7service.getHl7ApplicationsList('');
        }
    }
    getWebApp(){
        if(_.hasIn(this.mainservice.global,'webApp')){
            return of(this.mainservice.global.webApp);
        }else{
            return this.webAppListService.getWebApps();
        }
    }
}
