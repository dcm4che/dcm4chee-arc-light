import { Injectable } from '@angular/core';
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

    getAes(filters?){
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

    getFiltersSchema(){
        return j4care.prepareFlatFilterObject([
            {
                tag:"input",
                type:"text",
                filterKey:"dicomDeviceName",
                description:"Device name",
                placeholder:"Device name"
            },{
                tag:"input",
                type:"text",
                filterKey:"dicomAETitle",
                description:"AE Title",
                placeholder:"AE Title"
            },{
                tag:"input",
                type:"text",
                filterKey:"dicomDescription",
                description:"Description",
                placeholder:"Description"
            },{
                tag:"input",
                type:"text",
                filterKey:"dicomAssociationInitiator",
                description:"Association Initiator",
                placeholder:"Association Initiator"
            },{
                tag:"input",
                type:"text",
                filterKey:"dicomAssociationAcceptor",
                description:"Association Acceptor",
                placeholder:"Association Acceptor"
            },{
                tag:"input",
                type:"text",
                filterKey:"dicomApplicationCluster",
                description:"Application Cluster",
                placeholder:"Application Cluster"
            },
            {
                tag: "button",
                id: "submit",
                text: "SUBMIT",
                description: "Query AE List"
            }
        ],2)
    }
}
