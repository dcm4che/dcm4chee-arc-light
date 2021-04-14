import { Injectable } from '@angular/core';
import {AppService} from '../../app.service';
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {DevicesService} from "../../configuration/devices/devices.service";
import {j4care} from "../../helpers/j4care.service";
import {HttpHeaders} from "@angular/common/http";

@Injectable()
export class QueuesService{

    header = new HttpHeaders({ 'Content-Type': 'application/json' });
    constructor(
        public $http:J4careHttpService,
        public mainservice: AppService,
        private deviceService:DevicesService
    ) { }

    search(queueName, status, offset, limit, dicomDeviceName,createdTime,updatedTime, batchID, orderby) {

        return this.$http.get(this.url(queueName) + '?' + this.mainservice.param(this.queryParams(status, offset, limit, dicomDeviceName,createdTime,updatedTime, batchID, orderby)));
    };

    getCount(queueName, status, offset, limit, dicomDeviceName,createdTime,updatedTime, batchID, orderby) {
        return this.$http.get(this.url(queueName) + '/count' + '?' + this.mainservice.param(this.queryParams(status, offset, limit, dicomDeviceName,createdTime,updatedTime, batchID, orderby)));
    };

    cancel(queueName, msgId) {
        return this.$http.post(this.url3(queueName, msgId, 'cancel'), {}, this.header);
    };

    cancelAll(filter,queueName){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`${j4care.addLastSlash(this.mainservice.baseUrl)}queue/${queueName}/cancel${urlParam}`, {}, this.header);
    }

    reschedule(queueName, msgId, filter) {
        return this.$http.post(this.url3(queueName, msgId, 'reschedule'), filter, this.header)
    };
    rescheduleAll(filter,queueName){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`${j4care.addLastSlash(this.mainservice.baseUrl)}queue/${queueName}/reschedule${urlParam}`, {}, this.header);
    }
    delete(queueName, msgId) {
        return this.$http.delete(this.url2(queueName, msgId));
    };
    deleteAll(filter,queueName){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.delete(`${j4care.addLastSlash(this.mainservice.baseUrl)}queue/${queueName}${urlParam}`, this.header);
    }
    url(queueName) {
        return `${j4care.addLastSlash(this.mainservice.baseUrl)}queue/${queueName}`;
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
                text:$localize `:@@sort_by_creation_time_asc:Sort by creation time (ASC)`
            },
            {
                value:"-createdTime",
                text:$localize `:@@sort_by_creation_time_desc:Sort by creation time (DESC)`
            },
            {
                value:"updatedTime",
                text:$localize `:@@sort_by_updated_time_asc:Sort by updated time (ASC)`
            },
            {
                value:"-updatedTime",
                text:$localize `:@@sort_by_updated_time_desc:Sort by updated time (DESC)`
            }
        ]
    };

    statuses(){
        return [
            {
                value:"SCHEDULED",
                text:$localize `:@@SCHEDULED:SCHEDULED`
            },
            {
                value:"IN PROCESS",
                text:$localize `:@@in_process:IN PROCESS`
            },
            {
                value:"COMPLETED",
                text:$localize `:@@COMPLETED:COMPLETED`
            },
            {
                value:"WARNING",
                text:$localize `:@@WARNING:WARNING`
            },
            {
                value:"FAILED",
                text:$localize `:@@FAILED:FAILED`
            },
            {
                value:"CANCELED",
                text:$localize `:@@CANCELED:CANCELED`
            },
        ]
    }
    getFilterSchema(queueNames, devices, countText){
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
                description:$localize `:@@queue_name:Queue Name`,
                placeholder:$localize `:@@queue:Queue`
            },{
                tag:"select",
                options:this.sortValues(),
                showStar:true,
                filterKey:"orderby",
                description:$localize `:@@sort:Sort`,
                placeholder:$localize `:@@sort:Sort`
            },
            {
                tag:"select",
                options:this.statuses(),
                showStar:true,
                filterKey:"status",
                description:$localize `:@@status:Status`,
                placeholder:$localize `:@@status:Status`
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
                description:$localize `:@@device_name:Device name`,
                placeholder:$localize `:@@device_name:Device name`
            },
            {
                tag:"label",
                text:$localize `:@@page_size:Page Size`
            },
            {
                tag:"input",
                type:"number",
                filterKey:"limit",
                description:$localize `:@@limit:Limit`
            },
            {
                tag:"range-picker",
                filterKey:"createdTime",
                description:$localize `:@@created_date:Created Date`
            },
            {
                tag:"range-picker",
                filterKey:"updatedTime",
                description:$localize `:@@updated_date:Updated Date`
            },{
                tag:"input",
                type:"text",
                filterKey:"batchID",
                description:$localize `:@@batch_id:Batch ID`,
                placeholder:$localize `:@@batch_id:Batch ID`
            },{
                tag:"dummy"
            },
            {
                tag:"button",
                text:countText,
                id:"count",
                description:$localize `:@@get_count:Get Count`
            },
            {
                tag:"button",
                id:"submit",
                text:$localize `:@@SUBMIT:SUBMIT`,
                description:$localize `:@@maximal_number_of_tasks_in_returned_list:Maximal number of tasks in returned list`
            }
        ]
    }
}
