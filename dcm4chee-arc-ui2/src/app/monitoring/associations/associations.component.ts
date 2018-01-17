///<reference path="../../../../node_modules/@angular/core/src/metadata/lifecycle_hooks.d.ts"/>
import { Component, OnDestroy } from '@angular/core';
import {Http} from '@angular/http';
import 'rxjs/add/operator/map';
import 'rxjs/add/operator/catch';
import * as FileSaver from 'file-saver';
import {SlimLoadingBarService} from 'ng2-slim-loading-bar';
import {MessagingComponent} from '../../widgets/messaging/messaging.component';
import {AppService} from '../../app.service';
import {WindowRefService} from "../../helpers/window-ref.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {HttpErrorHandler} from "../../helpers/http-error-handler";

@Component({
  selector: 'app-associations',
  templateUrl: './associations.component.html'
})
export class AssociationsComponent implements OnDestroy{
    updaterate: any = 3;
    // logoutUrl = myApp.logoutUrl();
    // status:any;
    stopLoop = true;
    message = '';
    others   = false;
    associationStatus;
    pause = false;
    // myValue = 10;
    constructor(public $http:J4careHttpService, public appservices: AppService, private cfpLoadingBar: SlimLoadingBarService, public messaging: MessagingComponent, public httpErrorHandler:HttpErrorHandler) {
        this.cfpLoadingBar.interval = 200;
    }


    modifyObject(obj){
        let local = [];
        let definedFields = [
            'serialNo',
            'connectTime',
            'initiated',
            'localAETitle',
            'remoteAETitle',
            'performedOps',
            'invokedOps'
        ];
        console.log('obj', obj);
        obj.forEach((j, l) => {
            for (let i in j){
                let m = j[i];
                local[l] = local[l] || {};
                if (definedFields.indexOf(i) > -1){
                    local[l][i] = m;
                }else{

                    local[l]['this.others'] = local[l]['this.others'] || {};
                    local[l]['this.othersFile'] = local[l]['this.othersFile'] || {};
                    if (Object.keys(local[l]['this.others']).length === 0){
                        local[l]['this.others'] = '<table><tr><td>' + i + '</td><td>' + m + '</td></tr>';
                        local[l]['this.othersFile'] = i + '=' + m;
                    }else{
                        local[l]['this.others'] += '<tr><td>' + i + '</td><td>' + m + '</td></tr>';
                        local[l]['this.othersFile'] += ' | ' + i + '=' + m;
                    }
                    this.others = true;
                }
            }
            if (local[l]['this.others'] && Object.keys(local[l]['this.others']).length > 0){
                local[l]['this.others'] += '<table>';
            }
        });
        return local;
    };
    timeCalculator(data){
        data.forEach((m, i) => {
            let date: any    = new Date(m.connectTime);
            let today: any   = new Date();
            let diff: number    = Math.round((today - date) / 1000);
            let sec: any     = '00';
            let min: any     = '00';
            let h: any       = '00';
            let milsec: any       = Math.round((((today - date) / 1000) - Math.floor((today - date) / 1000)) * 1000);

            if (diff < 60){
                sec = diff;
            }else{
                sec = Math.round(((diff / 60) - Math.floor(diff / 60)) * 60);

                if (Math.floor(diff / 60) < 60){
                    min = Math.round(Math.floor(diff / 60));
                    if (min < 10){
                        min = '0' + min;
                    }
                }else{
                    min = Math.round(((Math.floor(diff / 60) / 60) - Math.floor(Math.floor(diff / 60) / 60)) * 60);
                    h   = Math.round(Math.floor(Math.floor(diff / 60) / 60));
                }
            }
            if (sec < 10 && sec != '00'){
                sec = '0' + sec;
            }
            if (min < 10 && min != '00'){
                min = '0' + min;
            }
            if (h < 10 && h != '00'){
                h = '0' + h;
            }
            let dYear  = date.getFullYear();
            let dMonth = date.getMonth() + 1;
            if (dMonth < 10){
                dMonth = '0' + dMonth;
            }
            let dDate = date.getDate();
            if (dDate < 10){
                dDate = '0' + dDate;
            }
            let dHours = date.getHours();
            if (dHours < 10 && dHours != '00'){
                dHours = '0' + dHours;
            }
            let dMinutes = date.getMinutes();
            if (dMinutes < 10 && dMinutes != '00'){
                dMinutes = '0' + dMinutes;
            }
            let dSeconds = date.getSeconds();
            if (dSeconds < 10 && dSeconds != '00'){
                dSeconds = '0' + dSeconds;
            }
            data[i]['browserTime'] = dYear + '-' + dMonth + '-' + dDate + '  ' + dHours + ':' + dMinutes + ':' + dSeconds;
            data[i]['openSince']   = h + ':' + min + ':' + sec + '.' + milsec;
            data[i]['openSinceOrder']   = (today - date);
        });
        return data;
    }
    abort(serialnr){
        this.cfpLoadingBar.start();
        this.$http.delete('/dcm4chee-arc/monitor/associations/' + serialnr).subscribe(res => {
            this.cfpLoadingBar.complete();
            this.refresh();
        },(err)=>{
            this.cfpLoadingBar.complete();
            this.httpErrorHandler.handleError(err);
        });
    }

