import {Component, OnInit, ViewContainerRef} from '@angular/core';
import {Http, Headers} from "@angular/http";
import {SlimLoadingBarService} from "ng2-slim-loading-bar";
import * as _ from "lodash";
import {ConfirmComponent} from "../widgets/dialogs/confirm/confirm.component";
import {AppService} from "../app.service";
import {MdDialog, MdDialogConfig, MdDialogRef} from "@angular/material";
import {DevicesService} from "./devices.service";

@Component({
  selector: 'app-devices',
  templateUrl: './devices.component.html',
  styleUrls: ['./devices.component.css']
})
export class DevicesComponent {
    debugpre = false;
    _ = _;
    devices;
    aes;
    aets;
    advancedConfig = false;
    showDeviceList=true;
    showAetList=false;
    devicefilter = "";
    aesfilter = "";
    filter = {
        dicomAssociationInitiator:undefined,
        dicomDeviceName:undefined,
        dicomAETitle:undefined,
        dicomDescription:undefined,
        dicomApplicationCluster:undefined,
        dicomSoftwareVersion:undefined,
        dicomStationName:undefined,
        dicomPrimaryDeviceType:undefined,
        dicomInstitutionName:undefined,
        dicomInstitutionDepartmentName:undefined,
        dicomInstalled:undefined
    };
    dialogRef: MdDialogRef<any>;

