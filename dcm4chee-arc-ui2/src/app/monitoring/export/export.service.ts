import { Injectable } from '@angular/core';
import {Http, Headers} from '@angular/http';
import {AppService} from '../../app.service';
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {DevicesService} from "../../devices/devices.service";
import {WindowRefService} from "../../helpers/window-ref.service";
import * as _ from 'lodash';
import {j4care} from "../../helpers/j4care.service";

@Injectable()
export class ExportService {

    header = new Headers({ 'Content-Type': 'application/json' });
    constructor(public $http:J4careHttpService, public mainservice: AppService, private deviceService:DevicesService) {
    }

    search(filters, offset) {
        return this.$http.get('../monitor/export' + '?' + this.mainservice.param(this.queryParams(filters, offset)));
    };

    getCount(filters) {
        return this.$http.get('../monitor/export' + '/count' + '?' +  this.mainservice.param(this.paramWithoutLimit(filters)))
            .map(res => j4care.redirectOnAuthResponse(res));
    };
    paramWithoutLimit(filters){
        let clonedFilters = this.queryParams(filters,undefined);
        delete clonedFilters.limit;
        return clonedFilters;
    }
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
    cancelAll(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.post(`../monitor/export/cancel${urlParam}`, {}, this.header)
            .map(res => j4care.redirectOnAuthResponse(res));
    }
    delete(pk){
        return this.$http.delete('../monitor/export/' + pk);
    }
    deleteAll(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        return this.$http.delete(`../monitor/export${urlParam}`, this.header)
            .map(res => j4care.redirectOnAuthResponse(res));
    }
    reschedule(pk, exporterID){
        return this.$http.post('../monitor/export/' + pk + '/reschedule/' + exporterID, {});
    }

    rescheduleAll(filter, exporterID){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        let exporter = exporterID? `/${exporterID}`:'';
        return this.$http.post(`../monitor/export/reschedule${exporter}${urlParam}`, {}, this.header)
            .map(res => j4care.redirectOnAuthResponse(res));
    }
    downloadCsv(filter){
        let urlParam = this.mainservice.param(filter);
        urlParam = urlParam?`?${urlParam}`:'';
        // let header = new Headers({ 'Content-Type': 'text/csv' });
        let header = new Headers({ 'Accept': 'text/csv' });
        return this.$http.get(`/dcm4chee-arc/monitor/export${urlParam}`, header)
    }
    getDevices(){
        return this.deviceService.getDevices()
    }
}
