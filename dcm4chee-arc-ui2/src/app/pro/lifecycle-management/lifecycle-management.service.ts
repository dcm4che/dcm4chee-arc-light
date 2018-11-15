import { Injectable } from '@angular/core';
import {AppService} from "../../app.service";
import {Http} from "@angular/http";
import {WindowRefService} from "../../helpers/window-ref.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {j4care} from "../../helpers/j4care.service";
import {PermissionService} from "../../helpers/permissions/permission.service";

@Injectable()
export class LifecycleManagementService {

    constructor(
        private $http:J4careHttpService,
        private mainservice:AppService,
        private permissionService:PermissionService
    ) { }
    _config(params) {
        return '?' + jQuery.param(params);
    };
    setExpiredDate(aet,studyUID, expiredDate){
        return this.$http.put(`../aets/${aet}/rs/studies/${studyUID}/expire/${expiredDate}`,{}).map(res => j4care.redirectOnAuthResponse(res))
    }

    saveArchivDevice(deviceObject){
        return this.$http.put(`../devices/${deviceObject.dicomDeviceName}`, deviceObject).map(res => j4care.redirectOnAuthResponse(res))
    }
    getStudies(aet, params, expired){
        if(expired){
            params['expired'] = params['expired'] || true;
        }else{
            params['expired'] = params['expired'] || false;
            params['expired'] = false;
        }
        return  this.$http.get(`../aets/${aet}/rs/studies${this._config(params)}`).map(res => j4care.redirectOnAuthResponse(res))
    }
    getArchiveDevice(deviceName) {
        return this.$http.get(`../devices/${deviceName}`)
            .map(res => j4care.redirectOnAuthResponse(res))
    }
    getAets(){
        return this.$http.get('../aets')
            .map(res => j4care.redirectOnAuthResponse(res))
            .map(aet=> this.permissionService.filterAetDependingOnUiConfig(aet,'internal'))
    };
    getLifecyclemanagementSchema(){
        return this.$http.get('./assets/schema/studyRetentionPolicy.schema.json')
            .map(res => j4care.redirectOnAuthResponse(res))
    }
}
