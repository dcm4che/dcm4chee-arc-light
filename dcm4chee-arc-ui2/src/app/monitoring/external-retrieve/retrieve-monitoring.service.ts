import { Injectable } from '@angular/core';
import { Headers } from '@angular/http';
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {WindowRefService} from "../../helpers/window-ref.service";
import {AppService} from "../../app.service";
import {DevicesService} from "../../configuration/devices/devices.service";
import * as _ from 'lodash';
import {j4care} from "../../helpers/j4care.service";
import {HttpHeaders} from "@angular/common/http";

@Injectable()
export class RetrieveMonitoringService {

    header = new HttpHeaders({ 'Content-Type': 'application/json' });
    constructor(
      public $http:J4careHttpService,
      public mainservice: AppService,
      private deviceService:DevicesService
    ) { }

    getExternalRetrieveEntries(filter, offset, batch){
        filter.offset = (offset && offset != '') ? offset : 0;
        return this.$http.get(`../monitor/retrieve${(batch?'/batch':'')}?${this.mainservice.param(filter)}`)
            .map(res => j4care.redirectOnAuthResponse(res));
    };
    getCount(filter) {
        let filterClone = _.cloneDeep(filter);
            delete filterClone.offset;
            delete filterClone.limit;
            delete filterClone.orderby;
        return this.$http.get('../monitor/retrieve/count' + '?' + this.mainservice.param(filterClone))
            .map(res => j4care.redirectOnAuthResponse(res));
    };
    getExporters(){
      return this.$http.get('../export')
          .map(res => j4care.redirectOnAuthResponse(res));
    }
    delete(pk){
        return this.$http.delete('../monitor/retrieve/' + pk);
    }
    deleteAll(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.delete(`../monitor/retrieve${urlParam}`, this.header)
            .map(res => j4care.redirectOnAuthResponse(res));
    }
    reschedule(pk, data){
        return this.$http.post(`../monitor/retrieve/${pk}/reschedule`, data);
    }
    rescheduleAll(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`../monitor/retrieve/reschedule${urlParam}`, {}, this.header)
            .map(res => j4care.redirectOnAuthResponse(res));
    }
    cancel(pk){
        return this.$http.post('../monitor/retrieve/' + pk + '/cancel', {});
    }

    cancelAll(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`../monitor/retrieve/cancel${urlParam}`, {}, this.header)
            .map(res => j4care.redirectOnAuthResponse(res));
    }
    downloadCsv(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        // let header = new Headers({ 'Content-Type': 'text/csv' });
        let header = new HttpHeaders({ 'Accept': 'text/csv' });
        return this.$http.get(`/dcm4chee-arc/monitor/retrieve${urlParam}`, header)
    }

    statusValues(){
        return [
            {
                value:"TO SCHEDULE",
                text:"TO SCHEDULE"
            },{
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
        ];
    }
    getFilterSchema(localAET,destinationAET,remoteAET,devices, countText, queueNames){
        let destinationAet:any = {};
        if(destinationAET){
            destinationAet = {
                tag:"select",
                options:destinationAET,
                showStar:true,
                filterKey:"DestinationAET",
                placeholder:"Destination AET",
                description:"Destination AE Title to filter by"
            };
        }else{
            destinationAet = {
                tag:"input",
                type:"text",
                filterKey:"DestinationAET",
                placeholder:"Destination AET",
                description:"Destination AE Title to filter by"
            }
        }
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
                        tag:"multi-select",
                        options:queueNames,
                        filterKey:"dcmQueueName",
                        description:"Queue Name",
                        showSearchField:true,
                        showStar:true,
                        placeholder:"Queue Name"
                    },
                    destinationAet
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
                        tag:"input",
                        type:"text",
                        filterKey:"batchID",
                        description:"Batch ID",
                        placeholder:"Batch ID"
                    },
                    {
                        tag:"select",
                        options:this.statusValues(),
                        filterKey:"status",
                        showStar:true,
                        description:"Status of tasks to filter by",
                        placeholder:"Status"
                    }
                ]
            ],[
                [
                    {
                        tag:"range-picker",
                        filterKey:"createdTime",
                        description:"Created Date"
                    },
                    {
                        tag:"range-picker",
                        filterKey:"updatedTime",
                        description:"Updated Date"
                    }
                ],
                [
                    {
                        tag:"select",
                        options:[{
                                value:'createdTime',
                                text:'Sort by creation time (ASC)'
                            },
                            {
                                value:'-createdTime',
                                text:'Sort by creation time (DESC)'
                            },
                            {
                                value:'updatedTime',
                                text:'Sort by updated time (ASC)'
                            },
                            {
                                value:'-updatedTime',
                                text:'Sort by updated time (DESC)'
                            }
                        ],
                        showStar:true,
                        filterKey:"orderby",
                        description:"Sort",
                        placeholder:"Sort"
                    },
                    {
                        tag:"input",
                        type:"text",
                        filterKey:"StudyInstanceUID",
                        description:"Unique Identifier of the Study to filter by",
                        placeholder:"Study Instance UID"
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
    getQueueNames(){
        return this.$http.get('../queue').map(res => j4care.redirectOnAuthResponse(res));
    }
    getDevices(){
        return this.deviceService.getDevices()
    }
}
