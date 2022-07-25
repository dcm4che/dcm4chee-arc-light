import {Component, OnInit, ViewContainerRef} from '@angular/core';
import { MatDialog, MatDialogRef, MatDialogConfig } from '@angular/material/dialog';
import {MessagingComponent} from './widgets/messaging/messaging.component';
import {AppService} from './app.service';
import {ViewChild} from '@angular/core';


import {ProductLabellingComponent} from './widgets/dialogs/product-labelling/product-labelling.component';
import {HostListener} from '@angular/core';
import {WindowRefService} from "./helpers/window-ref.service";
import * as _ from 'lodash-es';
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
import {ConfiguredDateTameFormatObject, LanguageObject, LocalLanguageObject} from "./interfaces";
import {AppRequestsService} from "./app-requests.service";
declare var DCM4CHE: any;
declare var Keycloak: any;
const worker = new Worker('./server-time.worker', { type: 'module', name: 'server-time'});

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
    hasAdministrator:boolean = false;
    hasViewRealm:boolean = false;
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
    dateTimeFormat:ConfiguredDateTameFormatObject;
    personNameFormat:string;
    dcm4cheeArch;
    _ = _;
    changeDeviceText = {
        label:$localize `:@@available_devices:Available devices`,
        title:$localize `:@@here_you_can_change_the_archive_device_to_which_the_calls_are_made:Here you can change the archive device to which the calls are made`
    }
    constructor(
        public viewContainerRef: ViewContainerRef,
        public dialog: MatDialog,
        public config: MatDialogConfig,
        public mainservice: AppService,
        private appRequests: AppRequestsService,
        private permissionService:PermissionService,
        private keycloakHttpClient:KeycloakHttpClient,
        private _keycloakService: KeycloakService,
        public httpErrorHandler:HttpErrorHandler
    ){
        console.log("in app.component construct", window);
    }



    ngOnInit(){

/*        this.appRequests.getDcm4cheeArc().subscribe(res=>{
            if(_.hasIn(res, "dcm4chee-arc-urls[0]")){
                this.mainservice.baseUrl = _.get(res, "dcm4chee-arc-urls[0]");
            }
            this.dcm4cheeArch = res;
            console.log("baseUrl=",this.mainservice.baseUrl);
        },err=>{
            console.log("Error on /dcm4chee-arc/ui2/rs/dcm4chee-arc",err);
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

    switchBaseUrl(url){
        if(url){
            this.mainservice.baseUrl = url;
            if(_.hasIn(this.mainservice,'dcm4cheeArcConfig.deviceNameUrlMap') && this.mainservice.dcm4cheeArcConfig.deviceNameUrlMap[url]){
                //this.myDeviceName = this.dcm4cheeArch['deviceNameUrlMap'][url];
                this.mainservice.archiveDeviceName = this.mainservice.dcm4cheeArcConfig.deviceNameUrlMap[url];
            }
            this.mainservice.dcm4cheeArcConfig.open = false;
            //this.dcm4cheeArch.open = false;
        }
    }

    initLanguage(){
        let languageConfig:any = localStorage.getItem('languageConfig');
        if(languageConfig){
            this.languageSwitcher = new LanguageSwitcher(JSON.parse(languageConfig), this.mainservice.user);
        }
        this.mainservice.globalSet$.subscribe(global=>{
            if(_.hasIn(global,"uiConfig")){
                if(_.hasIn(global, "uiConfig.dcmuiLanguageConfig[0]")) {
                    if (languageConfig != JSON.stringify(_.get(global, "uiConfig.dcmuiLanguageConfig[0]"))) { //TODO comparing with stringify is not a good idea
                        localStorage.setItem('languageConfig', JSON.stringify(_.get(global, "uiConfig.dcmuiLanguageConfig[0]")));
                        languageConfig = _.get(global, "uiConfig.dcmuiLanguageConfig[0]");
                        if(languageConfig){
                            this.languageSwitcher = new LanguageSwitcher(languageConfig, this.mainservice.user);
                        }
                    }
                    //TODO check if the current_language is the same with the default language of the uiConfig if not update default language in LDAP and Localstorage
                }
                if(_.hasIn(global, "uiConfig.dcmuiDateTimeFormat") && !this.dateTimeFormat){
                    this.dateTimeFormat = j4care.extractDateTimeFormat(_.get(global, "uiConfig.dcmuiDateTimeFormat"));
                    global["dateTimeFormat"] = this.dateTimeFormat;
                    console.log("Global Date Time Format:", this.dateTimeFormat);
                    this.mainservice.setGlobal(global);
                }
                if(_.hasIn(global, "uiConfig.dcmuiPersonNameFormat") && !this.personNameFormat){
                    this.personNameFormat = _.get(global, "uiConfig.dcmuiPersonNameFormat");
                    global["personNameFormat"] = this.personNameFormat;
                    console.log("Global Patient Name Format:", this.personNameFormat);
                    this.mainservice.setGlobal(global);
                }
            }
        });
    }
    init(){
        this.setUserInformation(()=>{
            this.initLanguage();
        });
        Date.prototype.toDateString = function() {
            return `${this.getFullYear()}${j4care.getSingleDateTimeValueFromInt(this.getMonth()+1)}${j4care.getSingleDateTimeValueFromInt(this.getDate())}${j4care.getSingleDateTimeValueFromInt(this.getHours())}${j4care.getSingleDateTimeValueFromInt(this.getMinutes())}${j4care.getSingleDateTimeValueFromInt(this.getSeconds())}`;
        };
        this.initGetDevicename(2);
/*        this.setServerTime(()=>{
        });*/

        document.addEventListener("visibilitychange", () => {
            if(document.visibilityState === "visible"){
                this.startTime();
            }else{
                if(worker){
                    worker.postMessage({
                        serverTime:this.currentServerTime,
                        idle:document.hidden
                    });
                }
            }
        });
    }
    startTime(){
        if (typeof Worker !== 'undefined') {
            worker.onmessage = ({data}) => {
                try{
                    this.currentServerTime = new Date(data.serverTime);
                    this.mainservice.serverTime = this.currentServerTime;
                    if(data.refresh){
                        this.refreshTime(worker);
                    }
                }catch (e) {
                    j4care.log("Error on setting time coming  from worker",e);
                }
            };
            this.refreshTime(worker);
            // worker.postMessage('worker started');
        }else{
            console.log("worker not available");
        }

    }

    refreshTime(worker){
        let currentBrowserTime = new Date().getTime();
        this.appRequests.getServerTime()
            .subscribe(res=>{
                if(_.hasIn(res,"serverTimeWithTimezone") && res.serverTimeWithTimezone){
                    let serverTimeObject = j4care.splitTimeAndTimezone(res.serverTimeWithTimezone);
                    this.timeZone = serverTimeObject.timeZone;
                    this.mainservice.timeZone = this.timeZone;
                    worker.postMessage({
                        serverTime:new Date(serverTimeObject.time).getTime()+((new Date().getTime()-currentBrowserTime)/2),
                        idle:document.hidden
                    });
                    this.hideExtendedClock();
                }
            });
    }
    switchLanguage(language:LanguageObject){
/*        if(language.code === "en"){
            localStorage.removeItem('current_language');
        }else{*/
            const localLanguage:LocalLanguageObject = {
                language:language,
                username:this.mainservice.user.user
            };
            localStorage.setItem('current_language', JSON.stringify(localLanguage));
            //TODO update the uiConfig so that the new choose language to be the default one for this user
        //}
        setTimeout(()=>{
            location.reload();
        },1);
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
        this.appRequests.getServerTime()
            .subscribe(res=>{
                if(_.hasIn(res,"serverTimeWithTimezone") && res.serverTimeWithTimezone){
                    console.log("server clock res",res);
                    let serverTimeObject = j4care.splitTimeAndTimezone(res.serverTimeWithTimezone);
                    this.timeZone = serverTimeObject.timeZone;
                    this.mainservice.timeZone = serverTimeObject.timeZone;
                    this.mainservice.serverTimeWithTimezone = res.serverTimeWithTimezone;
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
                    this.hasAdministrator = _.hasIn(user,"tokenParsed.realm_access.roles") && user.tokenParsed.realm_access.roles.indexOf("ADMINISTRATOR") > -1;
                    this.hasViewRealm = _.hasIn(user,"tokenParsed.resource_access[realm-management].roles") && user.tokenParsed.resource_access["realm-management"].roles.indexOf("view-realm") > -1;
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
        try{
            let url;
            if(window.location.protocol.toLowerCase() === "https:"){
                url = `//${window.location.hostname}:${this.mainservice["management-https-port"]}/console`
            }else{
                url = `//${window.location.hostname}:${this.mainservice["management-http-port"]}/console`
            }
            e.preventDefault();
            window.open(url, "_blank");
        }catch (e) {
            window.open(`//${window.location.hostname}:9990/console`, "_blank");
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

    testurl = "";
    onClick() {
        // this.dcm4che.elementName.forTag()
/*        console.log('dcm4chetest', DCM4CHE.elementName.forTag('00000000'));

        this.msg.setMsg({
            'title': $localize `:@@warning:Warning`,
            'text': $localize `:@@attribute_already_exists:Attribute already exists!`,
            'status': 'warning',
            'timeout': 50000
        });
        setTimeout(() => {
            this.msg.setMsg({
                'title': $localize `:@@app.info:Info `,
                'text': $localize `:@@app.info_message:Info message!`,
                'status': 'info'
            });
        }, 500);*/
        this.mainservice.testUrl(this.testurl);
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
        this.appRequests.getDeviceName()
            .subscribe(
                (res) => {
                    // $this.mainservice["deviceName"] = res.dicomDeviceName;
                    this.initGetPDQServices();
                    this.startTime();
                    this.dcm4cheeArch = res;
                    $this.mainservice["xRoad"] = res.xRoad || false;
                    $this.mainservice["management-http-port"] = res["management-http-port"] || 9990;
                    $this.mainservice["management-https-port"] = res["management-https-port"] || 9993;
                    this.appRequests.getDeviceInfo(res.dicomDeviceName)
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
                    console.log("---------err",err);
                    if (retries)
                        $this.initGetDevicename(retries - 1);
                }
            );
    }
    initGetPDQServices(){
        this.appRequests.getPDQServices().subscribe(pdqs=>{
            this.mainservice.updateGlobal("PDQs",pdqs);
        })
    }


}

