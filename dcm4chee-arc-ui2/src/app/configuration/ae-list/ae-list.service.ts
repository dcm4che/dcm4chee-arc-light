import { Injectable } from '@angular/core';
import {DevicesService} from "../devices/devices.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {j4care} from "../../helpers/j4care.service";
import * as _ from 'lodash-es';
import {AppService} from "../../app.service";
@Injectable()
export class AeListService {

    constructor(
      private $http:J4careHttpService,
      private devicesService:DevicesService,
      private appService:AppService
    ) { }

    getAes(filters?){
      return this.$http.get(
          `${j4care.addLastSlash(this.appService.baseUrl)}aes`
      )
    }
    getAets(){
       return this.$http.get(
            `${j4care.addLastSlash(this.appService.baseUrl)}aets`
        )
    }
    getDevices(){
        return this.devicesService.getDevices();
    }
    echoAe(callingAet, externalAet,data){
        return  this.$http.post(
            `${j4care.addLastSlash(this.appService.baseUrl)}aets/${callingAet}/dimse/${externalAet}${j4care.getUrlParams(data)}`,
            {}
        )
    }
    generateEchoResponseText(response){
        if (_.hasIn(response, 'errorMessage') && response.errorMessage != ''){
            return {
                title:$localize `:@@error:Error`,
                text:response.errorMessage,
                status:"error"
            };
        }else{
            return {
                title:$localize `:@@info:Info`,
                status:"info",
                text: $localize `:@@echo_accomplished:Echo successfully accomplished!<br>- Connection time: ${
                    response.connectionTime
                    }:@@connection_time: ms<br/>- Echo time: ${
                    response.echoTime
                    }:@@echo_time: ms<br/>- Release time: ${
                    response.releaseTime
                    } ms`
            }
        }
    }

    getFiltersSchema(){
        return j4care.prepareFlatFilterObject([
            {
                tag:"input",
                type:"text",
                filterKey:"dicomDeviceName",
                description:$localize `:@@device_name:Device name`,
                placeholder:$localize `:@@device_name:Device name`
            },{
                tag:"input",
                type:"text",
                filterKey:"dicomAETitle",
                description:$localize `:@@aetitle:AE Title`,
                placeholder:$localize `:@@aetitle:AE Title`
            },{
                tag:"input",
                type:"text",
                filterKey:"dicomDescription",
                description:$localize `:@@description:Description`,
                placeholder:$localize `:@@description:Description`
            },{
                tag:"input",
                type:"text",
                filterKey:"dicomAssociationInitiator",
                description:$localize `:@@ae-list.association_initiator:Association Initiator`,
                placeholder:$localize `:@@ae-list.association_initiator:Association Initiator`
            },{
                tag:"input",
                type:"text",
                filterKey:"dicomAssociationAcceptor",
                description:$localize `:@@ae-list.association_acceptor:Association Acceptor`,
                placeholder:$localize `:@@ae-list.association_acceptor:Association Acceptor`
            },{
                tag:"input",
                type:"text",
                filterKey:"dicomApplicationCluster",
                description:$localize `:@@application_cluster:Application Cluster`,
                placeholder:$localize `:@@application_cluster:Application Cluster`
            },
            {
                tag: "button",
                id: "submit",
                text: $localize `:@@SUBMIT:SUBMIT`,
                description: $localize `:@@ae-list.query_ae_list:Query AE List`
            }
        ],2)
    }
}
