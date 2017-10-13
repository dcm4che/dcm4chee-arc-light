import { Injectable } from '@angular/core';
import {Http} from "@angular/http";
import {WindowRefService} from "../helpers/window-ref.service";
import {DevicesService} from "../devices/devices.service";
import {J4careHttpService} from "../helpers/j4care-http.service";

@Injectable()
export class AeListService {

    constructor(
      private $http:J4careHttpService,
      private devicesService:DevicesService
    ) { }

    getAes(){
      return this.$http.get(
          '../aes'
      ).map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
    }
    getAets(){
       return this.$http.get(
            '../aets'
        ).map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})

    }
    getDevices(){
        return this.devicesService.getDevices();
    }
}
