import { Injectable } from '@angular/core';
import {Http} from '@angular/http';
import {AppService} from '../../app.service';
import {J4careHttpService} from "../../helpers/j4care-http.service";

@Injectable()
export class ExportService {


    constructor(public $http:J4careHttpService, public mainservice: AppService) {
    }

    search(filters, offset) {
        return this.$http.get('../monitor/export' + '?' + this.mainservice.param(this.queryParams(filters, offset)));
    };
    queryParams(filters, offset) {
/*        var params = {
            offset: (offset && offset != '') ? offset : 0,
            limit: limit,
            status:undefined
        }*/
        filters.offset = (offset && offset != '') ? offset : 0;
        if (filters.status && filters.status === '*'){
            delete filters.status;
        }
        if (filters.ExporterID && filters.ExporterID === '*'){
            delete filters.ExporterID;
        }
        return filters;
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

}
