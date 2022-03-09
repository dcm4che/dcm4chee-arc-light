import {Injectable, OnInit, OnDestroy} from '@angular/core';
import {Observable, Subject, Subscription, of} from 'rxjs';
import {User} from './models/user';
import * as _ from 'lodash-es';
import {WindowRefService} from "./helpers/window-ref.service";
import {DatePipe} from "@angular/common";
import {HttpClient} from "@angular/common/http";
import {j4care} from "./helpers/j4care.service";
import {DcmWebApp} from "./models/dcm-web-app";
import {Router} from "@angular/router";
import {Error} from "tslint/lib/error";
import {first, map, switchMap} from "rxjs/operators";
import { loadTranslations } from '@angular/localize';
import {ConfiguredDateTameFormatObject, DateTimeFormatMode, TimeRange} from "./interfaces";

@Injectable()
export class AppService implements OnInit, OnDestroy{

    private _user: User;
    private userSubject = new Subject<User>();
    private secured = new Subject<boolean>();
    private serverTimeSubject = new Subject<Date>();
    private securedValue:boolean;
    private _global;
    private _baseUrl:string = '../';
    extensionsMap;
    subscription: Subscription;
    keycloak;
    private _dcm4cheeArcConfig;
    constructor(
        public $httpClient:HttpClient,
        private router: Router
    ) {
        this.subscription = this.globalSet$.subscribe(obj => {
            this._global = obj;
        });
    }
    private _deviceName;
    private _archiveDevice;
    private _archiveDeviceName;
    get global() {
        return this._global;
    }

    private _serverTime:Date;

    get serverTime(): Date {
        return this._serverTime;
    }

    set serverTime(value: Date) {
        this._serverTime = value;
        this.serverTimeSubject.next(value);
    }

    get baseUrl(): string {
        return this._baseUrl;
    }

    set baseUrl(value: string) {
        this._baseUrl = value;
    }

    timeZone;
    serverTimeWithTimezone;
    set global(value) {
        this._global = value;
    }

    setUser(user:User){
        this._user = user;
        this.userSubject.next(user);
    }
    getUser():Observable<User>{
        if(this._user){
            return of(this._user);
        }else{
            return this.userSubject.asObservable();
        }
    }

    isSecure(){
        if(typeof this.securedValue === "boolean"){
            return of(this.securedValue);
        }else{
            return this.secured.asObservable();
        }
    }

    setSecured(state:boolean){
        this.securedValue = state;
        this.secured.next(state);
    }
    get dcm4cheeArcConfig() {
        return this._dcm4cheeArcConfig;
    }

    set dcm4cheeArcConfig(value) {
        this._dcm4cheeArcConfig = value;
    }

    private _isRole = function(role){
        if (this.user){
            if (this.user.user === null && this.user.roles.length === 0){
                return true;
            }else{
                if (this.user.roles && this.user.roles.indexOf(role) > -1){
                    return true;
                }else{
                    return false;
                }
            }
        }else{
            if (role === 'admin'){
                return false;
            }else{
                return true;
            }
        }
    };

    get deviceName() {
        return this._deviceName;
    }

    set deviceName(value) {
        this._deviceName = value;
    }

    get archiveDevice() {
        return this._archiveDevice;
    }

    set archiveDevice(value) {
        this._archiveDevice = value;
        if(!this._deviceName && _.hasIn(value,"dicomDeviceName")){
            this._deviceName = value.dicomDeviceName;
        }
        if(!this._archiveDeviceName && _.hasIn(value,"dicomDeviceName")){
            this._archiveDeviceName = value.dicomDeviceName;
        }
    }
    get archiveDeviceName() {
        return this._archiveDeviceName;
    }

    set archiveDeviceName(value) {
        this._archiveDeviceName = value;
    }

// Observable string sources
    private setMessageSource = new Subject<any>();
    private setGlobalSource = new Subject<string>();
    private createPatientSource = new Subject<string>();

