import { Injectable } from '@angular/core';
import {Http} from '@angular/http';
import {AppService} from '../../app.service';
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {DevicesService} from "../../devices/devices.service";
import {WindowRefService} from "../../helpers/window-ref.service";
import * as _ from 'lodash';

@Injectable()
export class ExportService {


    constructor(public $http:J4careHttpService, public mainservice: AppService, private deviceService:DevicesService) {
    }

    search(filters, offset) {
        return this.$http.get('../monitor/export' + '?' + this.mainservice.param(this.queryParams(filters, offset)));
    };

    getCount(filters) {
        return this.$http.get('../monitor/export' + '/count' + '?' +  this.mainservice.param(this.queryParams(filters, undefined)))
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;});
    };
    queryParams(filters, offset) {
/*        var params = {
            offset: (offset && offset != '') ? offset : 0,
            limit: limit,
            status:undefined
        }*/
        let clonedFilters = _.cloneDeep(filters);
        clonedFilters.offset = (offset && offset != '') ? offset : 0;
        if (clonedFilters.status && clonedFilters.status === '*'){
            delete clonedFilters.status;
        }
        if (clonedFilters.ExporterID && clonedFilters.ExporterID === '*'){
            delete clonedFilters.ExporterID;
        }
        if (clonedFilters.updatedTimeObject){
            delete clonedFilters.updatedTimeObject;
        }
        if (clonedFilters.createdTimeObject){
            delete clonedFilters.createdTimeObject;
        }
        return clonedFilters;
    }
    cancel(pk){
        return this.$http.post('../monitor/export/' + pk + '/cancel', {});
    }
    delete(pk){
        return this.$http.delete('../monitor/export/' + pk);
    }
    reschedule(pk, exporterID){
        return this.$http.post('../monitor/export/' + pk + '/reschedule/' + exporterID, {});
    }
    getDevices(){
        return this.deviceService.getDevices()
    }
}
