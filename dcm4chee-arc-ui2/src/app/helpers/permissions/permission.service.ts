import { Injectable } from '@angular/core';
import {j4care} from "../j4care.service";
import {Globalvar} from "../../constants/globalvar";
import {J4careHttpService} from "../j4care-http.service";
import {AppService} from "../../app.service";
import {Route, Router} from "@angular/router";
import * as _ from 'lodash';
import {KeycloakService} from "../keycloak-service/keycloak.service";

@Injectable()
export class PermissionService {

    user;
    uiConfig;
    configChecked = false;
    constructor(
        private $http:J4careHttpService,
        private mainservice:AppService,
        private router: Router,
        private _keycloakService:KeycloakService
    ) { }

    getPermission(url){
        // console.log("permission user",this.mainservice.user.roles);
        if(this.mainservice.user && this.mainservice.user.roles){
            return this.checkSuperAdmin(url);
        }else
            return this.getConfigWithUser(()=>{
                if(this.mainservice.user && !this.mainservice.user.user && this.mainservice.user.roles && this.mainservice.user.roles.length === 0){
                    this.mainservice.global.notSecure = true;
                    return true; //not secured
                }else
                    if(this.mainservice.user && this.mainservice.user.su)
                        return true;
                    else
                        return this.checkMenuTabAccess(url)
            });
    }
    checkSuperAdmin(url){
        if((this.mainservice.user && this.mainservice.user.su) || (!this.mainservice.user.user && this.mainservice.user.roles.length === 0))
            return true;
        else
            return this.getConfig(()=>{return this.checkMenuTabAccess(url)});
    }
    getConfig(response){
        let deviceName;
        let archiveDeviceName;
        if(!this.uiConfig && !this.configChecked)
            return this.$http.get('./rs/devicename')
                .map(res => j4care.redirectOnAuthResponse(res))
                .switchMap(res => {
                    deviceName = (res.UIConfigurationDeviceName || res.dicomDeviceName);
                    archiveDeviceName = res.dicomDeviceName;
                    return this.$http.get('./rs/devices/' + deviceName)
                })
                .map((res)=>{
                    try{
                        this.configChecked = true;
                        this.uiConfig = res.dcmDevice.dcmuiConfig["0"];
                        let global = _.cloneDeep(this.mainservice.global);
                        console.log("permission uiconfig");
                        global["uiConfig"] = res.dcmDevice.dcmuiConfig["0"];
                        global["myDevice"] = res;
                        this.mainservice.deviceName = deviceName;
                        this.mainservice.archiveDeviceName = archiveDeviceName;
                        this.mainservice.setGlobal(global);
                    }catch(e){
                        console.warn("Permission not found!",e);
                        this.mainservice.showError("Permission not found!");
                        return response.call(this);
                    }
                    // return this.checkMenuTabAccess(url);
                    return response.call(this);
                });
        else
            return response.call(this);
    }
    getConfigWithUser(response){
        let deviceName;
        let archiveDeviceName;
        if(!this.uiConfig && !this.configChecked)
            return this._keycloakService.getUserInfo()//TODO Remove one of the mehthodes getUserInfo()
            // return this.mainservice.getUserInfo()
                .switchMap(res => this.mainservice.getUserInfo())
                .map(user=>{
                    console.log("user",user);
                    this.mainservice.user = user;
                    this.user = user;
                    return user;
                })
                .switchMap(res => this.$http.get('./rs/devicename'))
                .map(res => j4care.redirectOnAuthResponse(res))
                .switchMap(res => {
                    deviceName = (res.UIConfigurationDeviceName || res.dicomDeviceName);
                    archiveDeviceName = res.dicomDeviceName;
                    return this.$http.get('./rs/devices/' + deviceName);
                })
                .map(res => j4care.redirectOnAuthResponse(res))
                .map((res)=>{
                    try{
                        this.configChecked = true;
                        this.uiConfig = res.dcmDevice.dcmuiConfig["0"];
                        let global = _.cloneDeep(this.mainservice.global) || {};
                        global["uiConfig"] = res.dcmDevice.dcmuiConfig["0"];
                        global["myDevice"] = res;
                        console.log("permission uiconfig");
                        this.mainservice.archiveDeviceName = archiveDeviceName;
                        this.mainservice.deviceName = deviceName;
                        this.mainservice.setGlobal(global);
                    }catch(e){
                        console.warn("Permission not found!",e);
                        if(this.mainservice.global.notSecure || (this.mainservice.user && !this.mainservice.user.user && this.mainservice.user.roles && this.mainservice.user.roles.length === 0)){
                            this.mainservice.global.notSecure = true;
                        }else
                            this.mainservice.setMessage({
                                'text': "Permission not found!",
                                'status': 'error'
                            });
                        return response.apply(this,[]);
                    }
                    // return this.checkMenuTabAccess(url);
                    return response.apply(this,[]);
                });
        else
            return response.apply(this,[]);
    }

