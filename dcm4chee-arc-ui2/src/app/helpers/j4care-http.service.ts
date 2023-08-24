import { Injectable } from '@angular/core';
import {AppService} from "../app.service";

import * as _ from 'lodash-es';
import {HttpErrorHandler} from "./http-error-handler";
import {DcmWebApp} from "../models/dcm-web-app";
import {HttpClient, HttpHeaders} from "@angular/common/http";
import {DcmWebAppRequestParam, HttpMethod} from "../interfaces";
import {j4care} from "./j4care.service";
import {KeycloakService} from "./keycloak-service/keycloak.service";
import {throwError, of, Observable} from "rxjs";
import {catchError, flatMap, map} from "rxjs/operators";

@Injectable()
export class J4careHttpService{
    constructor (
        private $httpClient:HttpClient,
        public mainservice:AppService,
        public httpErrorHandler:HttpErrorHandler,
        private _keycloakService: KeycloakService
    ){
        console.log("in j4care-http-service constructor");
    }
    header;
    token = {};
    get(url:string,header?, doNotEncode?:boolean, dcmWebApp?:DcmWebApp, params?:any){
        let httpParameters;
        if(dcmWebApp && _.hasIn(dcmWebApp,"dcmKeycloakClientID")){
            url = url || j4care.getUrlFromDcmWebApplication(dcmWebApp, this.mainservice.baseUrl);
            httpParameters = {
                url:(doNotEncode ? url : encodeURI(url)),
                doNotEncode:doNotEncode,
                header:header,
                dcmWebApp:dcmWebApp,
                params:params
            };
            if(_.hasIn(header,"responseType")){
                httpParameters["responseType"] = header["responseType"];
                delete header.responseType;
                if(Object.keys(header).length === 0){
                    httpParameters["header"] = undefined;
                }
            }
            return this.dcmWebAppRequest.apply(this,['get', httpParameters]);
        }else{
            if(dcmWebApp){
                url = url || this.getUrlFromDcmWebAppAndParams(dcmWebApp, params);
            }
            httpParameters = {
                url:(doNotEncode ? url : encodeURI(url)),
                header:header
            };
            if(_.hasIn(header,"responseType")){
                httpParameters["responseType"] = header["responseType"];
                delete header.responseType;
                if(Object.keys(header).length === 0){
                    httpParameters["header"] = undefined;
                }
            }
            return this.request.apply(this,['get', httpParameters]);
        }
    }
    head(url:string,header?, doNotEncode?:boolean, dcmWebApp?:DcmWebApp, params?:any){
        if(dcmWebApp && _.hasIn(dcmWebApp,"dcmKeycloakClientID")){
            url = url || j4care.getUrlFromDcmWebApplication(dcmWebApp, this.mainservice.baseUrl);
            return this.dcmWebAppRequest.apply(this,['head', {url:(doNotEncode ? url : encodeURI(url)), doNotEncode:doNotEncode,header:header, dcmWebApp:dcmWebApp, params:params}]);
        }else{
            if(dcmWebApp){
                url = url || this.getUrlFromDcmWebAppAndParams(dcmWebApp, params);
            }
            return this.request.apply(this,['head', {url:(doNotEncode ? url : encodeURI(url)), header:header}]);
        }
    }
    post(url:string,data:any,header?, doNotEncode?:boolean, dcmWebApp?:DcmWebApp, params?:any){
        if(dcmWebApp && _.hasIn(dcmWebApp,"dcmKeycloakClientID")){
            url = url || j4care.getUrlFromDcmWebApplication(dcmWebApp, this.mainservice.baseUrl);
            return this.dcmWebAppRequest.apply(this,['post', {url:(doNotEncode ? url : encodeURI(url)), doNotEncode:doNotEncode,header:header, dcmWebApp:dcmWebApp, params:params}]);
        }else{
            if(dcmWebApp){
                url = url || this.getUrlFromDcmWebAppAndParams(dcmWebApp, params);
            }
            return this.request.apply(this,['post', {url:(doNotEncode ? url : encodeURI(url)), data:data, header:header}]);
        }
    }
    put(url:string,data:any,header?, doNotEncode?:boolean, dcmWebApp?:DcmWebApp, params?:any){
        if(dcmWebApp && _.hasIn(dcmWebApp,"dcmKeycloakClientID")){
            url = url || j4care.getUrlFromDcmWebApplication(dcmWebApp, this.mainservice.baseUrl);
            return this.dcmWebAppRequest.apply(this,['put', {url:(doNotEncode ? url : encodeURI(url)), doNotEncode:doNotEncode,header:header, dcmWebApp:dcmWebApp, params:params, data:data}]);
        }else{
            if(dcmWebApp){
                url = url || this.getUrlFromDcmWebAppAndParams(dcmWebApp, params);
            }
            return this.request.apply(this,['put', {url:(doNotEncode ? url : encodeURI(url)), data:data, header:header}]);
        }
    }
    delete(url:string,header?, doNotEncode?:boolean, dcmWebApp?:DcmWebApp, params?:any){
        if(dcmWebApp && _.hasIn(dcmWebApp,"dcmKeycloakClientID")){
            url = url || j4care.getUrlFromDcmWebApplication(dcmWebApp, this.mainservice.baseUrl);
            return this.dcmWebAppRequest.apply(this,['delete', {url:(doNotEncode ? url : encodeURI(url)), doNotEncode:doNotEncode,header:header, dcmWebApp:dcmWebApp, params:params}]);
        }else{
            if(dcmWebApp){
                url = url || this.getUrlFromDcmWebAppAndParams(dcmWebApp, params);
            }
            return this.request.apply(this,['delete', {url:(doNotEncode ? url : encodeURI(url)), header:header}]);
        }
    }
    private getUrlFromDcmWebAppAndParams(dcmWebApp:DcmWebApp, params:any){
        let url = j4care.getUrlFromDcmWebApplication(dcmWebApp, this.mainservice.baseUrl);
        if(params)
            return `${url}?${j4care.objToUrlParams(params)}`;
        return url;
    }
    private dcmWebAppRequest(requestFunctionName:HttpMethod, param:DcmWebAppRequestParam){
        return this.getRealm(param.dcmWebApp).pipe(flatMap(response=>{
            param.header = {
                headers: new HttpHeaders({
                    'Content-Type':  'application/json',
                    'Authorization': `Bearer ${response.token}`
                })
            };
            return this.$httpClient[requestFunctionName].apply(this.$httpClient , this.getParamAsArray(param, requestFunctionName));
        }));
    }
    private request(requestFunctionName:HttpMethod, param, dcmWebApp?:DcmWebApp){
        let $this = this;
        return $this.refreshToken().pipe(
            flatMap((response)=>{
                $this.setHeader(param.header, dcmWebApp);
                param.header = {headers:this.header};
                return $this.$httpClient[requestFunctionName].apply($this.$httpClient , this.getParamAsArray(param, requestFunctionName));
            }),
            catchError(res=>{
                j4care.log("In catch",res);
                if(res.statusText === "Unauthorized"){
                    return $this.refreshToken().pipe(flatMap((resp)=>{
                        // this.setGlobalToken(resp,param);
                        return $this.$httpClient[requestFunctionName].apply($this.$httpClient , this.getParamAsArray(param, requestFunctionName));
                    }));
                }
                return throwError(res);
            }),
            map((res:any)=>{
               if(_.hasIn(res,"body")){
                   if(_.hasIn(res,"headers")){
                       try{
                           const warning = res.headers.get("Warning") || "";
                           if(warning){
                               this.mainservice.showWarning(warning);
                           }
                       }catch (e) {
                       }
                   }
                   return res.body;
               }
               return res;
            })
        );
    }
    getParamAsArray(param:any, requestFunctionName?:HttpMethod){
        let httpParam = [];
        [
            "url",
            "data",
            "header",
            "params",
            "responseType"
        ].forEach(key=>{
            if(_.hasIn(param,key)){
                if(key === "responseType"){
                    let headerObject = httpParam[1] || {};
                    headerObject["responseType"] = param[key];
                    httpParam[1] = headerObject;
                    // httpParam.push({responseType:param[key]});
                }else{
                    if(key=== "header"){
                        httpParam.push({
                            ...param[key],
                            ...{
                                observe:"response"
                            }
                        });
                    }else{
                        httpParam.push(param[key]);
                    }
                }
            }else{
                if(key === "data" && (requestFunctionName === "post" || requestFunctionName === "put")){
                    httpParam.push({});
                }
            }
        });
        return httpParam;
    }
    resetAuthenticationInfo(response){
        let browserTime = Math.floor(Date.now() / 1000);
        if(response.systemCurrentTime != browserTime){
            let diffTime = browserTime - response.systemCurrentTime;
            response.expiration = response.expiration + diffTime;
        }
        this.setValueInGlobal('authentication',response);
    }
    setGlobalToken(response, param){
        if(response && response.length != 0){
            if(response['token'] === null){
                this.setValueInGlobal('notSecure',true);
                // this.mainservice.setSecured(false);
            }else{
                this.setValueInGlobal('notSecure',false);
                // this.mainservice.setSecured(true);
                this.resetAuthenticationInfo(response);
                this.token["UI"] = response['token'];
                // this.setHeader(param[headerIndex]);
                this.mainservice.global.getRealmStateActive = true;
            }
        }
        if(!this.mainservice.global.notSecure){
            this.setHeader(param.header);
            param.header = {"headers":this.header};
        }
        this.setValueInGlobal('getRealmStateActive',false);
    }
    getRealm(dcmWebApp?:DcmWebApp){
        let service = this._keycloakService.getToken();
        if(dcmWebApp && dcmWebApp.dcmWebAppName && _.hasIn(dcmWebApp,"dcmKeycloakClientID") && dcmWebApp.dcmKeycloakClientID){
            service = this.request("get",{url:`../token2/${dcmWebApp.dcmWebAppName}`});
        }
        return service
            .pipe(map(res => {
                let resjson;
                try{
                    resjson = res.json();
                    console.log("getRealm Response:",res);
                }catch (e){
                    // j4care.log("error on extracting json",e);
                    if(_.hasIn(e, "message") && e.message.indexOf(".json") > -1){
                        resjson = res;
                    }else{
                        resjson = [];
                    }
                }
                if(dcmWebApp && dcmWebApp.dcmWebAppName){
                    this.token[dcmWebApp.dcmWebAppName] = resjson.token;
                }else{
                    this.token["UI"] = KeycloakService.keycloakAuth.token;
                }
                return resjson;
            }))
    }
    refreshToken(dcmWebApp?:DcmWebApp):Observable<any>{
        if(!dcmWebApp){
            if((!_.hasIn(KeycloakService,"keycloakAuth.authenticated") || !this.tokenValid()) && (!this.mainservice.global || !this.mainservice.global.notSecure) && (!this.mainservice.global || !this.mainservice.global.getRealmStateActive)){
                this.setValueInGlobal('getRealmStateActive',true);
                return this.getRealm();
            }else{
                if(!this.mainservice.global || !this.mainservice.global.notSecure){
                    if((KeycloakService.keycloakAuth && KeycloakService.keycloakAuth.authenticated) && !KeycloakService.keycloakAuth.isTokenExpired(5)){
                        this.token["UI"] = KeycloakService.keycloakAuth.token;
                    }else{
                        this.setValueInGlobal('getRealmStateActive',true);
                        return this.getRealm();
                    }
                }
                return of([]);
            }
        }
        return of([]);

    }
    tokenValid(){
        return this._keycloakService.authenticated() && !KeycloakService.keycloakAuth.isTokenExpired(5);
    }
    setValueInGlobal(key, value){
        if (this.mainservice.global && !this.mainservice.global[key]){
            let global = _.cloneDeep(this.mainservice.global);
            global[key] = value;
            this.mainservice.setGlobal(global);
        }else{
            if (this.mainservice.global && this.mainservice.global[key]){
                this.mainservice.global[key] = value;
            }else{
                let global = {};
                global[key] = value;
                this.mainservice.setGlobal(global);
            }
        }
    }
    setHeader(header, dcmWebApp?:DcmWebApp){
        let token;
        if(dcmWebApp && dcmWebApp.dcmKeycloakClientID){
            token = this.token[dcmWebApp.dcmWebAppName];
        }else{
            token = this.token["UI"];
        }
        if(header){
            if(token){
                try{
                    if(header instanceof HttpHeaders){
                        this.header = header.set('Authorization', `Bearer ${token}`);
                    }else{
                        if(_.hasIn(header, "headers")){
                            let newHeader = header.headers;
                            this.header = newHeader.set('Authorization', `Bearer ${token}`);
                        }
                    }
                }catch (e) {
                    this.header = new HttpHeaders().append('Authorization', `Bearer ${token}`);
                    j4care.log("Error on setting bearer on header, j4care-http.service.ts",e);
                }
            }
        }else{
            this.header = new HttpHeaders();
            if(token){
                this.header = new HttpHeaders().append('Authorization', `Bearer ${token}`);
            }
        }
    }
}
