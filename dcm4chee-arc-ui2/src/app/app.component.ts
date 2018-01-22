import {Component, OnInit, ViewContainerRef} from '@angular/core';
import {MdDialog, MdDialogRef, MdDialogConfig} from '@angular/material';
import {MessagingComponent} from './widgets/messaging/messaging.component';
import {AppService} from './app.service';
import {ViewChild} from '@angular/core';
import 'rxjs/add/operator/catch';
import {Http} from '@angular/http';
import {ProductLabellingComponent} from './widgets/dialogs/product-labelling/product-labelling.component';
import {HostListener} from '@angular/core';
import {WindowRefService} from "./helpers/window-ref.service";
import * as _ from 'lodash';
import {J4careHttpService} from "./helpers/j4care-http.service";
import {j4care} from "./helpers/j4care.service";
// import {DCM4CHE} from "./constants/dcm4-che";
// declare var $:JQueryStatic;
// import * as vex from "vex-js";
// declare var vex: any;
// const vex = require("vex-js");
declare var DCM4CHE: any;
@Component({
  selector: 'app-root',
  templateUrl: './app.component.html'
})
export class AppComponent implements OnInit {
    progressValue = 30;
    //Detect witch header should be shown.
    user: any = {};
    dialogRef: MdDialogRef<any>;
    showUserMenu = false;
    url = '/auth';
    logoutUrl = '';
    isRole: any;
    archive;
    realm;
    authServerUrl;
    showMenu = false;
    showScrollButton = false;
    currentServerTime;
    currentClockTime;
    clockInterval;
    j4care = j4care;
    @ViewChild(MessagingComponent) msg;
    // vex["defaultOptions"]["className"] = 'vex-theme-os';
    constructor(
        public viewContainerRef: ViewContainerRef,
        public dialog: MdDialog,
        public config: MdDialogConfig,
        public mainservice: AppService,
        public $http:J4careHttpService
    ){}

    ngOnInit(){
        Date.prototype.toDateString = function() {
            return `${this.getFullYear()}${j4care.getSingleDateTimeValueFromInt(this.getMonth()+1)}${j4care.getSingleDateTimeValueFromInt(this.getDate())}${j4care.getSingleDateTimeValueFromInt(this.getHours())}${j4care.getSingleDateTimeValueFromInt(this.getMinutes())}${j4care.getSingleDateTimeValueFromInt(this.getSeconds())}`;
        };
        let $this = this;
        if (!this.mainservice.user){
            this.mainservice.user = this.mainservice.getUserInfo().share();
            this.mainservice.user
                .subscribe(
                    (response) => {

                        if(_.hasIn(response,"token") && response.token === null){
                            if ($this.mainservice.global && !$this.mainservice.global.notSecure){
                                let global = _.cloneDeep($this.mainservice.global);
                                global.notSecure = true;
                                $this.mainservice.setGlobal(global);
                            }else{
                                if ($this.mainservice.global && $this.mainservice.global.notSecure){
                                    $this.mainservice.global.notSecure = true;
                                }else{
                                    $this.mainservice.setGlobal({notSecure: true});
                                }
                            }
                            $this.mainservice.user.user = 'admin';
                            $this.mainservice.user.roles = ['user', 'admin'];
                            $this.mainservice.isRole = (role) => {
                                return true;
                            };
                            $this.isRole = $this.mainservice.isRole;
                            $this.initGetDevicename(2);
                        }else{
                            let browserTime = Math.floor(Date.now() / 1000);
                            if(response.systemCurrentTime != browserTime){
                                let diffTime = browserTime - response.systemCurrentTime;
                                response.expiration = response.expiration + diffTime;
                            }
                            if ($this.mainservice.global && !$this.mainservice.global.authentication){
                                let global = _.cloneDeep($this.mainservice.global);
                                global.authentication = response;
                                $this.mainservice.setGlobal(global);
                            }else{
                                if ($this.mainservice.global && $this.mainservice.global.authentication){
                                    $this.mainservice.global.authentication = response;
                                }else{
                                    $this.mainservice.setGlobal({authentication: response});
                                }
                            }
                            $this.mainservice.user.user = response.user;
                            $this.mainservice.user.roles = response.roles;
                            $this.mainservice.user.realm = response.realm;
                            $this.mainservice.user.authServerUrl = response['auth-server-url'];
                            $this.mainservice.isRole = function(role){
                                if (response.user === null && response.roles.length === 0){
                                    return true;
                                }else{
                                    if (response.roles && response.roles.indexOf(role) > -1){
                                        return true;
                                    }else{
                                        return false;
                                    }
                                }
                            };
                            $this.user = $this.mainservice.user;
                            $this.isRole = $this.mainservice.isRole;
                            $this.realm = response.realm;
                            $this.authServerUrl = response['auth-server-url'];
                            let host    = location.protocol + '//' + location.host;
                            $this.logoutUrl = response['auth-server-url'] + `/realms/${response.realm}/protocol/openid-connect/logout?redirect_uri=`
                                + encodeURIComponent(host + location.pathname);
                            $this.initGetDevicename(2);
                        }
                    },
                    (response) => {
                        // this.user = this.user || {};
                        console.log('in user auth errorespons', response);
                        $this.mainservice.user.user = 'user';
                        $this.mainservice.user.roles = ['user', 'admin'];
                        $this.mainservice.isRole = (role) => {
                            if (role === 'admin'){
                                return false;
                            }else{
                                return true;
                            }
                        };
                        $this.isRole = $this.mainservice.isRole;
                        $this.initGetDevicename(2);
                    }
                );
        }
        let currentBrowserTime = new Date().getTime();
        this.$http.get('../monitor/serverTime')
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
            .subscribe(res=>{
                if(_.hasIn(res,"serverTimeWithTimezone") && res.serverTimeWithTimezone){
                    console.log("server clock res",res);
                    let serverTimeObject = j4care.splitTimeAndTimezone(res.serverTimeWithTimezone);
                    this.startClock(new Date(serverTimeObject.time).getTime()+((new Date().getTime()-currentBrowserTime)/2));
                    // this.startClock(new Date(serverTimeObject.time));
                }
            });
    }
    startClock(serverTime){
        this.currentServerTime = new Date(serverTime);
        this.clockInterval = setInterval(() => {
            // this.currentClockTime = new Date(this.currentServerTime);
            // this.currentServerTime += 1000;
            this.currentServerTime.setMilliseconds(this.currentServerTime.getMilliseconds()+1000);
        }, 1000);
    }
    synchronizeClock(serverTime){
        clearInterval(this.clockInterval);
        this.startClock(serverTime);
    }
    progress(){
        let changeTo = function (t) {
            this.progressValue = t;
        };

        return{
            getValue: this.progressValue,
            setValue: (v) => {
                this.progressValue = v;
            }
        };
    };
    @HostListener('window:scroll', ['$event'])
    onScroll(event) {
        if (window.pageYOffset > 150 && !this.showScrollButton){
            this.showScrollButton = true;
        }
        if (window.pageYOffset < 149 && this.showScrollButton){
            this.showScrollButton = false;
        }
    }
    scrollUp(){
        $('html, body').animate({
            scrollTop: 0
        }, 300);
    }
    createPatient(){
        this.mainservice.createPatient({});
    }

