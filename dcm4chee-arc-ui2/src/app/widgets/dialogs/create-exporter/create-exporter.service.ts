import { Injectable } from '@angular/core';
import {Http} from '@angular/http';
import {WindowRefService} from "../../../helpers/window-ref.service";
import {J4careHttpService} from "../../../helpers/j4care-http.service";

@Injectable()
export class CreateExporterService {

    constructor(private $http:J4careHttpService) { }

    getDevice(devicename){
        return this.$http.get('../devices/' + devicename).map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;});
    }
    getQueue(){
        return this.$http.get('../queue').map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;});
    }
}