    checkMenuTabAccess(url){
        try{
            let urlAction = Globalvar.LINK_PERMISSION(url);
            let checkObject = this.uiConfig.dcmuiPermission.filter(element=>{
                return urlAction && element.dcmuiAction === urlAction.permissionsAction && element.dcmuiActionParam.indexOf('accessible') > -1;
            });
            if(checkObject && checkObject[0]){
                let check = this.comparePermissionObjectWithRoles(checkObject);
                if(check && checkObject[0].dcmuiActionParam.indexOf('accessible') > -1)
                    return true;
                else
                if(urlAction.nextCheck){
                    this.router.navigate([urlAction.nextCheck]);
                    return {redirect:urlAction.nextCheck};
                }
            }
            return false;
        }catch (e){
            console.warn('Are you sure you configured the permissions? ',e);
            this.mainservice.setMessage({
                'text': "Are you sure you configured the permissions?",
                'status': 'error'
            })
        }
    }
    checkVisibility(permissionObject){
        if(this.mainservice.user && this.mainservice.user.roles && this.mainservice.user.roles.length > 0 && this.mainservice.user.su)
            return true;
        else
            if(this.mainservice.user && !this.mainservice.user.user && this.mainservice.user.roles && this.mainservice.user.roles.length === 0)
                return true; //not secured
            else
                return this.getConfig(()=>{
                    try{

                        let checkObject = this.uiConfig.dcmuiPermission.filter(element=>{
                            return element.dcmuiAction === permissionObject.id && element.dcmuiActionParam.indexOf(permissionObject.param) > -1;
                        });
                        return this.comparePermissionObjectWithRoles(checkObject);
                    }catch (e){
                        console.warn("Error on permission check",e);
                        return false;
                    }
                })
    }
    comparePermissionObjectWithRoles(object){
        try{
            let check = false;
            if(object[0])
                object[0].dcmAcceptedUserRole.forEach(role =>{
                    if(this.mainservice.user.roles && this.mainservice.user.roles.indexOf(role) > -1)
                        check = true;
                });
            return check;
        }catch (err){
            console.warn("Error comparing permissions object with the roles",err);
            return false;
        }
    }
    filterAetDependingOnUiConfig(aets, mode){
        if(this.uiConfig && this.uiConfig.dcmuiAetConfig){
            try{
                let aetConfig = this.uiConfig.dcmuiAetConfig.filter(config=>{
                    let oneAetRolesHasUser = false;
                    if(_.hasIn(config, "dcmAcceptedUserRole") && config.dcmAcceptedUserRole && _.hasIn(this.mainservice.global,"authentication.roles") && this.mainservice.global.authentication.roles.length > 0){
                        config.dcmAcceptedUserRole.forEach(role=>{
                            if(this.mainservice.global.authentication.roles.indexOf(role) > -1){
                                oneAetRolesHasUser = true;
                            }
                        });
                    }
                    return oneAetRolesHasUser;
                });
                if(mode){
                    aetConfig = aetConfig.filter(config=>{
                        return !config.dcmuiMode || config.dcmuiMode === mode;
                    });
                }
                if(aetConfig.length > 0){
                    return aets.filter(aet=>{
                        return aetConfig[0].dcmuiAets.indexOf(aet.dicomAETitle) > -1;
                    });
                }else{
                    return [];
                }
            }catch(e){
                console.warn(e);
                return [];
            }
        }else{
            return aets;
        }
    }
}
