import { Injectable } from '@angular/core';
import {Observable} from "rxjs/Observable";
import {DevicesService} from "../../configuration/devices/devices.service";
import {AppService} from "../../app.service";
import * as _ from 'lodash';
import {Hl7ApplicationsService} from "../../configuration/hl7-applications/hl7-applications.service";
import {AeListService} from "../../configuration/ae-list/ae-list.service";
import "rxjs/add/observable/of";

@Injectable()
export class DynamicFieldService {

  constructor(
      private mainservice:AppService,
      private deviceService:DevicesService,
      private aeListService:AeListService,
      private hl7service:Hl7ApplicationsService
  ) { }
    getDevice(){
        if(_.hasIn(this.mainservice.global,'devices')){
            return Observable.of(this.mainservice.global.devices);
        }else{
            return this.deviceService.getDevices();
        }
    }
    getAets(){
        if(_.hasIn(this.mainservice.global,'aes')){
            return Observable.of(this.mainservice.global.aes);
        }else{
            return this.aeListService.getAes();
        }
    }
    getHl7(){
        if(_.hasIn(this.mainservice.global,'hl7')){
            return Observable.of(this.mainservice.global.hl7);
        }else{
            return this.hl7service.getHl7ApplicationsList('');
        }
    }
}
