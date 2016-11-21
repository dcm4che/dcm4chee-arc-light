import { Component, OnInit } from '@angular/core';
import {Http} from "@angular/http";
// Import RxJs required methods
import 'rxjs/add/operator/map';
import 'rxjs/add/operator/catch';
import * as FileSaver from "file-saver";
import {AppService} from "../app.service";
import {SlimLoadingBarService} from "ng2-slim-loading-bar";

@Component({
    selector: 'app-control',
    templateUrl: './control.component.html',
    styleUrls: ['./control.component.css'],
    providers: [AppService]
})
export class ControlComponent implements OnInit {
    updaterate:any = 3;
    // logoutUrl = myApp.logoutUrl();
    status:any;
    stopLoop:boolean = true;
    message = '';
    others   = false;
    associationStatus;
    // myValue = 10;
    constructor(public $http: Http, public appservices:AppService, private cfpLoadingBar:SlimLoadingBarService ) {}
    ngOnInit() {
        this.fetchStatus();
        this.cfpLoadingBar.interval = 200;
    }

    // reverse = false;
    fetchStatus() {
        this.$http.get("/dcm4chee-arc/ctrl/status")
            .map(response => response.json())
            .subscribe( (res) => {
                this.status = res["status"];
                this.message = '';
            });
    };
    start(){
        this.$http.post("/dcm4chee-arc/ctrl/start",{}).subscribe((res) => {
            this.status = 'STARTED';
            this.message = '';
        });
    };
    stop() {
        console.log("stop");
        this.$http.post("/dcm4chee-arc/ctrl/stop",{}).subscribe((res) => {
            this.status = 'STOPPED';
            this.message = '';
        });
    };
    reload() {
        this.$http.post("/dcm4chee-arc/ctrl/reload",{}).subscribe((res) => {
            console.log("res",res);
                this.message = 'Reload successful';
        });
    };


    modifyObject(obj){
        var local = [];
        var definedFields = [
            "serialNo",
            "connectTime",
            "initiated",
            "localAETitle",
            "remoteAETitle",
            "performedOps",
            "invokedOps"
        ];
        console.log("obj",obj);
        obj.forEach((j, l) =>{
            for(let i in j){
                let m = j[i];
                local[l] = local[l] || {};
                if(definedFields.indexOf(i) > -1){
                    local[l][i] = m;
                }else{

                    local[l]["this.others"] = local[l]["this.others"] || {};
                    local[l]["this.othersFile"] = local[l]["this.othersFile"] || {};
                    if(Object.keys(local[l]["this.others"]).length === 0){
                        local[l]["this.others"] = "<table><tr><td>"+i+"</td><td>"+m+"</td></tr>";
                        local[l]["this.othersFile"] = i+"="+m;
                    }else{
                        local[l]["this.others"] += "<tr><td>"+i+"</td><td>"+m+"</td></tr>";
                        local[l]["this.othersFile"] += " | "+i+"="+m;
                    }
                    this.others = true;
                }
            }
            if(local[l]["this.others"] && Object.keys(local[l]["this.others"]).length > 0){
                local[l]["this.others"] += "<table>";
            }
        });
        return local;
    };
        timeCalculator(data){
            data.forEach((m, i) => {
            var date:any    = new Date(m.connectTime);
            var today:any   = new Date();
            var diff:number    = Math.round((today-date)/1000);
            var sec:any     = "00";
            var min:any     = "00";
            var h:any       = "00";
            var milsec:any       = Math.round((((today-date) / 1000)-Math.floor((today-date) / 1000))*1000);

            if(diff < 60){
                sec = diff;
            }else{
                sec = Math.round(((diff / 60)-Math.floor(diff / 60))*60);

                if(Math.floor(diff / 60) < 60){
                    min = Math.round(Math.floor(diff / 60));
                    if(min < 10){
                        min = "0"+min;
                    }
                }else{
                    min = Math.round(((Math.floor(diff / 60) / 60) - Math.floor(Math.floor(diff / 60) / 60))*60);
                    h   = Math.round(Math.floor(Math.floor(diff / 60) / 60));
                }
            }
            if(sec < 10 && sec != "00"){
                sec = "0"+sec;
            }
            if(min < 10 && min != "00"){
                min = "0"+min;
            }
            if(h < 10 && h != "00"){
                h = "0"+h;
            }
            var dYear  = date.getFullYear();
            var dMonth = date.getMonth()+1;
            if(dMonth < 10){
                dMonth = "0"+dMonth;
            }
            var dDate = date.getDate();
            if(dDate < 10){
                dDate = "0"+dDate;
            }
            var dHours = date.getHours();
            if(dHours < 10 && dHours != "00"){
                dHours = "0"+dHours;
            }
            var dMinutes = date.getMinutes();
            if(dMinutes < 10 && dMinutes != "00"){
                dMinutes = "0"+dMinutes;
            }
            var dSeconds = date.getSeconds();
            if(dSeconds < 10 && dSeconds != "00"){
                dSeconds = "0"+dSeconds;
            }
            data[i]["browserTime"] = dYear +"-"+ dMonth +"-"+ dDate +"  "+ dHours +":"+ dMinutes +":"+ dSeconds;
            data[i]["openSince"]   = h+":"+min+":"+sec+"."+milsec;
            data[i]["openSinceOrder"]   = (today-date);
        });
        return data;
    }
    abort(serialnr){
        this.cfpLoadingBar.start();
        this.$http.delete("/dcm4chee-arc/monitor/associations/"+serialnr).subscribe(res => {
            this.refresh();
        });
        // this.$http({
        //     method: 'DELETE',
        //     url: "../monitor/associations/"+serialnr
        // }).then(function successCallback(res) {
        //     this.refresh();
        //     // cfpLoadingBar.complete();
        // }, function errorCallback(response) {
        //     console.error("response=",response);
        //     // DeviceService.msg($scope, {
        //     //     "title": "Error",
        //     //     "text": "Error: "+response,
        //     //     "status": "error"
        //     // });
        //     // cfpLoadingBar.complete();
        // });
    }

