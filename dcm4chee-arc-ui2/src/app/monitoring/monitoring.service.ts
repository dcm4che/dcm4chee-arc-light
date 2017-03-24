import { Injectable } from '@angular/core';
import {AppService} from "../app.service";
import {Http} from "@angular/http";

@Injectable()
export class MonitoringService {

    constructor(public $http: Http,public mainservice:AppService) { }

    search(offset, limit) {
        return this.$http.get("../monitor/export", this.queryParams(offset, limit));
    };

    queryParams(offset, limit) {
        var params = {
            offset: offset,
            limit: limit
        }
        return params;
    }
}
