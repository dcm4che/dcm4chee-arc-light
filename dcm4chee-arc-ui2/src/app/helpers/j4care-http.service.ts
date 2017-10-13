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
        let $this = this;
        return this.refreshTolken().flatMap((response)=>{
            if(response && response.length != 0){
                this.resetAuthenticationInfo(response);
                this.token = response['token'];
            }
            this.setHeader(header);
            return this.$http.get(url,{
                headers: this.header
            });
        });
    }
    head(url,header?){
        let $this = this;
        return this.refreshTolken().flatMap((response)=>{
            if(response && response.length != 0){
                this.resetAuthenticationInfo(response);
                this.token = response['token'];
            }
            this.setHeader(header);
            return this.$http.get(url,{
                headers: this.header
            });
        });
    }
    post(url,data,header?){
        this.setHeader(header);
        return this.refreshTolken().flatMap((response)=>{
            if(response && response.length != 0){
                this.resetAuthenticationInfo(response);
                this.token = response['token'];
            }
            this.setHeader(header);
            return this.$http.post(url,data,{
                headers: this.header
            });
        });
    }
    put(url,data,header?){
        this.setHeader(header);
        return this.refreshTolken().flatMap((response)=>{
            if(response && response.length != 0){
                this.resetAuthenticationInfo(response);
                this.token = response['token'];
            }
            this.setHeader(header);
            return this.put(url,data,{
                headers: this.header
            });
        });
    }
    delete(url,header?){
        this.setHeader(header);
        return this.refreshTolken().flatMap((response)=>{
            if(response && response.length != 0){
                this.resetAuthenticationInfo(response);
                this.token = response['token'];
            }
            this.setHeader(header);
            return this.$http.delete(url,{
                headers: this.header
            });
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
    refreshTolken():Observable<any>{
        if(!_.hasIn(this.mainservice,"global.authentication") || !this.tokenValid()){
            return this.$http.get('rs/realm').map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;});
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
            this.header = header;
        }else{
            this.header = new Headers();
        }
        if(this.token){
            this.header.set('Authorization', `Bearer ${this.token}`);
        }
    }
}
