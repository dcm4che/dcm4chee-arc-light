import {Component, OnInit, ViewContainerRef} from '@angular/core';
//import { MatLegacyDialog as MatDialog, MatLegacyDialogRef as MatDialogRef, MatLegacyDialogConfig as MatDialogConfig } from '@angular/material/legacy-dialog';
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
import { HttpClient } from "@angular/common/http";
import {DcmWebApp} from "./models/dcm-web-app";
import {KeycloakService} from "./helpers/keycloak-service/keycloak.service";
import {Globalvar} from "./constants/globalvar";
import {KeycloakHttpClient} from "./helpers/keycloak-service/keycloak-http-client.service";
import {User} from "./models/user";
import {LanguageSwitcher} from "./models/language-switcher";
import {HttpErrorHandler} from "./helpers/http-error-handler";
import {ConfiguredDateTameFormatObject, LanguageObject, LanguageProfile, LocalLanguageObject} from "./interfaces";
import {AppRequestsService} from "./app-requests.service";
import { Title } from '@angular/platform-browser';
import {MatDialog, MatDialogRef} from "@angular/material/dialog";
import {KeycloakHelperService} from "./helpers/keycloak-service/keycloak-helper.service";
declare var DCM4CHE: any;
declare var Keycloak: any;
const worker = new Worker(new URL('./server-time.worker', import.meta.url), { type: 'module', name: 'server-time'});

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
    dcmuiHideClock:boolean = false;
    dcmuiPageTitle:string;
    dcmuiHideOtherPatientIDs: boolean = false;
    dcmuiInstitutionNameFilterType:string;
    dcmuiInstitutionName:string[];
    constructor(
        public viewContainerRef: ViewContainerRef,
        public dialog: MatDialog,
        public mainservice: AppService,
        private appRequests: AppRequestsService,
        private permissionService:PermissionService,
        private keycloakHttpClient:KeycloakHttpClient,
        private _keycloakService: KeycloakService,
        private keycloakHelperService: KeycloakHelperService,
        public httpErrorHandler:HttpErrorHandler,
        private title:Title
    ){
        console.log("in app.component construct", window);
    }



    ngOnInit(){
        console.warn("oniinit in app.component")

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
        try{
            if(url){
                if(_.hasIn(this.mainservice,'dcm4cheeArcConfig.deviceNameUrlMap') && this.mainservice.dcm4cheeArcConfig.deviceNameUrlMap[url] && this.mainservice.dcm4cheeArcConfig.deviceNameUrlMap[url] != 'NOT_FOUND'){
                    this.mainservice.baseUrl = url;
                    //this.myDeviceName = this.dcm4cheeArch['deviceNameUrlMap'][url];
                    this.mainservice.archiveDeviceName = this.mainservice.dcm4cheeArcConfig.deviceNameUrlMap[url];
                }
                this.mainservice.dcm4cheeArcConfig.open = false;
                //this.dcm4cheeArch.open = false;
            }
        }catch (e) {
            console.error(e);
        }
    }

    initLanguage(){
        let languageConfig:any = localStorage.getItem('languageConfig');
        if(languageConfig){
            this.languageSwitcher = new LanguageSwitcher(JSON.parse(languageConfig));
        }
        this.mainservice.globalSet$.subscribe(global=>{
            if(_.hasIn(global,"uiConfig")){
                console.warn("initlanguage in app.component after uiConfig")
                if(_.hasIn(global, "uiConfig.dcmuiInstitutionNameFilterType") && !this.dcmuiInstitutionNameFilterType){
                    this.dcmuiInstitutionNameFilterType = _.get(global, "uiConfig.dcmuiInstitutionNameFilterType");
                    global["dcmuiInstitutionNameFilterType"] = this.dcmuiInstitutionNameFilterType;
                    this.mainservice.setGlobal(global);

                }
                if(_.hasIn(global, "uiConfig.dcmuiInstitutionName") && !this.dcmuiInstitutionName){
                    this.dcmuiInstitutionName = _.get(global, "uiConfig.dcmuiInstitutionName");
                    global["dcmuiInstitutionName"] = this.dcmuiInstitutionName;
                    this.mainservice.setGlobal(global);
                }
                if(_.hasIn(global, "uiConfig.dcmuiLanguageConfig[0]")) {
                    if (!_.isEqual(languageConfig,_.get(global, "uiConfig.dcmuiLanguageConfig[0]"))) {
                        languageConfig = _.get(global, "uiConfig.dcmuiLanguageConfig[0]");
                        localStorage.setItem('languageConfig', JSON.stringify(languageConfig));
                        if(languageConfig){
                            this.languageSwitcher = new LanguageSwitcher(languageConfig);
                        }
                    }
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
                if(_.hasIn(global, "uiConfig.dcmuiHideClock") && !this.dcmuiHideClock){
                    this.dcmuiHideClock = _.get(global, "uiConfig.dcmuiHideClock");
                    global["dcmuiHideClock"] = this.dcmuiHideClock;
                    this.mainservice.setGlobal(global);
                }
                if(_.hasIn(global, "uiConfig.dcmuiHideOtherPatientIDs") && !this.dcmuiHideOtherPatientIDs){
                    this.dcmuiHideOtherPatientIDs = _.get(global, "uiConfig.dcmuiHideOtherPatientIDs");
                    global["dcmuiHideOtherPatientIDs"] = this.dcmuiHideOtherPatientIDs;
                    this.mainservice.setGlobal(global);
                }
                if(_.hasIn(global, "uiConfig.dcmuiPageTitle") && !this.dcmuiPageTitle){
                    this.dcmuiPageTitle = _.get(global, "uiConfig.dcmuiPageTitle");
                    global["dcmuiPageTitle"] = this.dcmuiPageTitle;
                    if(this.dcmuiPageTitle && this.dcmuiPageTitle != ""){
                        this.title.setTitle(this.dcmuiPageTitle);
                    }
                    this.mainservice.setGlobal(global);
                }

            }
        });
    }
   /* applyLanguageProfile(languageProfile:LanguageProfile|LanguageProfile[]){
        try{
            let profile:LanguageProfile;
            if(languageProfile instanceof Array){
                profile =<LanguageProfile> languageProfile[0];
            }else{
                profile = languageProfile;
            }
            const currentLanguageCode = this.getActiveLanguageCodeFromURL();
            if(profile.dcmDefaultLanguage.indexOf(currentLanguageCode) === -1){
                //reload
                const profileDefaultLanguageCode = profile.dcmDefaultLanguage.substring(0,2);
                console.warn("redirecting in applylanguage",currentLanguageCode, ", defaultlanguageprof",profileDefaultLanguageCode);
                const localLanguage:any = {
                    language:profile.dcmDefaultLanguage,
                    username:this.mainservice.user.user || ""
                };
                localStorage.setItem('current_language', JSON.stringify(localLanguage));
                window.location.href = `/dcm4chee-arc/ui2/${profileDefaultLanguageCode}/`;
            }
            console.log("locale")
        }catch (e) {
            
        }
    }*/
    getActiveLanguageCodeFromURL(){
        try{
            const currentPath = location.pathname;
            const regex = /dcm4chee-arc\/ui2\/(\w*)\//;
            let match = regex.exec(currentPath);
            if (match !== null) {
                return match[1];
            }
        }catch (e) {
            return "en";
        }
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
        let saveAndRedirect = function () {
            localStorage.setItem('current_language', language.code);
            window.location.href = `/dcm4chee-arc/ui2/${language.code}/`;
            setTimeout(() => {
                location.reload();
            }, 1);
        }
        if(!this.mainservice.global.notSecure) {
            this.keycloakHelperService.changeLanguageToUserProfile(language.code).subscribe(res => {
                saveAndRedirect();
            }, err => {
                saveAndRedirect();
                console.error("Error on switching language", err)
            });
        }else{
            saveAndRedirect();
        }
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
            let url:string;
            let port:string;
            if(this.mainservice["management-url"]){
                url = this.mainservice["management-url"];
            }else{
                if(this.mainservice["management-https-port"] && this.mainservice["management-http-port"]){
                    if(window.location.protocol.toLowerCase() === "https:"){
                        port = this.mainservice["management-https-port"];
                    }else{
                        port = this.mainservice["management-http-port"];
                    }
                }else{
                    port = this.mainservice["management-https-port"] || this.mainservice["management-http-port"] || "9990";
                }
                url = `//${this.mainservice["management-host"] || window.location.hostname}:${port}/console`
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
        //this.config.viewContainerRef = this.viewContainerRef;
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
                    if(res["management-url"]){
                        $this.mainservice["management-url"] = res["management-url"];
                    }else{
                        $this.mainservice["management-https-port"] = res["management-https-port"] || 9990;
                        $this.mainservice["management-http-port"] = res["management-http-port"] || 9990;
                        $this.mainservice["management-host"] = res["management-host"] || window.location.hostname;
                    }
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


/*    private compareSavedLanguageWithLanguageInPath() {
        try{
            const currentLanguage:LocalLanguageObject = JSON.parse(localStorage.getItem('current_language'));
            const regex = /dcm4chee-arc\/ui2\/(\w{2})\//gm;
            let match;
            if ((match = regex.exec(location.href)) !== null) {
                if(match[1] != currentLanguage.language.code){
                    window.location.href = `/dcm4chee-arc/ui2/${currentLanguage.language.code}/`;
                }
            }
        }catch (e) {}
    }*/
}

