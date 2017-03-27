import { Injectable } from '@angular/core';
import {AppService} from "../app.service";
import {Http} from "@angular/http";

@Injectable()
export class MonitoringService {

    constructor(public $http: Http,public mainservice:AppService) { }

    search(filters, offset) {

        return this.$http.get("../monitor/export" + '?' + this.mainservice.param(this.queryParams(filters, offset)));
    };
    queryParams(filters, offset) {
/*        var params = {
            offset: (offset && offset != '') ? offset : 0,
            limit: limit,
            status:undefined
        }*/
        filters.offset = (offset && offset != '') ? offset : 0;
        if (filters.status && filters.status === "*"){
            delete filters.status;
        }
        return filters;
    }
}
