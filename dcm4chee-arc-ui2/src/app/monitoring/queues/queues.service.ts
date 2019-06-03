import { Injectable } from '@angular/core';
import { Headers} from '@angular/http';
import {AppService} from '../../app.service';
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {DevicesService} from "../../configuration/devices/devices.service";
import {j4care} from "../../helpers/j4care.service";
import {HttpHeaders} from "@angular/common/http";

@Injectable()
export class QueuesService{

    header = new HttpHeaders({ 'Content-Type': 'application/json' });
    constructor(public $http:J4careHttpService, public mainservice: AppService, private deviceService:DevicesService) { }

    search(queueName, status, offset, limit, dicomDeviceName,createdTime,updatedTime, batchID, orderby) {

        return this.$http.get(this.url(queueName) + '?' + this.mainservice.param(this.queryParams(status, offset, limit, dicomDeviceName,createdTime,updatedTime, batchID, orderby)))
            .map(res => j4care.redirectOnAuthResponse(res));
    };

    getCount(queueName, status, offset, limit, dicomDeviceName,createdTime,updatedTime, batchID, orderby) {
        return this.$http.get(this.url(queueName) + '/count' + '?' + this.mainservice.param(this.queryParams(status, offset, limit, dicomDeviceName,createdTime,updatedTime, batchID, orderby)))
            .map(res => j4care.redirectOnAuthResponse(res));
    };

    cancel(queueName, msgId) {
        return this.$http.post(this.url3(queueName, msgId, 'cancel'), {}, this.header);
    };

    cancelAll(filter,queueName){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`../queue/${queueName}/cancel${urlParam}`, {}, this.header)
            .map(res => j4care.redirectOnAuthResponse(res));
    }

    reschedule(queueName, msgId, filter) {
        return this.$http.post(this.url3(queueName, msgId, 'reschedule'), filter, this.header)
    };
    rescheduleAll(filter,queueName){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`../queue/${queueName}/reschedule${urlParam}`, {}, this.header)
            .map(res => j4care.redirectOnAuthResponse(res));
    }
    delete(queueName, msgId) {
        return this.$http.delete(this.url2(queueName, msgId));
    };
    deleteAll(filter,queueName){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.delete(`../queue/${queueName}${urlParam}`, this.header)
            .map(res => j4care.redirectOnAuthResponse(res));
    }
    url(queueName) {
        return '../queue/' + queueName;
    }
    url2(queueName, msgId) {
        return this.url(queueName) + '/' + msgId;
    }
    url3(queueName, msgId, command) {
        return this.url2(queueName, msgId) + '/' + command;
    }
    config(params) {
        console.log('paramsconfig', params);
        let header = new HttpHeaders({ 'Content-Type': 'application/json' });
        header.append('params', params);
        return header;
    }

    queryParams(status, offset, limit, dicomDeviceName,createdTime,updatedTime, batchID, orderby) {
        let params = {
            offset: offset,
            limit: limit,
            dicomDeviceName: dicomDeviceName,
            status: undefined,
            createdTime:createdTime,
            updatedTime:updatedTime,
            batchID:batchID,
            orderby:undefined
        };
        if (orderby != '*')
            params.orderby = orderby;
        if (status != '*')
            params.status = status;
        return params;
    }
    getDevices(){
        return this.deviceService.getDevices()
    }
    sortValues(){
        return [
            {
                value:"createdTime",
                text:"Sort by creation time (ASC)"
            },
            {
                value:"-createdTime",
                text:"Sort by creation time (DESC)"
            },
            {
                value:"updatedTime",
                text:"Sort by updated time (ASC)"
            },
            {
                value:"-updatedTime",
                text:"Sort by updated time (DESC)"
            }
        ]
    };

    statuses(){
        return [
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
            },
        ]
    }
    getFilterSchema(queueNames, devices, counText){
        return [
            {
                tag:"select",
                options:queueNames.map(d=>{
                    return{
                        text:d.description || d.name,
                        value:d.name
                    }
                }),
                showStar:true,
                filterKey:"queueName",
                description:"Queue name",
                placeholder:"Queue"
            },{
                tag:"select",
                options:this.sortValues(),
                showStar:true,
                filterKey:"orderby",
                description:"Sort",
                placeholder:"Sort"
            },
            {
                tag:"select",
                options:this.statuses(),
                showStar:true,
                filterKey:"status",
                description:"Status",
                placeholder:"Status"
            },{
                tag:"select",
                options:devices.map(d=>{
                    return{
                        text:d.dicomDescription ? `${d.dicomDescription} ( ${d.dicomDeviceName} )` : d.dicomDeviceName,
                        value:d.dicomDeviceName
                    }
                }),
                showStar:true,
                filterKey:"dicomDeviceName",
                description:"Device name",
                placeholder:"Device name"
            },
            {
                tag:"label",
                text:"Page size"
            },
            {
                tag:"input",
                type:"number",
                filterKey:"limit",
                description:"Limit"
            },
            {
                tag:"range-picker",
                filterKey:"createdTime",
                description:"Created Date"
            },
            {
                tag:"range-picker",
                filterKey:"updatedTime",
                description:"Updated Date"
            },{
                tag:"input",
                type:"text",
                filterKey:"batchID",
                description:"Batch ID",
                placeholder:"Batch ID"
            },{
                tag:"dummy"
            },
            {
                tag:"button",
                text:counText,
                id:"count",
                description:"Get Count"
            },
            {
                tag:"button",
                id:"submit",
                text:"SUBMIT",
                description:"Maximal number of tasks in returned list"
            }
        ]
    }
}
