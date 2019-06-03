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

type KeycloakClient = KeycloakModule.KeycloakClient;

@Injectable()
export class KeycloakService {
    static keycloakAuth: KeycloakClient = Keycloak();
    static keycloakConfig:any;
    private static setTokenSource = new Subject<any>();
    static getTokenObs = KeycloakService.setTokenSource.asObservable();
    constructor(
        private mainservice:AppService
    ){
        KeycloakService.keycloakConfig = JSON.parse(localStorage.getItem('keycloakConfig'));
    }
    static init(options?: any) {
        let $this = this;
        if(KeycloakService.keycloakConfig){
            KeycloakService.keycloakAuth = Keycloak(KeycloakService.keycloakConfig);
/*            KeycloakService.keycloakAuth.init(Globalvar.KEYCLOAK_OPTIONS()).success(res=>{
                console.log("this.keycloaksuccess",KeycloakService.keycloakAuth.token);
            });*/
            return Observable.of(1).flatMap(res=>{ return new Promise((resolve, reject) => {
                KeycloakService.keycloakAuth.init(Globalvar.KEYCLOAK_OPTIONS())
                    .success(() => {
                        // this.test.emit(KeycloakService.keycloakAuth.token);
                        this.setTokenSource.next(KeycloakService.keycloakAuth.token);
                        resolve();
                    })
                    .error((errorData: any) => {
                        reject(errorData);
                    });
            })})
        }else{
            KeycloakService.keycloakConfig = JSON.parse(localStorage.getItem('keycloakConfig'));
             console.log("in else");
            KeycloakService.keycloakAuth = Keycloak(KeycloakService.keycloakConfig);
/*            KeycloakService.keycloakAuth.init(Globalvar.KEYCLOAK_OPTIONS()).success(res=>{
                console.log("this.keycloaksuccess",KeycloakService.keycloakAuth.token);
            });*//*
            return this.mainservice.getKeycloakJson().flatMap((keycloakJson:any)=>{
                console.log("dcmWebApps",keycloakJson);
                localStorage.setItem("this.keycloakConfig",JSON.stringify(keycloakJson));
                KeycloakService.keycloakAuth = Keycloak(keycloakJson);
                // return KeycloakService.keycloakAuth.init({flow: 'standard', responseMode: 'fragment', checkLoginIframe: true, onLoad: 'login-required'}).success();
                return new Promise((resolve, reject) => {
                    KeycloakService.keycloakAuth.init(keycloakJson)
                        .success(() => {
                            resolve();
                        })
                        .error((errorData: any) => {
                            reject(errorData);
                        });
                })
            });*/
        }

/*        const keycloakPromise = new Promise((resolve, reject) => {
            KeycloakService.keycloakAuth.init(options)
                .success(() => {
                    resolve();
                })
                .error((errorData: any) => {
                    reject(errorData);
                });
        });
        return Observable.from(keycloakPromise);*/
    }


    authenticated(): boolean {
        return KeycloakService.keycloakAuth.authenticated;
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
/*        const tokenPromise:Promise<any> = new Promise((resolve,reject) =>{
            if (KeycloakService.keycloakAuth.token) {
                KeycloakService.keycloakAuth
                    .updateToken(5)
                    .success(() => {
                        resolve(<string>KeycloakService.keycloakAuth.token);
                    })
                    .error(() => {
                        reject('Failed to refresh token');
                    });
            } else {
                reject('Not loggen in');
            }
        });
        const tokenObservable: Observable<any> = Observable.fromPromise(tokenPromise);
        if(KeycloakService.keycloakAuth.authenticated){
            return tokenObservable.switchMap(()=>{
               return Observable.of(KeycloakService.keycloakAuth.token);
            });
        }else{
            return tokenObservable.switchMap(()=>{
                return KeycloakService.getTokenObs;
            });
        }*/
        if(KeycloakService.keycloakAuth.authenticated){
            return Observable.of(KeycloakService.keycloakAuth.token);
        }else{
            return KeycloakService.getTokenObs;
        }
    }

    getToken1(): Promise<any> {
        return new Promise<any>((resolve, reject) => {
            if (KeycloakService.keycloakAuth.token) {
                KeycloakService.keycloakAuth
                    .updateToken(5)
                    .success(() => {
                        resolve(<string>KeycloakService.keycloakAuth.token);
                    })
                    .error(() => {
                        reject('Failed to refresh token');
                    });
            } else {
                reject('Not loggen in');
            }
        });
    }
}
