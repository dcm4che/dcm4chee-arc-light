import { Injectable } from '@angular/core';
import {Http, Headers} from '@angular/http';
import {DatePipe} from '@angular/common';
import {AppService} from '../../app.service';
import {J4careHttpService} from "../../helpers/j4care-http.service";

@Injectable()
export class QueuesService {

    header = new Headers({ 'Content-Type': 'application/json' });
    constructor(public $http:J4careHttpService, public mainservice: AppService) { }

    search(queueName, status, offset, limit, dicomDeviceName) {
        return this.$http.get(this.url(queueName) + '?' + this.mainservice.param(this.queryParams(status, offset, limit, dicomDeviceName)));
    };

    cancel(queueName, msgId) {
        return this.$http.post(this.url3(queueName, msgId, 'cancel'), {}, this.header);
    };

    reschedule(queueName, msgId) {
        return this.$http.post(this.url3(queueName, msgId, 'reschedule'), {}, this.header);
    };

    delete(queueName, msgId) {
        return this.$http.delete(this.url2(queueName, msgId));
    };

    flush(queueName, status, before) {
        let urlParam = this.mainservice.param(this.flushParams(status, before));
        return this.$http.delete(this.url(queueName) + '?' + urlParam);
    };

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
        // return {
        //     headers: {Accept: 'application/json'},
        //     params: params
        // }
    }

    queryParams(status, offset, limit, dicomDeviceName) {
        let params = {
            offset: offset,
            limit: limit,
            dicomDeviceName: dicomDeviceName,
            status: undefined
        };
        if (status != '*')
            params.status = status;
        return params;
    }

    flushParams(status, before) {
        let params = {
            status: undefined,
            updatedBefore: undefined
        };
        if (status != '*')
            params.status = status;
        if (before != null){
            let datePipeEn = new DatePipe('us-US');
            params.updatedBefore = datePipeEn.transform(before, 'yyyy-MM-dd');
            console.log('params,updatedBefore', params.updatedBefore);
            // params.updatedBefore = $filter('date')(before, 'yyyy-MM-dd'); //TODO
        }
        console.log('params', params);
        return params;
    }


}
