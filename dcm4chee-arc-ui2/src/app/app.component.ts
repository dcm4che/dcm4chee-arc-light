import {Component, OnInit, ViewContainerRef} from '@angular/core';
import {MatDialog, MatDialogRef, MatDialogConfig} from '@angular/material';
import {MessagingComponent} from './widgets/messaging/messaging.component';
import {AppService} from './app.service';
import {ViewChild} from '@angular/core';
import 'rxjs/add/operator/catch';
import 'rxjs/add/operator/share';

import {Http} from '@angular/http';
import {ProductLabellingComponent} from './widgets/dialogs/product-labelling/product-labelling.component';
import {HostListener} from '@angular/core';
import {WindowRefService} from "./helpers/window-ref.service";
import * as _ from 'lodash';
import {J4careHttpService} from "./helpers/j4care-http.service";
import {j4care} from "./helpers/j4care.service";
import {PermissionService} from "./helpers/permissions/permission.service";
import {Observable} from "../../node_modules/rxjs";
import {HttpClient} from "@angular/common/http";
import {DcmWebApp} from "./models/dcm-web-app";
import {KeycloakService} from "./helpers/keycloak-service/keycloak.service";
import {Globalvar} from "./constants/globalvar";
import {KeycloakHttpClient} from "./helpers/keycloak-service/keycloak-http-client.service";
declare var DCM4CHE: any;
declare var Keycloak: any;

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html'
})
export class AppComponent implements OnInit {
    progressValue = 30;
    //Detect witch header should be shown.
    user: any = {};
    dialogRef: MatDialogRef<any>;
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
    clockUnExtended = true;
    myDeviceName = '';
    timeZone;
    sidenavopen = false;
    superUser:boolean = false;
    // vex["defaultOptions"]["className"] = 'vex-theme-os';
    constructor(
        public viewContainerRef: ViewContainerRef,
        public dialog: MatDialog,
        public config: MatDialogConfig,
        public mainservice: AppService,
        private $http:J4careHttpService,
        private nativeHttp:Http,
        private permissionService:PermissionService,
        private keycloakHttpClient:KeycloakHttpClient,
        private _keycloakService: KeycloakService
    ){
        console.log("in app.component construct", window);
    }

    ngOnInit(){
        // console.log("app.component.ts",this.mainservice.keycloak);
        console.log("config",this.mainservice);
        if(j4care.hasSet(KeycloakService,"keycloakAuth.token")){
            console.log("token in j4carehttpservice",KeycloakService.keycloakAuth.token);
            this.init();
        }else {
            this._keycloakService.init(Globalvar.KEYCLOAK_OPTIONS()).subscribe(res=>{
                console.log("subscripkeycloak res",res);
                console.log("token",KeycloakService.keycloakAuth.token);
                this.init();
            })
        }


    }
    init(){
        Date.prototype.toDateString = function() {
            return `${this.getFullYear()}${j4care.getSingleDateTimeValueFromInt(this.getMonth()+1)}${j4care.getSingleDateTimeValueFromInt(this.getDate())}${j4care.getSingleDateTimeValueFromInt(this.getHours())}${j4care.getSingleDateTimeValueFromInt(this.getMinutes())}${j4care.getSingleDateTimeValueFromInt(this.getSeconds())}`;
        };
        this.initGetDevicename(2);
        this.setServerTime(()=>{
            this.setLogutUrl();
            this.initGetPDQServices();
        });
    }
    testLogout(){
        this.keycloakHttpClient.get('./rs/devicename').subscribe(res=>{
           console.log("keycloakhttpclient",res);
        });
        console.log("in logout",this.mainservice.keycloak);
        console.log("in logouturl",this.logoutUrl);
        // this.mainservice.keycloak.logout(this.logoutUrl);
    }
    setServerTime(recall?:Function){
        let currentBrowserTime = new Date().getTime();
        this.getServerTime()
            .subscribe(res=>{
                if(_.hasIn(res,"serverTimeWithTimezone") && res.serverTimeWithTimezone){
                    console.log("server clock res",res);
                    let serverTimeObject = j4care.splitTimeAndTimezone(res.serverTimeWithTimezone);
                    this.timeZone = serverTimeObject.timeZone;
                    this.startClock(new Date(serverTimeObject.time).getTime()+((new Date().getTime()-currentBrowserTime)/2));
                    // this.startClock(new Date(serverTimeObject.time));
                }
                if(recall)
                    recall.apply(this);
            });
    }
    setLogutUrl(){
        try{
            this.mainservice.user = this.mainservice.user || this.mainservice.global.authentication
            this.user = this.mainservice.user;
            this.realm = this.mainservice.user.realm;
            this.superUser = (_.hasIn(this.mainservice.user, "su") && this.mainservice.user["su"]);
            this.authServerUrl = this.mainservice.user['auth-server-url'];
            let host    = location.protocol + '//' + location.host;
            this.logoutUrl =  `${this.mainservice.user['auth-server-url']}/realms/${this.mainservice.user.realm}/protocol/openid-connect/logout?redirect_uri=${encodeURIComponent(host + location.pathname)}`;
        }catch(e){
            console.warn("Authentication not found",e);
        }
    }
    closeFromOutside(){
        if(this.showMenu)
            this.showMenu = false;
    }
    startClock(serverTime){
        this.currentServerTime = new Date(serverTime);
        this.mainservice.serverTime = this.currentServerTime;
        clearInterval(this.clockInterval);
        this.clockInterval = setInterval(() => {
            // this.currentClockTime = new Date(this.currentServerTime);
            // this.currentServerTime += 1000;
            this.currentServerTime.setMilliseconds(this.currentServerTime.getMilliseconds()+1000);
            this.mainservice.serverTime = this.currentServerTime;
        }, 1000);
        this.hideExtendedClock();
    }
    hideExtendedClock(){
        setTimeout(()=>{
            this.clockUnExtended = false;
        },2000);
    }
    synchronizeClock(serverTime){
        clearInterval(this.clockInterval);
        this.startClock(serverTime);
    }
    logout(){
        window.location.href = this.logoutUrl;
/*        setTimeout(()=>{
            location.reload(true);
        },100);*/
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
    onScroll(event){
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
        this.getDeviceName()
            .subscribe(
                (res) => {
                    // $this.mainservice["deviceName"] = res.dicomDeviceName;
                    $this.mainservice["xRoad"] = res.xRoad || false;
                    this.getDeviceInfo(res.dicomDeviceName)
                        .subscribe(
                            arc => {
                                $this.mainservice["archiveDevice"] = arc[0];
                                $this.archive = arc[0];
                                try{
                                    this.myDeviceName = arc[0].dicomDeviceName;
                                }catch (e){

                                }
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
    initGetPDQServices(){
        this.getPDQServices().subscribe(pdqs=>{
            this.mainservice.updateGlobal("PDQs",pdqs);
        })
    }
    getPDQServices(url?:string):Observable<any[]>{
        return this.$http.get(`${url || '.'}/rs/pdq`).map(res => j4care.redirectOnAuthResponse(res));
    }
    getServerTime(url?:string){
        return this.$http.get(`${url || '..'}/monitor/serverTime`).map(res => j4care.redirectOnAuthResponse(res));
    }
    getDeviceName(url?:string){
        return this.$http.get(`${url || '.'}/rs/devicename`).map(res => j4care.redirectOnAuthResponse(res));
    }
    getDeviceInfo(dicomDeviceName:string, url?:string){
        return this.$http.get(`${url || '.'}/rs/devices?dicomDeviceName=${dicomDeviceName}`).map(res => j4care.redirectOnAuthResponse(res));
    }
}