    // Observable string streams
    messageSet$ = this.setMessageSource.asObservable();
    globalSet$ = this.setGlobalSource.asObservable();
    createPatient$ = this.createPatientSource.asObservable();
    // Service message commands
    setMessage(msg: any) {
        console.log('in set message', msg);
        this.setMessageSource.next(msg);
    }
    showError(msg:string){
        this.setMessageSource.next({
            "title":$localize `:@@error:Error`,
            "text":msg,
            "status":"error"
        })
    }
    showMsg(msg:string){
        this.setMessageSource.next({
            "title":$localize `:@@info:Info`,
            "text":msg,
            "status":"info"
        })
    }

    showMsgCopyMoveLink(res, action:string) {
        let msg;
        const errorCount = res.filter(result=>result.isError).length;
        let errorDetail = _.hasIn(res, "0.error.error.errorMessage") ? _.get(res, "0.error.error.errorMessage") : '';
        if(errorCount === res.length){
            msg = $localize `:@@study.process_executed_all_failed_detail:${action}:@@action: process executed - all failed:<br>\nErrors: ${errorCount}:@@error:`;
            errorDetail = msg + `<br>\n` + errorDetail;
            this.showError(errorDetail);
        } else {
            msg = $localize `:@@study.process_executed_successfully_detailed:${action}:@@action: process executed successfully:<br>\nErrors: ${errorCount}:@@error:<br>\nSuccessful: ${res.length - errorCount}:@@successfull:`;
            if(errorCount > 0){
                this.showWarning(msg);
            }else{
                this.showMsg(msg);
            }
        }
    }

    showMsgUpdateCharsets(res) {
        let detail = '';
        let successful = _.hasIn(res, "updated") ? _.get(res, "updated") : '';
        let failures = _.hasIn(res, "failures") ? _.get(res, "failures") : '';
        if (successful != '' || successful == 0)
            detail = detail + `updated: ` + successful + `<br>\n`;
        if (failures  != '') {
            detail = detail + `failures: ` + `<br>\n`;
            _.forEach(failures, (s) => {
                detail += s + `<br>\n`;
            })
        }
        if (failures  != '') {
            if (successful != '')
                this.showWarning(detail);
            else
                this.showError(detail);
        } else
            this.showMsg(detail);
    }

    showMsgSupplementIssuer(res){
        console.log(res);
        let detail = '';
        let successful = _.hasIn(res, "pids") ? _.get(res, "pids") : '';
        let ambiguous = _.hasIn(res, "ambiguous") ? _.get(res, "ambiguous") : '';
        let failures = _.hasIn(res, "failures") ? _.get(res, "failures") : '';
        let tooManyDuplicates = _.hasIn(res, "tooManyDuplicates") ? _.get(res, "tooManyDuplicates") : '';
        if (successful != '') {
            detail = detail + `PIDs: ` + `<br>\n`;
            _.forEach(successful, pid => {
                detail = detail + `- PID: ` + pid + `<br>\n`;
            })
        }
        if (ambiguous != '') {
            detail = detail + `Ambiguous: ` + `<br>\n`;
            _.forEach(ambiguous, ambiguousPID => {
                detail = detail + `PID: ` + _.get(ambiguousPID, "pid") + `, Count: ` + ambiguousPID.count + `<br>\n`;
            })
        }
        if (failures  != '') {
            detail = detail + `Failures: ` + `<br>\n`;
            _.forEach(failures, (pid, errorMessage) => {
                detail += `PID: ` + pid + `, Error: ` + errorMessage + `<br>\n`;
            })
        }
        if (tooManyDuplicates != '') {
            detail = detail + `Too many duplicates for: ` + tooManyDuplicates;
        }

        if (failures  != '' || ambiguous != '' || tooManyDuplicates != '') {
            if (successful != '')
                this.showWarning(detail);
            else
                this.showError(detail);
        } else
            this.showMsg(detail);
    }