    propertyName = 'openSinceOrder';
    reverse = true;

    sortBy(pn) {
        console.log('sortby pn', pn);
        console.log('sortby propertyname', this.propertyName);
        this.reverse = (this.propertyName === pn) ? !this.reverse : false;
        this.propertyName = pn;
    };
    refresh(){

        this.cfpLoadingBar.start();
        // this.myValue = 10;
        // this.cfpLoadingBar.progress = this.cfpLoadingBar.progress + 10;
        this.$http.get('/dcm4chee-arc/monitor/associations')
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
            .subscribe(res => {
                res = this.getDummy();
                if (res && res[0] && res[0] != ''){
                    res = this.modifyObject(res);
                    res = this.timeCalculator(res);
                    this.associationStatus = res;
                }else{
                    this.associationStatus = null;
                }
                this.cfpLoadingBar.progress = this.cfpLoadingBar.progress + 10;
                this.cfpLoadingBar.complete();
                // },1000);
            }, (err) => {
                this.httpErrorHandler.handleError(err);
                this.cfpLoadingBar.complete();
            });
    }
    mauseEnter(){
        this.pause = true;
    }
    mauseLeave(){
        this.pause = false;
    }
    monitor(){
        // cfpLoadingBar.start();
        this.stopLoop = false;
        this.$http.get('/dcm4chee-arc/monitor/associations')
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
            .subscribe(res => {
                let data = res;
                if (data && data[0] && data[0] != ''){
                    data = this.modifyObject(data);
                    data = this.timeCalculator(data);
                    this.associationStatus = data;
                }else{
                    this.associationStatus = null;
                }
            });
        if (this.updaterate && typeof this.updaterate === 'string' && this.updaterate.indexOf(',') > -1){
            this.updaterate = this.updaterate.replace(',', '.');
        }
        let $that: any = this;
        let associationLoop = setInterval(() => {
            if ($that.stopLoop){
                clearInterval(associationLoop);
            }else{
                if(!this.pause){
                    $that.$http.get('/dcm4chee-arc/monitor/associations')
                        .map((res) => res.json())
                        .subscribe(
                            (res) => {
                                res = this.getDummy();
                                let data = res;
                                if (data && data[0] && data[0] != ''){
                                    data = $that.modifyObject(data);
                                    data = $that.timeCalculator(data);
                                    $that.associationStatus = data;
                                }else{
                                    $that.associationStatus = null;
                                }
                            },
                            (err) => {
                                this.httpErrorHandler.handleError(err);
                                //     // DeviceService.msg($scope, {
                                //     //     "title": "Error",
                                //     //     "text": "Connection error!",
                                //     //     "status": "error"
                                //     // });
                            }
                        );
                }
            }
        }, $that.updaterate * 1000);
    };
    downloadAssocImmage(){
        let csv = 'Local AE Title ⇆ Remote AE Title';
        csv += ',Invoked Ops.';
        csv += ',Performed Ops.';
        csv += ',Connection time (Server)';
        csv += ',Connection time (Browser)';
        csv += ',Connection open for (hh:mm:ss)';

        if (this.others){
            csv += ',Other attributes\n';
        }else{
            csv += '\n';
        }
        if (this.associationStatus){

            this.associationStatus.forEach((m, i) => {
                if (m.initiated){
                    csv += m.localAETitle + '→' + m.remoteAETitle;
                }else{
                    csv += m.localAETitle + '←' + m.remoteAETitle;
                }
                console.log('m', m);
                if (m.invokedOps){
                    csv += ',';
                    console.log('m.invokedOps', m.invokedOps);
                    for (let j in m.invokedOps){
                        let l = m.invokedOps[j];
                        csv = csv + '   ' + j + '- RQ/RSP : ' + l.RQ + '/' + l.RSP;
                    }
                }else{
                    csv += ',';
                }
                if (m.performedOps){
                    csv += ',';
                    for (let j in m.performedOps){
                        let l = m.performedOps[j];
                        csv = csv + '   ' + j + '- RQ/RSP : ' + l.RQ + '/' + l.RSP;
                    }
                }else{
                    csv += ',';
                }
                csv += ',' + m.connectTime;
                csv += ',' + m.browserTime;
                csv += ',' + m.openSince;
                if (m.othersFile){
                    csv += ',' + m.othersFile + '\n';
                }else{
                    csv += '\n';
                }
            });
        }
        let file = new File([csv], 'associacions.csv', {type: 'text/csv;charset=utf-8'});
        FileSaver.saveAs(file);
    };
    getDummy(){
        return [{"serialNo":1049399,"connectTime":"2018-01-10T20:55:11.062+02:00","initiated":true,"localAETitle":"EEVNATRT1","remoteAETitle":"MAS1TRT","performedOps":{},"invokedOps":{"C-MOVE":{"RQ":1,"RSP":0}}},{"serialNo":1049422,"connectTime":"2018-01-10T20:55:37.802+02:00","initiated":true,"localAETitle":"EEVNATRT","remoteAETitle":"EEVNATLN_FW","performedOps":{},"invokedOps":{"C-STORE":{"RQ":848,"RSP":838}}},{"serialNo":1049429,"connectTime":"2018-01-10T20:55:44.075+02:00","initiated":false,"localAETitle":"EEVNATRT","remoteAETitle":"MAS1TLN","performedOps":{"C-STORE":{"RQ":8,"RSP":7}},"invokedOps":{}},{"serialNo":1049463,"connectTime":"2018-01-10T20:56:05.265+02:00","initiated":true,"localAETitle":"EEVNATRT","remoteAETitle":"EEVNATLN_FW","performedOps":{},"invokedOps":{"C-STORE":{"RQ":914,"RSP":897}}},{"serialNo":1049469,"connectTime":"2018-01-10T20:56:09.073+02:00","initiated":false,"localAETitle":"EEVNATRT_FW","remoteAETitle":"EEVNATLN","performedOps":{"C-STORE":{"RQ":810,"RSP":809}},"invokedOps":{}},{"serialNo":1049473,"connectTime":"2018-01-10T20:56:13.350+02:00","initiated":true,"localAETitle":"EEVNATRT","remoteAETitle":"EEVNATLN_FW","performedOps":{},"invokedOps":{"C-STORE":{"RQ":851,"RSP":832}}},{"serialNo":1049475,"connectTime":"2018-01-10T20:56:18.171+02:00","initiated":false,"localAETitle":"EEVNATRT","remoteAETitle":"MAS1TLN","performedOps":{"C-STORE":{"RQ":448,"RSP":447}},"invokedOps":{}},{"serialNo":1049513,"connectTime":"2018-01-10T20:56:44.496+02:00","initiated":false,"localAETitle":"EEVNATRT_FW","remoteAETitle":"EEVNATLN","performedOps":{"C-STORE":{"RQ":573,"RSP":572}},"invokedOps":{}},{"serialNo":1049529,"connectTime":"2018-01-10T20:56:52.688+02:00","initiated":true,"localAETitle":"EEVNATRT","remoteAETitle":"EEVNATLN_FW","performedOps":{},"invokedOps":{"C-STORE":{"RQ":619,"RSP":596}}},{"serialNo":1049537,"connectTime":"2018-01-10T20:56:57.407+02:00","initiated":true,"localAETitle":"EEVNATRT1","remoteAETitle":"MAS1TRT","performedOps":{},"invokedOps":{"C-MOVE":{"RQ":1,"RSP":0}}},{"serialNo":1049538,"connectTime":"2018-01-10T20:56:58.653+02:00","initiated":false,"localAETitle":"EEVNATRT","remoteAETitle":"MAS1TLN","performedOps":{"C-STORE":{"RQ":344,"RSP":343}},"invokedOps":{}},{"serialNo":1049571,"connectTime":"2018-01-10T20:57:28.052+02:00","initiated":true,"localAETitle":"EEVNATRT1","remoteAETitle":"MAS1TRT","performedOps":{},"invokedOps":{"C-MOVE":{"RQ":1,"RSP":0}}},{"serialNo":1049574,"connectTime":"2018-01-10T20:57:30.320+02:00","initiated":false,"localAETitle":"EEVNATRT","remoteAETitle":"MAS1TLN","performedOps":{"C-STORE":{"RQ":53,"RSP":52}},"invokedOps":{}},{"serialNo":1049582,"connectTime":"2018-01-10T20:57:42.966+02:00","initiated":false,"localAETitle":"EEVNATRT","remoteAETitle":"MAS1TLN","performedOps":{"C-STORE":{"RQ":130,"RSP":129}},"invokedOps":{}},{"serialNo":1049601,"connectTime":"2018-01-10T20:58:09.686+02:00","initiated":true,"localAETitle":"EEVNATRT","remoteAETitle":"EEVNATLN_FW","performedOps":{},"invokedOps":{"C-STORE":{"RQ":132,"RSP":104}}},{"serialNo":1049603,"connectTime":"2018-01-10T20:58:10.609+02:00","initiated":false,"localAETitle":"EEVNATRT","remoteAETitle":"MAS1TLN","performedOps":{"C-STORE":{"RQ":4,"RSP":3}},"invokedOps":{}},{"serialNo":1049607,"connectTime":"2018-01-10T20:58:13.607+02:00","initiated":true,"localAETitle":"EEVNATRT1","remoteAETitle":"MAS1TRT","performedOps":{},"invokedOps":{"C-MOVE":{"RQ":1,"RSP":0}}},{"serialNo":1049609,"connectTime":"2018-01-10T20:58:14.831+02:00","initiated":false,"localAETitle":"EEVNATRT_FW","remoteAETitle":"EEVNATLN","performedOps":{"C-STORE":{"RQ":60,"RSP":59}},"invokedOps":{}},{"serialNo":1049617,"connectTime":"2018-01-10T20:58:23.153+02:00","initiated":false,"localAETitle":"EEVNATRT_FW","remoteAETitle":"EEVNATLN","performedOps":{"C-STORE":{"RQ":1,"RSP":0}},"invokedOps":{}},{"serialNo":1049618,"connectTime":"2018-01-10T20:58:23.347+02:00","initiated":true,"localAETitle":"EEVNATRT1","remoteAETitle":"MAS1TRT","performedOps":{},"invokedOps":{"C-MOVE":{"RQ":1,"RSP":0}}},{"serialNo":1049619,"connectTime":"2018-01-10T20:58:24.391+02:00","initiated":false,"localAETitle":"EEVNATRT_FW","remoteAETitle":"EEVNATLN","performedOps":{"C-STORE":{"RQ":1,"RSP":0}},"invokedOps":{}}];
    }
    ngOnDestroy(){
      this.stopLoop = true;
    }
}
