import { Component } from '@angular/core';
import {Http} from '@angular/http';
import 'rxjs/add/operator/map';
import 'rxjs/add/operator/catch';
import * as FileSaver from 'file-saver';
import {SlimLoadingBarService} from 'ng2-slim-loading-bar';
import {MessagingComponent} from '../../widgets/messaging/messaging.component';
import {AppService} from '../../app.service';
import {WindowRefService} from "../../helpers/window-ref.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";

@Component({
  selector: 'app-associations',
  templateUrl: './associations.component.html'
})
export class AssociationsComponent{
    updaterate: any = 3;
    // logoutUrl = myApp.logoutUrl();
    // status:any;
    stopLoop = true;
    message = '';
    others   = false;
    associationStatus;
    // myValue = 10;
    constructor(public $http:J4careHttpService, public appservices: AppService, private cfpLoadingBar: SlimLoadingBarService, public messaging: MessagingComponent) {
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
            this.refresh();
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
                this.cfpLoadingBar.complete();
            });
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
        let associationLoop = setInterval(function () {
            if ($that.stopLoop){
                clearInterval(associationLoop);
            }else{

                $that.$http.get('/dcm4chee-arc/monitor/associations')
                    .map((res) => res.json())
                    .subscribe(
                        (res) => {
                            let data = res;
                            if (data && data[0] && data[0] != ''){
                                data = $that.modifyObject(data);
                                data = $that.timeCalculator(data);
                                $that.associationStatus = data;
                            }else{
                                $that.associationStatus = null;
                            }
                        },
                        (res) => {
                            //     // DeviceService.msg($scope, {
                            //     //     "title": "Error",
                            //     //     "text": "Connection error!",
                            //     //     "status": "error"
                            //     // });
                        }
                    );
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

}