    showWarning(msg:string){
        this.setMessageSource.next({
            "title":$localize `:@@warning:Warning`,
            "text":msg,
            "status":"warning"
        })
    }
    setGlobal(object: any) {
        this.setGlobalSource.next(object);
    }
    updateGlobal(key:string, object:any){
        if (this.global && !this.global[key]){
            let global = _.cloneDeep(this.global); //,...[{hl7:response}]];
            global[key] = object;
            this.setGlobal(global);
        }else{
            if (this.global && this.global[key]){
                this.global[key] = object;
            }else{
                this.setGlobal({[key]: object});
            }
        }
    }
    createPatient(patient: any){
        this.createPatientSource.next(patient);
    }

    get user(): User {
        return this._user;
    }

    set user(value: User) {
        this._user = value;
    }

    get isRole(): (role) => boolean {
        return this._isRole;
    }

    set isRole(value: (role) => boolean) {
        this._isRole = value;
    }

    ngOnInit(): void {
    }

    ngOnDestroy() {
        // prevent memory leak when component destroyed
        this.subscription.unsubscribe();
    }
    param(filter){
        try{
            let filterMapped = Object.keys(filter).map((key) => {
                if(_.isArray(filter[key])){
                    let multiParameter = [];
                    filter[key].forEach(p=>{
                        multiParameter.push(`${key}=${p}`);
                    });
                    return multiParameter.join("&");
                    // return key + "[]=" + filter[key].join(",");
                }else{
                    if (filter[key] || filter[key] === false || filter[key] === 0){
                        return key + '=' + filter[key];
                    }
                }
            });
            let filterCleared = _.compact(filterMapped);
            return filterCleared.join('&');
        }catch (e) {
            j4care.log("Something went wrong on getting param",e);
            return "";
        }
    }


    getUniqueID(){
        let newDate = new Date(this._serverTime);
        return `${newDate.getFullYear().toString().substr(-2)}${newDate.getMonth()}${newDate.getDate()}${newDate.getHours()}${newDate.getMinutes()}${newDate.getSeconds()}`;
    }


    getMyWebApps(){
        if(_.hasIn(this.global,"myDevice")){
            return of((<DcmWebApp[]>_.get(this.global.myDevice,"dcmDevice.dcmWebApp")).map((dcmWebApp:DcmWebApp)=>{
                dcmWebApp.dcmKeycloakClientID = (<any[]>_.get(this.global.myDevice,"dcmDevice.dcmKeycloakClient")).filter(keycloakClient=>{
                    return keycloakClient.dcmKeycloakClientID === dcmWebApp.dcmKeycloakClientID;
                })[0];
                return dcmWebApp;
            }));
        }else{
            return this.getMyDevice().pipe(map(res=>{
                return (<DcmWebApp[]>_.get(res,"dcmDevice.dcmWebApp")).map((dcmWebApp:DcmWebApp)=>{
                    dcmWebApp.dcmKeycloakClientID = (<any[]>_.get(this.global.myDevice,"dcmDevice.dcmKeycloakClient")).filter(keycloakClient=>{
                        return keycloakClient.dcmKeycloakClientID === dcmWebApp.dcmKeycloakClientID;
                    })[0];
                    return dcmWebApp;
                });
            }))
        }
    }
    getServerTime():Observable<Date>{
        if(this._serverTime){
            return of(this._serverTime);
        }else{
            return this.serverTimeSubject.asObservable().pipe(map(serverTime=>new Date(serverTime)),first());
        }
    }

