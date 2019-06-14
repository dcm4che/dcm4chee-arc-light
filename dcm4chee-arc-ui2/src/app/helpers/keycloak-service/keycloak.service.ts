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

type KeycloakClient = KeycloakModule.KeycloakClient;

@Injectable()
export class KeycloakService {
    static keycloakAuth: KeycloakClient;
    static keycloakConfig:any;
    private setTokenSource = new Subject<any>();
    private setUserSource = new Subject<any>();
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
            KeycloakService.keycloakAuth = new Keycloak(KeycloakService.keycloakConfig);
            return Observable.of(1).flatMap(res=>{ return new Promise((resolve, reject) => {
                KeycloakService.keycloakAuth.init(Globalvar.KEYCLOAK_OPTIONS())
                    .success(() => {
                        this.setTokenSource.next(KeycloakService.keycloakAuth);
                        KeycloakService.keycloakAuth.loadUserProfile().success(user=>{
                            this.setUserSource.next({
                                userProfile:user,
                                tokenParsed:KeycloakService.keycloakAuth.tokenParsed,
                                authServerUrl:KeycloakService.keycloakAuth.authServerUrl,
                                realm:KeycloakService.keycloakAuth.realm
                            });
                        });
                        resolve();
                    })
                    .error((errorData: any) => {
                        reject(errorData);
                    });
            })})
        }else{
            return this.mainservice.getKeycloakJson().flatMap((keycloakJson:any)=>{
                localStorage.setItem(this.keycloakConfigName,JSON.stringify(keycloakJson));
                KeycloakService.keycloakAuth = new Keycloak(keycloakJson);
                return Observable.of(1).flatMap(res=>{ return new Promise((resolve, reject) => {
                    KeycloakService.keycloakAuth.init(Globalvar.KEYCLOAK_OPTIONS())
                        .success(() => {
                            this.setTokenSource.next(KeycloakService.keycloakAuth.token);
                            resolve();
                        })
                        .error((errorData: any) => {
                            reject(errorData);
                        });
                })})
            });
        }
    }

    getUserInfo():Observable<any>{
        return this.setUserSource.asObservable();
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
        if(KeycloakService.keycloakAuth && KeycloakService.keycloakAuth.authenticated){
            console.log("KeycloakService.keycloakAuth",KeycloakService.keycloakAuth)
            if(KeycloakService.keycloakAuth.isTokenExpired(5)){
                return Observable.of(1).flatMap(res=>{
                    return new Promise<any>((resolve, reject) => {
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
                            // return this.getTokenObs();
                            reject('Not loggen in');
                        }
                    });
                });
            }else{
                return Observable.of(KeycloakService.keycloakAuth);
            }
        }else{
            return this.getTokenObs();
        }
    }
/*    getToken2():Observable<any>{
        if(KeycloakService.keycloakAuth && KeycloakService.keycloakAuth.authenticated && !KeycloakService.keycloakAuth.isTokenExpired(5)){
            console.log("KeycloakService.keycloakAuth",KeycloakService.keycloakAuth)
            return Observable.of(KeycloakService.keycloakAuth);
        }else{
            // return this.getTokenObs();
            return Observable.of(1).flatMap(res=>{ return new Promise<any>((resolve, reject) => {
                if (KeycloakService.keycloakAuth.token) {
                    KeycloakService.keycloakAuth
                        .updateToken(5)
                        .success(() => {
                            this.setTokenSource.next(KeycloakService.keycloakAuth.token);
                            resolve(<any>KeycloakService.keycloakAuth);
                        })
                        .error(() => {
                            reject('Failed to refresh token');
                        });
                } else {
                    // return this.getTokenObs();
                    reject('Not loggen in');
                }
            });
            });
        }
    }

    getToken1(): Observable<any> {
        return Observable.of(1).flatMap(res=>{ return new Promise<any>((resolve, reject) => {
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
                reject('Not loggen in');
            }
            });
        });
    }*/
}
