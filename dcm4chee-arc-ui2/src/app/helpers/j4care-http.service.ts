import { Injectable } from '@angular/core';
import {Http, Headers} from '@angular/http';
import {Observable} from 'rxjs';
import {AppService} from "../app.service";
import * as _ from 'lodash';
import {WindowRefService} from "./window-ref.service";

@Injectable()
export class J4careHttpService{
    constructor (public $http:Http, public mainservice:AppService) {}
    header;
    token;
    get(url,header?){
       return this.request.apply(this,['get', [url, header]]);
    }
    head(url,header?){
        return this.request.apply(this,['head', [url, header]]);
    }
    post(url,data,header?){
        return this.request.apply(this,['post', [url, data, header]]);
    }
    put(url,data,header?){
        return this.request.apply(this,['put', [url, data, header]]);
    }
    delete(url,header?){
        return this.request.apply(this,['delete', [url, header]]);
    }
    private request(requestFunctionName, param){
        let $this = this;
        let headerIndex = (param.length === 3) ? 2:1;
        $this.setHeader(param[headerIndex]);
        return $this.refreshToken().flatMap((response)=>{
                if(response && response.length != 0){
                    $this.resetAuthenticationInfo(response);
                    $this.token = response['token'];
                    // $this.setHeader(param[headerIndex]);
                    $this.mainservice.global.getRealmState = 'notActive';
                }
                $this.setHeader(param[headerIndex]);
                param[headerIndex] = {"headers":$this.header};
                return $this.$http[requestFunctionName].apply($this.$http , param);
            }).catch(res=>{
                if(res.ok === false && res.status === 0 && res.type === 3){
                    WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";
                }
                return res;
        });
    }
    resetAuthenticationInfo(response){
        let $this = this;
        let browserTime = Math.floor(Date.now() / 1000);
        if(response.systemCurrentTime != browserTime){
            let diffTime = browserTime - response.systemCurrentTime;
            response.expiration = response.expiration + diffTime;
        }
        if ($this.mainservice.global && !$this.mainservice.global.authentication){
            let global = _.cloneDeep($this.mainservice.global);
            global.authentication = response;
            $this.mainservice.setGlobal(global);
        }else{
            if ($this.mainservice.global && $this.mainservice.global.authentication){
                $this.mainservice.global.authentication = response;
            }else{
                $this.mainservice.setGlobal({authentication: response});
            }
        }
    }
    refreshToken():Observable<any>{
        if(!_.hasIn(this.mainservice,"global.authentication") || !this.tokenValid()){
            return this.$http.get('rs/realm').map(res => {
                let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/");
                if(pattern.exec(res.url)){
                    WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";
                }
                resjson = res.json();
                }catch (e){
                    resjson = [];
                } return resjson;
            });
        }else{
            this.token = this.mainservice.global.authentication.token;
            return Observable.of([]);
        }
    }
    tokenValid(){
        if(this.mainservice.global.authentication.expiration > Math.floor(Date.now() / 1000)){
            return true;
        }else{
            return false;
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