    setTimeRange(rangeInMinuts?:number, rangeInHours?:number):Observable<TimeRange>{
        return this.getServerTime().pipe(map((serverTime:Date)=>{
            let d = new Date(serverTime);
            if(rangeInHours){
                d.setHours(d.getHours() - rangeInHours);
                return new TimeRange(d, new Date(serverTime));
            }
            rangeInMinuts = rangeInMinuts || 15;
            d.setMinutes(d.getMinutes() - rangeInMinuts);
            return new TimeRange(d, new Date(serverTime));
        }));
    }
    getUiConfig(){
        if(_.hasIn(this.global,"uiConfig")){
            return of(this.global.uiConfig);
        }else{
            return this.getMyDevice().pipe(map(res=>{
                return _.get(res,"dcmDevice.dcmuiConfig[0]");
            }))
        }
    }
    getMyDevice(){
        if(_.hasIn(this.global,"myDevice")){
            return of(this.global.myDevice);
        }else{
            let deviceName;
            let archiveDeviceName;
            return this.getDcm4cheeArc()
                .pipe(
                    switchMap(res => {
                        deviceName = (_.get(res,"UIConfigurationDeviceName") || _.get(res,"dicomDeviceName"));
                        archiveDeviceName = _.get(res,"dicomDeviceName");
                        return this.$httpClient.get(`${j4care.addLastSlash(this.baseUrl)}devices/${deviceName}`)
                    }),
                    map((res)=>{
                        try{
                            let global = _.cloneDeep(this.global) || {};
                            global["uiConfig"] = _.get(res,"dcmDevice.dcmuiConfig[0]");
                            global["myDevice"] = res;
                            this.deviceName = deviceName;
                            this.archiveDeviceName = archiveDeviceName;
                            this.setGlobal(global);
                        }catch(e){
                            console.warn("Permission not found!",e);
                            this.showError($localize `:@@permission_not_found:Permission not found!`);
                        }
                        return res;
                    }));
        }
    }
    getKeycloakJson(){
        if(!this.global || !this.global.notSecure){
            return this.$httpClient.get("./rs/keycloak.json")
                .pipe(map((res:any)=>{
                    if(_.isEmpty(res)){
                        console.log("ojbect is empty",res);
                        this.updateGlobal("notSecure", true);
                        return res;
                    }else{
                        this.updateGlobal("notSecure", false);
                        // this.setSecured(true);
                        return _.mapKeys(res, (value,key:any)=>{
                            if(key === "auth-server-url")
                                return "url";
                            if(key === "resource")
                                return "clientId";
                            return key;
                        });
                    }
                },err=>{
                    this.updateGlobal("notSecure", true);
                    console.log("err",err);
                    this.setSecured(false);
                    throw(err);
                }))
        }else{
            return of({})
        }
    }

    getDcm4cheeArc(){
        if(this._dcm4cheeArcConfig){
            return of(this._dcm4cheeArcConfig);
        }else{
            return this.$httpClient.get("./rs/dcm4chee-arc").pipe(map(dcm4cheeArc=>{
                if(_.hasIn(dcm4cheeArc, "dcm4chee-arc-urls[0]")){
                    this.baseUrl = _.get(dcm4cheeArc, "dcm4chee-arc-urls[0]");
                }
                this._dcm4cheeArcConfig = dcm4cheeArc;
                return dcm4cheeArc;
            }));
        }
    }

    formatBasedOnConfig(date:Date, mode:DateTimeFormatMode):string{
        try{
            let dateTimeFormat:ConfiguredDateTameFormatObject;
            if(_.hasIn(this.global,"dateTimeFormat")){
                dateTimeFormat = _.get(this.global, "dateTimeFormat");
            }else{
                dateTimeFormat = {
                    timeFormat: "HH:mm",
                    dateFormat: "dd.MM.yyyy",
                    dateTimeFormat: "dd.MM.yyyy HH:mm"
                }
            }
            return j4care.formatDate(date, dateTimeFormat[mode]);
        }catch (e){
            j4care.log("Error on formatting date based on config",e);
            return "";
        }
    }

    testUrl(url){

        this.$httpClient.get(url).subscribe(res=>{
            console.log("res",res);
        });
    }
}

