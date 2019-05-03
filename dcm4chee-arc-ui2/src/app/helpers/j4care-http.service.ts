import { Injectable } from '@angular/core';
import {Http, Headers} from '@angular/http';
import {Observable} from 'rxjs';
import {AppService} from "../app.service";
import 'rxjs/add/operator/mergeMap';
import * as _ from 'lodash';
import {WindowRefService} from "./window-ref.service";
import {HttpErrorHandler} from "./http-error-handler";

@Injectable()
export class J4careHttpService{
    constructor (
        public $http:Http,
        public mainservice:AppService,
        public httpErrorHandler:HttpErrorHandler,
    ) {}
    header;
    token;
    get(url,header?, doNotEncode?){
       return this.request.apply(this,['get', [doNotEncode ? url : encodeURI(url), header]]);
    }
    head(url,header?, doNotEncode?){
        return this.request.apply(this,['head', [doNotEncode ? url : encodeURI(url), header]]);
    }
    post(url,data,header?, doNotEncode?){
        return this.request.apply(this,['post', [doNotEncode ? url : encodeURI(url), data, header]]);
    }
    put(url,data,header?, doNotEncode?){
        return this.request.apply(this,['put', [doNotEncode ? url : encodeURI(url), data, header]]);
    }
    delete(url,header?, doNotEncode?){
        return this.request.apply(this,['delete', [doNotEncode ? url : encodeURI(url), header]]);
    }
    private request(requestFunctionName, param){
        let $this = this;
        let headerIndex = (param.length === 3) ? 2:1;
        $this.setHeader(param[headerIndex]);
        return $this.refreshToken().flatMap((response)=>{
                this.setGlobalToken(response,param,headerIndex);
                return $this.$http[requestFunctionName].apply($this.$http , param);
            }).catch(res=>{
                if(res.ok === false && res.status === 0 && res.type === 3){
                    if(_.hasIn(res,"_body.target.__zone_symbol__xhrURL") && _.get(res,"_body.target.__zone_symbol__xhrURL") === "rs/realm")
                        WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";
                }
                if(res.statusText === "Unauthorized"){
                    return $this.getRealm().flatMap((resp)=>{
                        this.setGlobalToken(resp,param,headerIndex);
                        return $this.$http[requestFunctionName].apply($this.$http , param);
                    });
                }
                return Observable.throw(res);
        });
    }
    resetAuthenticationInfo(response){
        let browserTime = Math.floor(Date.now() / 1000);
        if(response.systemCurrentTime != browserTime){
            let diffTime = browserTime - response.systemCurrentTime;
            response.expiration = response.expiration + diffTime;
        }
        this.setValueInGlobal('authentication',response);
    }
    setGlobalToken(response, param, headerIndex){
        if(response && response.length != 0){
            if(response['token'] === null){
                this.setValueInGlobal('notSecure',true);

            }else{
                this.setValueInGlobal('notSecure',false);
                this.resetAuthenticationInfo(response);
                this.token = response['token'];
                // this.setHeader(param[headerIndex]);
                this.mainservice.global.getRealmStateActive = true;
            }
        }
        if(!this.mainservice.global.notSecure){
            this.setHeader(param[headerIndex]);
            param[headerIndex] = {"headers":this.header};
        }
        this.setValueInGlobal('getRealmStateActive',false);
    }
    getRealm(){
        return this.$http.get('rs/realm').map(res => {
            let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/");
                if(pattern.exec(res.url)){
                    if(_.hasIn(res,"_body.target.__zone_symbol__xhrURL") && _.get(res,"_body.target.__zone_symbol__xhrURL") === "rs/realm")
                        WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";
                }
                resjson = res.json();
            }catch (e){
                resjson = [];
            } return resjson;
        })
    }
    refreshToken():Observable<any>{
        if((!_.hasIn(this.mainservice,"global.authentication") || !this.tokenValid()) && (!this.mainservice.global || !this.mainservice.global.notSecure) && (!this.mainservice.global || !this.mainservice.global.getRealmStateActive)){
            this.setValueInGlobal('getRealmStateActive',true);
            return this.getRealm();
        }else{
            if(!this.mainservice.global.notSecure){
                if(_.hasIn(this.mainservice, "global.authentication.token")){
                    this.token = this.mainservice.global.authentication.token;
                }else{
                    this.setValueInGlobal('getRealmStateActive',true);
                    return this.getRealm();
                }
            }
            return Observable.of([]);
        }
    }
    tokenValid(){
        if(_.hasIn(this.mainservice,"global.authentication.expiration") && (this.mainservice.global.authentication.expiration > Math.floor(Date.now() / 1000))){
            return true;
        }else{
            return false;
        }
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
    setHeader(header){
        if(header){
            if(this.token){
                console.log("header",header);
                if(_.hasIn(header,"headers")){
                    header.headers.set('Authorization', `Bearer ${this.token}`);
                    this.header = header.headers;
                }else{
                    header.set('Authorization', `Bearer ${this.token}`);
                    this.header = header;
                }
            }
        }else{
            this.header = new Headers();
            if(this.token){
                this.header.append('Authorization', `Bearer ${this.token}`);
            }
        }
    }
}
