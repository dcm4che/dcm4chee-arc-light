import { Injectable } from '@angular/core';
import { Headers} from '@angular/http';
import {AppService} from '../../app.service';
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {DevicesService} from "../../devices/devices.service";
import {j4care} from "../../helpers/j4care.service";

@Injectable()
export class QueuesService {

    header = new Headers({ 'Content-Type': 'application/json' });
    constructor(public $http:J4careHttpService, public mainservice: AppService, private deviceService:DevicesService) { }

    search(queueName, status, offset, limit, dicomDeviceName,createdTime,updatedTime) {
        return this.$http.get(this.url(queueName) + '?' + this.mainservice.param(this.queryParams(status, offset, limit, dicomDeviceName,createdTime,updatedTime)))
            .map(res => j4care.redirectOnAuthResponse(res));
    };

    getCount(queueName, status, offset, limit, dicomDeviceName,createdTime,updatedTime) {
        return this.$http.get(this.url(queueName) + '/count' + '?' + this.mainservice.param(this.queryParams(status, offset, limit, dicomDeviceName,createdTime,updatedTime)))
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

    reschedule(queueName, msgId) {
        return this.$http.post(this.url3(queueName, msgId, 'reschedule'), {}, this.header)
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
        let header = new Headers({ 'Content-Type': 'application/json' });
        header.append('params', params);
        return header;
    }

    queryParams(status, offset, limit, dicomDeviceName,createdTime,updatedTime) {
        let params = {
            offset: offset,
            limit: limit,
            dicomDeviceName: dicomDeviceName,
            status: undefined,
            createdTime:createdTime,
            updatedTime:updatedTime
        };
        if (status != '*')
            params.status = status;
        return params;
    }
    getDevices(){
        return this.deviceService.getDevices()
    }


}
