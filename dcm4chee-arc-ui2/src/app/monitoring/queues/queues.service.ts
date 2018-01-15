import { Injectable } from '@angular/core';
import {Http, Headers} from '@angular/http';
import {DatePipe} from '@angular/common';
import {AppService} from '../../app.service';
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {DevicesService} from "../../devices/devices.service";
import {WindowRefService} from "../../helpers/window-ref.service";

@Injectable()
export class QueuesService {

    header = new Headers({ 'Content-Type': 'application/json' });
    constructor(public $http:J4careHttpService, public mainservice: AppService, private deviceService:DevicesService) { }

    search(queueName, status, offset, limit, dicomDeviceName,createdTime,updatedTime) {
        return this.$http.get(this.url(queueName) + '?' + this.mainservice.param(this.queryParams(status, offset, limit, dicomDeviceName,createdTime,updatedTime)))
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;});
    };

    getCount(queueName, status, offset, limit, dicomDeviceName,createdTime,updatedTime) {
        return this.$http.get(this.url(queueName) + '/count' + '?' + this.mainservice.param(this.queryParams(status, offset, limit, dicomDeviceName,createdTime,updatedTime)))
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;});
    };

    cancel(queueName, msgId) {
        return this.$http.post(this.url3(queueName, msgId, 'cancel'), {}, this.header);
    };

    cancelAll(filter,queueName){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`../queue/${queueName}/cancel${urlParam}`, {}, this.header)
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;});
    }

    reschedule(queueName, msgId) {
        return this.$http.post(this.url3(queueName, msgId, 'reschedule'), {}, this.header)
    };

    rescheduleAll(filter,queueName){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`../queue/${queueName}/reschedule${urlParam}`, {}, this.header)
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;});
    }

    delete(queueName, msgId) {
        return this.$http.delete(this.url2(queueName, msgId));
    };

    deleteAll(filter,queueName){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.delete(`../queue/${queueName}${urlParam}`, this.header)
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;});
    }

/*    flush(queueName, status, before, device) {
        let urlParam = this.mainservice.param(this.flushParams(status, before, device));
        return this.$http.delete(this.url(queueName) + '?' + urlParam);
    };*/

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

/*    flushParams(status, before, device) {
        let params = {
            status: undefined,
            updatedBefore: undefined,
            dicomDeviceName:device
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
    }*/
    getDevices(){
        return this.deviceService.getDevices()
    }


}
