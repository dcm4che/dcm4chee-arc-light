import {Component, OnInit, ViewContainerRef} from '@angular/core';
import {Http} from "@angular/http";
import {SlimLoadingBarService} from "ng2-slim-loading-bar";
import * as _ from "lodash";
import {ConfirmComponent} from "../widgets/dialogs/confirm/confirm.component";
import {AppService} from "../app.service";
import {MdDialog, MdDialogConfig, MdDialogRef} from "@angular/material";

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

    constructor(public $http: Http, public cfpLoadingBar:SlimLoadingBarService, public mainservice:AppService,public viewContainerRef: ViewContainerRef ,public dialog: MdDialog, public config: MdDialogConfig) {
        this.getDevices();
        this.getEaes();

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
                $this.cfpLoadingBar.start();
                if(result){
                    $this.$http.delete("../devices/" + device.dicomDeviceName).subscribe((res)=>{
                        $this.mainservice.setMessage({
                            "title": "Info",
                            "text": "Device deleted successfully!",
                            "status": "info"
                        });
                        $this.getDevices();
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
    getEaes(){
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
}