    propertyName = 'openSinceOrder';
    reverse = true;

    sortBy(pn) {
        console.log("sortby pn",pn);
        console.log("sortby propertyname",this.propertyName);
        this.reverse = (this.propertyName === pn) ? !this.reverse : false;
        this.propertyName = pn;
    };
    refresh(){

        this.cfpLoadingBar.start();
        // this.myValue = 10;
        // this.cfpLoadingBar.progress = this.cfpLoadingBar.progress + 10;
        this.$http.get("/dcm4chee-arc/monitor/associations")
            .map(res => res.json())
            .subscribe(res => {
                let data = res;
                // this.cfpLoadingBar.progress = this.cfpLoadingBar.progress + 10;
                // let data:any = [{
                //                     "serialNo":6,
                //                     "connectTime":"2016-11-10T13:53:48.794+01:00",
                //                     "initiated":true,
                //                     "localAETitle":"1DCM4CHEE",
                //                     "remoteAETitle":"1STORESCP",
                //                     "performedOps":{
                //                         "C-STORE":{
                //                             "RQ":297,
                //                             "RSP":392
                //                         }
                //                     },
                //                     "invokedOps":{
                //                         "C-STORE":{
                //                             "RQ":397,
                //                             "RSP":792
                //                         }
                //                     }
                //                 },{
                //                     "serialNo":6,
                //                     "connectTime":"2013-11-10T13:53:48.794+01:00",
                //                     "initiated":true,
                //                     "localAETitle":"2DCM4CHEE",
                //                     "remoteAETitle":"2STORESCP",
                //                     "performedOps":{},
                //                     "invokedOps":{
                //                         "A-STORE":{
                //                             "RQ":297,
                //                             "RSP":492
                //                         }
                //                     }
                //                 },{
                //                     "serialNo":6,
                //                     "connectTime":"2014-11-10T13:53:48.794+01:00",
                //                     "initiated":false,
                //                     "localAETitle":"3DCM4CHEE",
                //                     "remoteAETitle":"3STORESCP",
                //                     "performedOps":{},
                //                     "invokedOps":{
                //                         "C-STORE":{
                //                             "RQ":797,
                //                             "RSP":492
                //                         }
                //                     }
                //                 }];
                if(data && data[0] && data[0] != ""){
                    data = this.modifyObject(data);
                    data = this.timeCalculator(data);
                    this.associationStatus = data;
                }else{
                    this.associationStatus = null;
                }
                this.cfpLoadingBar.progress = this.cfpLoadingBar.progress + 10;
                // console.log("progres",this.cfpLoadingBar.progress);

                // let increas = setInterval(() => {
                //     if(this.myValue === 20){
                //         clearInterval(increas);
                //     }else{
                //         this.myValue++;
                //     }
                // },50);
                // setTimeout(()=>{
                //     // this.cfpLoadingBar.progress = this.cfpLoadingBar.progress + 10;
                //     let increas = setInterval(() => {
                //         if(this.myValue === 30){
                //             clearInterval(increas);
                //         }else{
                //             this.myValue++;
                //         }
                //     },50);
                        this.cfpLoadingBar.complete();
                // },1000);
            });
        // this.$http({
        //     method: 'GET',
        //     url: "../monitor/associations"
        // }).then(function successCallback(res) {
        //     if(res.data && res.data[0] && res.data[0] != ""){
        //         res.data = this.modifyObject(res.data);
        //         res.data = this.timeCalculator(res.data);
        //         this.associationStatus = res.data;
        //     }else{
        //         this.associationStatus = null;
        //     }
        //     // cfpLoadingBar.complete();
        // }, function errorCallback(response) {
        //     console.error("response=",response);
        //     // DeviceService.msg($scope, {
        //     //     "title": "Error",
        //     //     "text": "Error: "+response,
        //     //     "status": "error"
        //     // });
        //     // cfpLoadingBar.complete();
        // });
    }

