/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/// <reference path="keycloak.d.ts"/>

import {Observable} from "rxjs/Observable";

declare var Keycloak: any;

import {EventEmitter, Injectable} from '@angular/core';
import {DcmWebApp} from "../../models/dcm-web-app";
import {AppService} from "../../app.service";
import {from} from "rxjs/observable/from";
import {Globalvar} from "../../constants/globalvar";
import {Subject} from "../../../../node_modules/rxjs";
import {j4care} from "../j4care.service";
import {User} from "../../models/user";
import * as _ from 'lodash';

type KeycloakClient = KeycloakModule.KeycloakClient;

@Injectable()
export class KeycloakService {
    static keycloakAuth: KeycloakClient;
    static keycloakConfig:any;
    private setTokenSource = new Subject<any>();
    private setUserSource = new Subject<any>();
    private userInfo;
    keycloakConfigName = `keycloak_config_${location.host}`;
    // static getTokenObs =
    constructor(
       private mainservice:AppService
    ){
        try{
            KeycloakService.keycloakConfig = JSON.parse(localStorage.getItem(this.keycloakConfigName));
        }catch (e) {
            j4care.log("keycloakConfig probably not set",e);
        }
    }
    init(options?: any) {
        if(KeycloakService.keycloakConfig){
            // this.mainservice.updateGlobal("notSecure",false);
            KeycloakService.keycloakAuth = new Keycloak(KeycloakService.keycloakConfig);
            return j4care.promiseToObservable(new Promise((resolve, reject) => {
                KeycloakService.keycloakAuth.init(Globalvar.KEYCLOAK_OPTIONS())
                    .success(() => {
                        this.setTokenSource.next(KeycloakService.keycloakAuth);
                        KeycloakService.keycloakAuth.loadUserProfile().success(user=>{
                            this.setUserInfo({
                                userProfile:user,
                                tokenParsed:KeycloakService.keycloakAuth.tokenParsed,
                                authServerUrl:KeycloakService.keycloakAuth.authServerUrl,
                                realm:KeycloakService.keycloakAuth.realm
                            });
                            this.mainservice.setSecured(true);
                            this.mainservice.updateGlobal("notSecure",false);
                            resolve();
                        }).error(err=>{
                            this.mainservice.setSecured(false);
                            this.mainservice.updateGlobal("notSecure",true);
                            console.error("err on loadingUserProfile",err);
                            reject(err);
                        });
                    })
                    .error((errorData: any) => {
                        this.mainservice.setSecured(false);
                        this.mainservice.updateGlobal("notSecure",true);
                        reject(errorData);
                    });
            }))
        }else{
            return this.mainservice.getKeycloakJson().flatMap((keycloakJson:any)=>{
                if(!_.isEmpty(keycloakJson)){
                    localStorage.setItem(this.keycloakConfigName,JSON.stringify(keycloakJson));
                    KeycloakService.keycloakAuth = new Keycloak(keycloakJson);
                    return j4care.promiseToObservable(new Promise((resolve, reject) => {
                        KeycloakService.keycloakAuth.init(Globalvar.KEYCLOAK_OPTIONS())
                            .success(() => {
                                this.setTokenSource.next(KeycloakService.keycloakAuth.token);
                                resolve();
                            })
                            .error((errorData: any) => {
                                reject(errorData);
                            });
                    }))
                }else{
                    this.setUserInfo(undefined);
                    this.setTokenSource.next("");
                    this.mainservice.updateGlobal("notSecure",true);
                    return Observable.of([]);
                }
            })
        }
    }

    setUserInfo(user){
        console.log("*******in set userINFo",user);
        this.userInfo = user;
        this.setUserSource.next(user);
    }
    getUserInfo():Observable<any>{
        console.log("**********inget userINFO",this.userInfo);
        if(this.userInfo){
            return Observable.of(this.userInfo);
        }else{
            return this.setUserSource.asObservable();
        }
    }
    getTokenObs():Observable<any>{
        return this.setTokenSource.asObservable();
    }

    authenticated(): boolean {
        return KeycloakService.keycloakAuth.authenticated;
    }
    isTokenExpired(minValidity:number):boolean{
        return KeycloakService.keycloakAuth.isTokenExpired(minValidity);
    }

    login() {
        KeycloakService.keycloakAuth.login();
    }

    logout() {
        KeycloakService.keycloakAuth.logout();
    }

    account() {
        KeycloakService.keycloakAuth.accountManagement();
    }

    getToken():Observable<any>{
        if(_.hasIn(this.mainservice,"global.notSecure") && this.mainservice.global.notSecure){
            return Observable.of({});
        }else{
            if(KeycloakService.keycloakAuth && KeycloakService.keycloakAuth.authenticated){
                console.log("KeycloakService.keycloakAuth",KeycloakService.keycloakAuth)
                if(KeycloakService.keycloakAuth.isTokenExpired(5)){
                    return j4care.promiseToObservable(new Promise<any>((resolve, reject) => {
                        if (KeycloakService.keycloakAuth.token) {
                            KeycloakService.keycloakAuth
                                .updateToken(5)
                                .success(() => {
                                    resolve(<any>KeycloakService.keycloakAuth);
                                })
                                .error(() => {
                                    reject('Failed to refresh token');
                                });
                        } else {
                            reject('Not logged in');
                        }
                    }));
                }else{
                    return Observable.of(KeycloakService.keycloakAuth);
                }
            }else{
                return this.getTokenObs();
            }
        }
    }
}