    constructor(public $http: Http, public cfpLoadingBar:SlimLoadingBarService, public mainservice:AppService,public viewContainerRef: ViewContainerRef ,public dialog: MdDialog, public config: MdDialogConfig,public service:DevicesService) {
        this.getDevices();
        this.getAes();
        this.getAets();

  }
    getKeys(obj){
        console.log("getkeys obj",obj);
        if(obj){
            if(_.isArray(obj)){
                return obj;
            }else{
                return Object.keys(obj);
            }
        }else{
            return [];
        }
    }
    searchDevices(){
        this.cfpLoadingBar.start();
        let $this = this;
        let urlParam = this.mainservice.param(this.filter);
        // urlParam = urlParam.join("&");
        if(urlParam){
            urlParam = "?"+urlParam;
        }
        this.$http.get(
            '../devices'+urlParam
        ).map(res => res.json())
        .subscribe((response) => {
            $this.devices = response;
            $this.cfpLoadingBar.complete();
        }, function errorCallback(response) {
/*            $log.error("Error loading device names", response);
            vex.dialog.alert("Error loading device names, please reload the page and try again!");*/
        });
    };
    searchAes(){
        this.cfpLoadingBar.start();
        let urlParam = this.mainservice.param(this.filter);
        if(urlParam){
            urlParam = "?"+urlParam;
        }
        let $this = this;
        this.$http.get('../aes'+urlParam)
            .map(res => res.json())
            .subscribe((response) => {
                $this.aes = response;
                $this.cfpLoadingBar.complete();
            }, (err) => {
                // vex.dialog.alert("Error loading aes, please reload the page and try again!");
                $this.cfpLoadingBar.complete();
            });
    };
    clearForm(){
        let $this = this;
        _.forEach($this.filter,function(m,i){
            $this.filter[i] = "";
        });
        if(this.showDeviceList === true){
            this.searchDevices();
        }else{
            this.searchAes();
        }
    };
    scrollToDialog(){
        let counter = 0;
        let i = setInterval(function(){
            if(($(".md-overlay-pane").length > 0)) {
                clearInterval(i);
                $('html, body').animate({
                    scrollTop: ($(".md-overlay-pane").offset().top)
                }, 200);
            }
            if(counter > 200){
                clearInterval(i);
            }else{
                counter++;
            }
        }, 50);
    }
    confirm(confirmparameters){
        this.scrollToDialog();
        this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(ConfirmComponent, this.config);
        this.dialogRef.componentInstance.parameters = confirmparameters;
        return this.dialogRef.afterClosed();
    };
    deleteDevice(device) {
        if (device && device.dicomDeviceName) {
            let $this = this;
            this.confirm({
                content:'Are you sure you want to delete the device ' + device.dicomDeviceName + '?'
            }).subscribe(result => {
                if(result){
                    $this.cfpLoadingBar.start();
                    $this.$http.delete("../devices/" + device.dicomDeviceName).subscribe((res)=>{
                        $this.mainservice.setMessage({
                            "title": "Info",
                            "text": "Device deleted successfully!",
                            "status": "info"
                        });
                        $this.getDevices();
                        $this.getAes();
                        $this.cfpLoadingBar.complete();
                    },(err)=>{
                        $this.mainservice.setMessage({
                            "title": "Error "+err.status,
                            "text": err.statusText,
                            "status": "error"
                        });
                        $this.cfpLoadingBar.complete();
                    });
                }
            });
        }
    };
    cloneDevice(devicename){
        let headers = new Headers({ 'Content-Type': 'application/json' });
        let deviceNameList = this.devices.map(res =>{
            return res.dicomDeviceName;
        });
        console.log("deviceNameList",deviceNameList);
        let parameters: any = {
            content: 'Set the name for the new device to clone '+devicename.dicomDeviceName,
            input: {
                name:'newdevice',
                type:'text'
            },
            result: {input:''},
            saveButton: "CLONE"
        };
        console.log("parameters", parameters);
        let $this = this;
        this.confirm(parameters).subscribe(result => {
            if(result){
                $this.cfpLoadingBar.start();
                console.log("result",result);
                console.log("param",parameters);
                console.log("devicename",devicename.dicomDeviceName);
                console.log("indexof",_.indexOf(deviceNameList,parameters.result.input));
                if(_.indexOf(deviceNameList,parameters.result.input) > -1){
                    $this.mainservice.setMessage({
                        "title": "Error",
                        "text": "This name exist, pleas chose an other one!",
                        "status": "error"
                    });
                    $this.cfpLoadingBar.complete();
                }else{
                    $this.$http.get(
                        '../devices/'+devicename.dicomDeviceName
                    )
                    .map(res => res.json())
                    .subscribe(
                        (device) => {
                            console.log("response",device);
                            $this.service.changeAetOnClone(device);
                            console.log("device afterchange",device);
                            device.dicomDeviceName = parameters.result.input;
                            $this.$http.post("../devices/" + parameters.result.input, device, headers)
                                .subscribe(res => {
                                        console.log("res succes",res);
                                        $this.cfpLoadingBar.complete();
                                        $this.mainservice.setMessage({
                                            "title": "Info",
                                            "text": "Device cloned successfully!",
                                            "status": "info"
                                        });
                                        $this.getDevices();
                                        $this.getAes();
                                    },
                                    err =>{
                                        console.log("error");
                                        $this.cfpLoadingBar.complete();
                                        $this.mainservice.setMessage({
                                            "title": "Error " + err.status,
                                            "text": err.statusText,
                                            "status": "error"
                                        });
                                    });
                        },
                        (err) => {
                            console.log("err",err);
                            $this.mainservice.setMessage({
                                "title": "Error " + err.status,
                                "text": err.statusText,
                                "status": "error"
                            });
                            $this.cfpLoadingBar.complete();
                        }
                    );
                }
            }
        });
    };
    echoAe(ae){
        let headers = new Headers({ 'Content-Type': 'application/json' });
        let select:any = [];
        _.forEach(this.aets, (m,i)=>{
            select.push({
                title:m.title,
                value:m.title,
                label:m.title
            });
        });
        let parameters: any = {
            content: 'Select one AET:',
            select: select,
            result: {select:this.aets[0].title},
            bodytext:'Remote AET: <b>'+ae+'</b>',
            saveButton: "ECHO",
            cssClass:'echodialog'
        };
        console.log("parameters", parameters);
        let $this = this;
        this.confirm(parameters).subscribe(result => {
            if (result){
                console.log("result", result);
                console.log("result", parameters.result);
                $this.$http.post(
                    '../aets/' + parameters.result.select + '/echo/' + ae,
                    {}
                ).map(res => res.json())
                    .subscribe((response) => {
                        console.log("response", response);
                        if (_.hasIn(response, "errorMessage") && response.errorMessage != '') {
                            $this.mainservice.setMessage({
                                "title": "Error ",
                                "text": response.errorMessage,
                                "status": "error"
                            });
                        } else {

                            $this.mainservice.setMessage({
                                "title": "Info",
                                "text": "Echo successfully accomplished!<br>- Connection time: " +
                                response.connectionTime +
                                " ms<br/>- Echo time: " +
                                response.echoTime +
                                " ms<br/>- Release time: " +
                                response.releaseTime + " ms",
                                "status": "info"
                            });
                        }
                    }, err=> {
                        console.log("error", err);
                        $this.mainservice.setMessage({
                            "title": "Error " + err.status,
                            "text": err.statusText,
                            "status": "error"
                        });
                    });
            }
        });
    };
    deleteAE(device, ae){
        let parameters: any = {
            content: 'Are you sure you want to unregister and delete from device the AE: <b>'+ae+'</b>?',
            input: {
                name:'deletedevice',
                type:'checkbox',
                checkboxtext:'Delete also the device <b>' + device + '</b>'
            },
            result:{input:false},
            saveButton: "DELETE",
            cssClass:'deleteaet'
        };
        console.log("parameters", parameters);
        let $this = this;
        this.confirm(parameters).subscribe(result => {
            if(result){
                console.log("in ok",result);
                console.log("parameters",parameters);
                $this.$http.delete(
                    "../unique/aets/"+ae
                )
                    .subscribe((response) => {

                    $this.mainservice.setMessage({
                        "title": "Info",
                        "text": "Aet unregistered successfully!",
                        "status": "info"
                    });
                    clearAe();
                },(err)=>{
                        console.log("in errror");

                        if(err.status === 404){
                            $this.mainservice.setMessage({
                                "title": "Info",
                                "text": "Aet not regiestered!",
                                "status": "info"
                            });
                            clearAe();
                        }else{
                            $this.mainservice.setMessage({
                                "title": "Error "+err.status,
                                "text": "Error:"+err.statusText,
                                "status": "error"
                            });
                        }
                }
                )
                let clearAe = function () {
                    console.log("in clearae",result);
                    if(result.input === true){
                        $this.$http.delete('../devices/'+device).subscribe((res)=>{
                                console.log("res",res);
                                $this.mainservice.setMessage({
                                    "title": "Info",
                                    "text": "Device deleted successfully!",
                                    "status": "info"
                                });
                                $this.getDevices();
                                $this.$http.post("../ctrl/reload",{}).subscribe((res) => {
                                    $this.mainservice.setMessage({
                                        "title": "Info",
                                        "text": "Archive reloaded successfully!",
                                        "status": "info"
                                    });
                                    $this.getAes();
                                },(error) => {
                                    console.warn("Reloading ther Archive faild");
                                })

                            },
                            (err) => {
                                $this.mainservice.setMessage({
                                    "title": "Error",
                                    "text": "Error deleting the device!",
                                    "status": "error"
                                });
                            });
                    }else{
                        $this.$http.get('../devices/'+device)
                            .map(res => res.json())
                            .subscribe(
                                (res) => {
                                    console.log("res",res);
                                    let deviceObject = res;
                                    //Remove ae from device and save it back
                                    _.forEach(deviceObject.dicomNetworkAE ,(m, i) => {
                                        console.log("m",m);
                                        console.log("i",i);
                                        if(m && m.dicomAETitle === ae){
                                            deviceObject.dicomNetworkAE.splice(i, 1);
                                        }
                                    });
                                    console.log("equal",_.isEqual(res,deviceObject));
                                    console.log("deviceObj",deviceObject);
                                    $this.$http.put("../devices/" + device, deviceObject)
                                        .subscribe((resdev) => {
                                                console.log("resdev",resdev);
                                                $this.mainservice.setMessage({
                                                    "title": "Info",
                                                    "text": "Ae removed from device successfully!",
                                                    "status": "info"
                                                });
                                                $this.$http.post("../ctrl/reload",{}).subscribe((res) => {
                                                    $this.mainservice.setMessage({
                                                        "title": "Info",
                                                        "text": "Archive reloaded successfully!",
                                                        "status": "info"
                                                    });
                                                    $this.getAes();
                                                });
                                            },
                                            (err) => {
                                                console.log("err",err);
                                                $this.mainservice.setMessage({
                                                    "title": "error",
                                                    "text": "Error, the AE was not removed from device!",
                                                    "status": "error"
                                                });
                                            });
                                },
                                (err) => {
                                    $this.mainservice.setMessage({
                                        "title": "error",
                                        "text": "Error getting device "+device,
                                        "status": "error"
                                    });
                                }
                            );
                    }
                }
            }
        });
    }
    getDevices(){
        let $this = this;
        this.$http.get(
            '../devices'
        ).map(res => res.json())
            .subscribe((response) => {
                $this.devices = response;
            }, (err) => {
                // vex.dialog.alert("Error loading device names, please reload the page and try again!");
            });
    };
    getAes(){
        let $this = this;
        this.$http.get(
            '../aes'
        )
        .map(res=>res.json())
        .subscribe((response) => {
                $this.aes = response;
            }, (response) => {
                // vex.dialog.alert("Error loading aes, please reload the page and try again!");
        });
    }
    getAets(){
        let $this = this;
        this.$http.get(
            '../aets'
        ).map(res => res.json())
        .subscribe((response) => {
            $this.aets = response;
        },(err) =>{
            console.log("error getting aets",err);
        });
    }
}
