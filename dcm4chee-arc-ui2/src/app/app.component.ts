import {Component, OnInit, ViewContainerRef} from '@angular/core';
import { MatDialog, MatDialogRef, MatDialogConfig } from '@angular/material/dialog';
import {MessagingComponent} from './widgets/messaging/messaging.component';
import {AppService} from './app.service';
import {ViewChild} from '@angular/core';
import 'rxjs/add/operator/catch';
import 'rxjs/add/operator/share';
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
import {User} from "./models/user";
import {LanguageSwitcher} from "./models/language-switcher";
import {HttpErrorHandler} from "./helpers/http-error-handler";
import {LanguageConfig} from "./interfaces";
declare var DCM4CHE: any;
declare var Keycloak: any;

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
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
    @ViewChild(MessagingComponent, {static: true}) msg;
    clockUnExtended = true;
    myDeviceName = '';
    timeZone;
    sidenavopen = false;
    superUser:boolean = false;
    languageSwitcher:LanguageSwitcher;
    constructor(
        public viewContainerRef: ViewContainerRef,
        public dialog: MatDialog,
        public config: MatDialogConfig,
        public mainservice: AppService,
        private $http:J4careHttpService,
        private permissionService:PermissionService,
        private keycloakHttpClient:KeycloakHttpClient,
        private _keycloakService: KeycloakService,
        public httpErrorHandler:HttpErrorHandler
    ){
        console.log("in app.component construct", window);
    }



    ngOnInit(){
        // const savedLanguageCode = localStorage.getItem('language_code');
/*        let languageConfig:any = localStorage.getItem('languageConfig');
        console.log("global",this.mainservice.global);
        console.log("this.user1",this.mainservice.user);
        console.log("_keycloakService",this._keycloakService.getUserInfo())
        this._keycloakService.getUserInfo().subscribe(user=>{
            console.log("user",user);
            if(languageConfig){
                this.languageSwitcher = new LanguageSwitcher(JSON.parse(languageConfig));
            }
            this.mainservice.globalSet$.subscribe(global=>{
                if(_.hasIn(global,"uiConfig")){
                    if(_.hasIn(global, "uiConfig.dcmuiLanguageConfig[0]")) {
                        console.log("this.user",this.mainservice.user);
                        if (languageConfig != JSON.stringify(_.get(global, "uiConfig.dcmuiLanguageConfig[0]"))) {
                            localStorage.setItem('languageConfig', JSON.stringify(_.get(global, "uiConfig.dcmuiLanguageConfig[0]")));
                            languageConfig = _.get(global, "uiConfig.dcmuiLanguageConfig[0]");
                            if(languageConfig){
                                this.languageSwitcher = new LanguageSwitcher(languageConfig);
                            }
                        }
                    }
                }
            });
        });*/
/*        this.mainservice.getUiConfig().subscribe(res=>{
            console.log("uiconfgi",res);
        },err=>{
            this.httpErrorHandler.handleError(err);
        });*/
        if(j4care.hasSet(KeycloakService,"keycloakAuth.token")){
            this.mainservice.updateGlobal("notSecure",false);
            this.init();
        }else {
            this._keycloakService.init(Globalvar.KEYCLOAK_OPTIONS()).subscribe(res=>{
                this.init();
            },(err)=>{
                this.init();
            })
        }
    }
    initLanguage(){
        let languageConfig:any = localStorage.getItem('languageConfig');
        console.log("global",this.mainservice.global);
        console.log("this.user1",this.mainservice.user);
        if(languageConfig){
            this.languageSwitcher = new LanguageSwitcher(JSON.parse(languageConfig), this.mainservice.user);
        }
        this.mainservice.globalSet$.subscribe(global=>{
            if(_.hasIn(global,"uiConfig")){
                if(_.hasIn(global, "uiConfig.dcmuiLanguageConfig[0]")) {
                    console.log("this.user",this.mainservice.user);
                    if (languageConfig != JSON.stringify(_.get(global, "uiConfig.dcmuiLanguageConfig[0]"))) {
                        localStorage.setItem('languageConfig', JSON.stringify(_.get(global, "uiConfig.dcmuiLanguageConfig[0]")));
                        languageConfig = _.get(global, "uiConfig.dcmuiLanguageConfig[0]");
                        if(languageConfig){
                            this.languageSwitcher = new LanguageSwitcher(languageConfig, this.mainservice.user);
                        }
                    }
                }
            }
        });
    }
    init(){
        this.setUserInformation(()=>{
            console.log("afteruserinformation");
            this.initLanguage();
        });
        Date.prototype.toDateString = function() {
            return `${this.getFullYear()}${j4care.getSingleDateTimeValueFromInt(this.getMonth()+1)}${j4care.getSingleDateTimeValueFromInt(this.getDate())}${j4care.getSingleDateTimeValueFromInt(this.getHours())}${j4care.getSingleDateTimeValueFromInt(this.getMinutes())}${j4care.getSingleDateTimeValueFromInt(this.getSeconds())}`;
        };
        this.initGetDevicename(2);
        this.setServerTime(()=>{
            this.initGetPDQServices();
        });
    }
    switchLanguage(languageCode){
        if(languageCode === "en"){
            localStorage.removeItem('language_code');
        }else{
            localStorage.setItem('language_code', languageCode);
        }
        setTimeout(()=>{
            location.reload();
        },200);
    }
    testUser(){
        KeycloakService.keycloakAuth.loadUserInfo().success(user=>{
            console.log("in test success",user);
            this._keycloakService.setUserInfo({
                userProfile:user,
                tokenParsed:KeycloakService.keycloakAuth.tokenParsed,
                authServerUrl:KeycloakService.keycloakAuth.authServerUrl,
                realm:KeycloakService.keycloakAuth.realm
            });
        })
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
                }
                if(recall)
                    recall.apply(this);
            });
    }
    setUserInformation(recall:Function){
        if(this.mainservice.global && this.mainservice.global.notSecure){
            this.user = null;
            this.realm = null;
            this.superUser = true;
            this.authServerUrl = null;
            recall.apply(this);
        }else{
            try{
                this.mainservice.getUser().subscribe((user:User)=>{
                    this.user = user;
                    this.realm = user.realm;
                    this.superUser = user.su;
                    this.authServerUrl = user.authServerUrl;
                    recall.apply(this);
                },(err)=>{
                    recall.apply(this);
                });
            }catch (e) {
                j4care.log("User information couldn't be set",e);
                recall.apply(this);
            }
        }
    }
    logout(){
        KeycloakService.keycloakAuth.logout();
    }
    gotToWildflyConsole(e){
        e.preventDefault();
        window.open(            `//${window.location.hostname}:9990`, "_blank");
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
        window.scrollTo({
            top:0,
            left:0,
            behavior: 'smooth'
        });
    }
    createPatient(){
        this.mainservice.createPatient({});
    }

    onClick() {
        // this.dcm4che.elementName.forTag()
        console.log('dcm4chetest', DCM4CHE.elementName.forTag('00000000'));

        this.msg.setMsg({
            'title': 'Warning',
            'text': $localize `:@@app.attribute_exist:Attribute already exists!`,
            'status': 'warning',
            'timeout': 50000
        });
        setTimeout(() => {
            this.msg.setMsg({
                'title': $localize `:@@app.info:Info `,
                'text': $localize `:@@app.info_message:Info message!`,
                'status': 'info'
            });
        }, 500);
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
        return this.$http.get(`${url || '..'}/pdq`)
    }
    getServerTime(url?:string){
        return this.$http.get(`${url || '..'}/monitor/serverTime`)
    }
    getDeviceName(url?:string){
        return this.$http.get(`${url || '..'}/devicename`)
    }
    getDeviceInfo(dicomDeviceName:string, url?:string){
        return this.$http.get(`${url || '..'}/devices?dicomDeviceName=${dicomDeviceName}`)
    }
}

