import { Injectable } from '@angular/core';
import {Http} from "@angular/http";
import {WindowRefService} from "../../helpers/window-ref.service";
import {DevicesService} from "../devices/devices.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {j4care} from "../../helpers/j4care.service";
import * as _ from 'lodash';
@Injectable()
export class AeListService {

    constructor(
      private $http:J4careHttpService,
      private devicesService:DevicesService
    ) { }

    getAes(){
      return this.$http.get(
          '../aes'
      ).map(res => j4care.redirectOnAuthResponse(res));
    }
    getAets(){
       return this.$http.get(
            '../aets'
        ).map(res => j4care.redirectOnAuthResponse(res));

    }
    getDevices(){
        return this.devicesService.getDevices();
    }
    echoAe(callingAet, externalAet,data){
        return  this.$http.post(
            `../aets/${callingAet}/dimse/${externalAet}${j4care.getUrlParams(data)}`,
            {}
        ).map(res => j4care.redirectOnAuthResponse(res));
    }
    generateEchoResponseText(response){
        if (_.hasIn(response, 'errorMessage') && response.errorMessage != ''){
            return {
                title:"Error",
                text:response.errorMessage,
                status:"error"
            };
        }else{
            return {
                title:"Info",
                status:"info",
                text:'Echo successfully accomplished!<br>- Connection time: ' +
                response.connectionTime +
                ' ms<br/>- Echo time: ' +
                response.echoTime +
                ' ms<br/>- Release time: ' +
                response.releaseTime + ' ms'
            }
        }
    }
}