    onClick() {
        // this.dcm4che.elementName.forTag()
        console.log('dcm4chetest', DCM4CHE.elementName.forTag('00000000'));

        this.msg.setMsg({
            'title': 'Warning',
            'text': 'Attribute already exists!',
            'status': 'warning',
            'timeout': 50000
        });
        setTimeout(() => {
            this.msg.setMsg({
                'title': 'Info ',
                'text': 'Info message!',
                'status': 'info'
            });
        }, 500);
        // this.messaging.showMessageBlock = true;
        // this.messaging.change.emit(new MessagingComponent(true, "testmsg"));
        // this.messaging.showMessageBlock.emit(false);
        // console.log("showmessging=",this.messaging.showMessageBlock);
    }
    productLabelling(){
        // this.scrollToDialog();
        this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(ProductLabellingComponent, {
            height: 'auto',
            width: 'auto'
        });

        this.dialogRef.componentInstance.archive = this.archive;
        /*        this.dialogRef.afterClosed().subscribe(result => {
         if(result){
         console.log("result", result);
         }else{
         console.log("false");
         }
         });*/
        this.dialogRef.afterClosed().subscribe(res => {
            if (res){
                console.log('in res');
            }
        });
    }
/*    initGetAuth(retries){
        let $this = this;
        this.$http.get('../auth')
            .map(res => {
                let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
            .subscribe(
                (response) => {
                    $this.url  = response.url;
                    // let host    = location.protocol + '//' + location.host;
                    //
                    // $this.logoutUrl = response.url + '/realms/dcm4che/protocol/openid-connect/logout?redirect_uri='
                    //     + encodeURIComponent(host + location.pathname);
                }, (response) => {
                    // vex.dialog.alert("Error loading device names, please reload the page and try again!");
                    if (retries){
                        $this.initGetAuth(retries - 1);
                    }else{
                        $this.url = '/auth';
                        let host = location.protocol + '//' + location.host;
                        $this.logoutUrl =  host + '/auth/realms/dcm4che/protocol/openid-connect/logout?redirect_uri='
                            + encodeURIComponent(host + location.pathname);
                    }
                });

    }*/
    initGetDevicename(retries){
        let $this = this;
        this.$http.get('../devicename')
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
            .subscribe(
                (res) => {
                    $this.mainservice["deviceName"] = res.dicomDeviceName;
                    $this.$http.get('../devices?dicomDeviceName=' + res.dicomDeviceName)
                        .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
                        .subscribe(
                            arc => {
                                $this.mainservice["archiveDevice"] = arc[0];
                                $this.archive = arc[0];
                            },
                            (err2)=>{
                                if (retries)
                                    $this.initGetDevicename(retries - 1);
                            }
                        );
                },(err)=>{
                    if (retries)
                        $this.initGetDevicename(retries - 1);
                }
            );
    }
    sidenavopen = false;
}


