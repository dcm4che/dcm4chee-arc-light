import { Injectable } from '@angular/core';
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {WindowRefService} from "../../helpers/window-ref.service";
import {AppService} from "../../app.service";
import {DevicesService} from "../../devices/devices.service";
import * as _ from 'lodash';

@Injectable()
export class ExternalRetrieveService {

    constructor(
      public $http:J4careHttpService,
      public mainservice: AppService,
      private deviceService:DevicesService
    ) { }

    getExternalRetrieveEntries(filter, offset){
        filter.offset = (offset && offset != '') ? offset : 0;
        return this.$http.get('../monitor/retrieve' + '?' + this.mainservice.param(filter))
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
    };
    getCount(filter) {
        let filterClone = _.cloneDeep(filter);
            delete filterClone.offset;
            delete filterClone.limit;
        return this.$http.get('../monitor/retrieve/count' + '?' + this.mainservice.param(filterClone))
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
    };
    getExporters(){
      return this.$http.get('../export')
          .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})

    }
    delete(pk){
        return this.$http.delete('../monitor/retrieve/' + pk);
    }
    reschedule(pk){
        return this.$http.post(`../monitor/retrieve/${pk}/reschedule`, {});
    }
    cancel(pk){
        return this.$http.post('../monitor/retrieve/' + pk + '/cancel', {});
    }
    getFilterSchema(localAET,destinationAET,remoteAET,devices, countText){
    return [
        [
                [
                    {
                        tag:"label",
                        text:"Device name"
                    },
                    {
                        tag:"select",
                        options:devices,
                        showStar:true,
                        filterKey:"dicomDeviceName",
                        description:"Device Name to filter by"
                    }
                ],[
                    {
                        tag:"label",
                        text:"LocalAET"
                    },{
                        tag:"select",
                        options:localAET,
                        showStar:true,
                        filterKey:"LocalAET",
                        description:"Archive AE Title to filter by"
                    }
                ],
                [
                    {
                        tag:"label",
                        text:"RemoteAET"
                    },
                    {
                        tag:"select",
                        options:remoteAET,
                        showStar:true,
                        filterKey:"RemoteAET",
                        description:"C-MOVE SCP AE Title to filter by"
                    }
                ]
            ],[
                [
                    {
                        tag:"label",
                        text:"DestinationAET"
                    },
                    {
                        tag:"select",
                        options:destinationAET,
                        showStar:true,
                        filterKey:"DestinationAET",
                        description:"Destination AE Title to filter by"
                    }
                ],
                [
                    {
                        tag:"label",
                        text:"StudyInstanceUID"
                    },
                    {
                        tag:"input",
                        type:"text",
                        filterKey:"StudyInstanceUID",
                        description:"Unique Identifier of the Study to filter by"
                    }
                ],
                [
                    {
                        tag:"label",
                        text:"Status"
                    },
                    {
                        tag:"select",
                        options:[
                            {
                                value:"SCHEDULED",
                                text:"SCHEDULED"
                            },
                            {
                                value:"IN PROCESS",
                                text:"IN PROCESS"
                            },
                            {
                                value:"COMPLETED",
                                text:"COMPLETED"
                            },
                            {
                                value:"WARNING",
                                text:"WARNING"
                            },
                            {
                                value:"FAILED",
                                text:"FAILED"
                            },
                            {
                                value:"CANCELED",
                                text:"CANCELED"
                            }
                        ],
                        filterKey:"status",
                        showStar:true,
                        description:"Status of tasks to filter by"
                    }
                ]
            ],[
                [
                    {
                        tag:"p-calendar",
                        filterKey:"createdTime",
                        description:"Created Date"
                    },
                    {
                        tag:"p-calendar",
                        filterKey:"updatedTime",
                        description:"Updated Date"
                    }
                ],
                [
                    {
                        tag:"label",
                        text:"Limit"
                    },
                    {
                        tag:"input",
                        type:"number",
                        filterKey:"limit",
                        description:"Maximal number of tasks in returned list"
                    }
                ],
                [
                    {
                        tag:"button",
                        id:"count",
                        text:countText,
                        description:"QUERIE ONLY THE COUNT"
                    },
                    {
                        tag:"button",
                        id:"submit",
                        text:"SUBMIT",
                        description:"Maximal number of tasks in returned list"
                    }
                ]
            ]
    ];
    }
    getDevices(){
        return this.deviceService.getDevices()
    }
}