    monitor(){
        // cfpLoadingBar.start();
        this.stopLoop = false;
        this.$http.get("/dcm4chee-arc/monitor/associations")
            .map(res => res.json())
            .subscribe(res => {
                let data = res;
                if(data && data[0] && data[0] != ""){
                    data = this.modifyObject(data);
                    data = this.timeCalculator(data);
                    this.associationStatus = data;
                }else{
                    this.associationStatus = null;
                }
            });
        if(this.updaterate && typeof this.updaterate === 'string' && this.updaterate.indexOf(",") > -1){
            this.updaterate = this.updaterate.replace(",", ".");
        }
        let $that:any = this;
        var associationLoop = setInterval(function () {
            if ($that.stopLoop){
                clearInterval(associationLoop);
            }else{

                    $that.$http.get("/dcm4chee-arc/monitor/associations")
                        .map((res) => res.json())
                        .subscribe(
                            (res) => {
                                let data = res;
                                if(data && data[0] && data[0] != ""){
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
        var csv = "Local AE Title ⇆ Remote AE Title";
        csv += ",Invoked Ops.";
        csv += ",Performed Ops.";
        csv += ",Connection time (Server)";
        csv += ",Connection time (Browser)";
        csv += ",Connection open for (hh:mm:ss)";

        if(this.others){
            csv += ",Other attributes\n";
        }else{
            csv += "\n";
        }
        if(this.associationStatus){

            this.associationStatus.forEach((m, i) =>{
                if(m.initiated){
                    csv += m.localAETitle +"→"+ m.remoteAETitle;
                }else{
                    csv += m.localAETitle +"←"+ m.remoteAETitle;
                }
                console.log("m",m);
                if(m.invokedOps){
                    csv += ","
                    console.log("m.invokedOps",m.invokedOps);
                    for(let j in m.invokedOps){
                        let l = m.invokedOps[j];
                        csv = csv + "   "+ j + "- RQ/RSP : " + l.RQ + "/" + l.RSP;
                    }
                }else{
                    csv += ",";
                }
                if(m.performedOps){
                    csv += ","
                    for(let j in m.performedOps){
                        let l = m.performedOps[j];
                        csv = csv + "   "+ j + "- RQ/RSP : " + l.RQ + "/" + l.RSP;
                    }
                }else{
                    csv += ","
                }
                csv += ","+m.connectTime;
                csv += ","+m.browserTime;
                csv += ","+m.openSince;
                if(m.othersFile){
                    csv += ","+m.othersFile+"\n";
                }else{
                    csv += "\n";
                }
            });
        }
        var file = new File([csv], "associacions.csv", {type: "text/csv;charset=utf-8"});
        FileSaver.saveAs(file);
    };

}
