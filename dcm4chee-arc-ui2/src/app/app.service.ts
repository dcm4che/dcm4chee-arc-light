import {Injectable, OnInit, OnDestroy} from '@angular/core';
import {Http} from '@angular/http';
import {Observable, Subject, Subscription} from 'rxjs';
import {User} from './models/user';
import * as _ from 'lodash';
import {WindowRefService} from "./helpers/window-ref.service";
import {DatePipe} from "@angular/common";
import {J4careHttpService} from "./helpers/j4care-http.service";
import {HttpClient} from "@angular/common/http";

@Injectable()
export class AppService implements OnInit, OnDestroy{
    private _user: User;
    private _global;
    subscription: Subscription;

    constructor(public ngHttp:Http) {
        this.subscription = this.globalSet$.subscribe(obj => {
            console.log('globalset subscribe ', obj);
            this._global = obj;
            console.log('globalafterset', this._global);
        });
    }

    get global() {
        return this._global;
    }

    set global(value) {
        this._global = value;
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

    // Observable string sources
    private setMessageSource = new Subject<string>();
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
    setGlobal(object: any) {
        // if(this._global){
        //
        //     let globalClone = _.cloneDeep(this._global);
        //     _.merge(globalClone, object);
        //     console.log("globalClone",globalClone);
        //     this.setGlobalSource.next(globalClone);
        // }else{
            this.setGlobalSource.next(object);
        // }
    }
    createPatient(patient: any){
        this.createPatientSource.next(patient);
    }

    // setMsg(msg){
    //     console.log("in appservice",msg);
    //     this.msg.setMsg(msg);
    // }
/*    getRealmOfLogedinUser(){
        return this.$http.get('rs/realm')
            .map(res => {
                let resjson;
                try {
                    resjson = res.json();
                } catch (e) {
                    resjson = res;
                }
                return resjson;
            });
    }*/
    getUserInfo(): Observable<User>{
        return this.ngHttp.get('rs/realm')
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;});
    }
    get user(): any {
        return this._user;
    }

    set user(value: any) {
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
    // getUserObservable():Observable<User>{
    //     return Observable.create(()=>{
    //         return this._user;
    //     })
    // }
    // setMessag(show, msg){
    //     this.messaging.showMessageBlock = show;
    //     this.messaging.msg = msg;
    // }

/*    confirm(confirmparameters){
        this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(ConfirmComponent, {
            height: 'auto',
            width: '500px'
        });
        this.dialogRef.componentInstance.parameters = confirmparameters;
        return this.dialogRef;
    }*/
    ngOnDestroy() {
        // prevent memory leak when component destroyed
        this.subscription.unsubscribe();
    }
    param(filter){
        let filterMaped = Object.keys(filter).map((key) => {
            if (filter[key] || filter[key] === false){
                return key + '=' + filter[key];
            }
        });
        let filterCleared = _.compact(filterMaped);
        return filterCleared.join('&');
    }
    getServerTime(){
/*        return this.http.get('../monitor/serverTime')
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;});
    */}

}
