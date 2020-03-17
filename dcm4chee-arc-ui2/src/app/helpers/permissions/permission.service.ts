import { Injectable } from '@angular/core';
import {j4care} from "../j4care.service";
import {Globalvar} from "../../constants/globalvar";
import {J4careHttpService} from "../j4care-http.service";
import {AppService} from "../../app.service";
import {Route, Router} from "@angular/router";
import * as _ from 'lodash';
import {KeycloakService} from "../keycloak-service/keycloak.service";
import {User} from "../../models/user";
import UserInfo = KeycloakModule.UserInfo;
import {map, switchMap} from "rxjs/operators";

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
                    // this.mainservice.setSecured(false);
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
            return this.$http.get('../devicename')
                .pipe(
                    switchMap((res:any) => {
                        deviceName = (res.UIConfigurationDeviceName || res.dicomDeviceName);
                        archiveDeviceName = res.dicomDeviceName;
                        return this.$http.get('../devices/' + deviceName)
                    }),
                    map((res:any)=>{
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
                    })
                );
        else
            return response.call(this);
    }
    getConfigWithUser(response){
        let deviceName;
        let archiveDeviceName;
        let userInfo:UserInfo;
        if(!this.uiConfig && !this.configChecked){
            return this._keycloakService.getUserInfo().pipe(
                map(user=>{
                    userInfo = user; //Extracting userInfo from KeyCloak
                }),
                switchMap(res => this.$http.get('../devicename')),
                map(deviceNameResponse=>{
                    if(userInfo){
                        const roles:Array<string> = _.get(userInfo,"tokenParsed.realm_access.roles");
                        let user = new User({
                            authServerUrl:userInfo.authServerUrl,
                            realm:userInfo.realm,
                            user:_.get(userInfo,"userProfile.username"),
                            roles:roles,
                            su:(_.hasIn(deviceNameResponse,"super-user-role") && roles.indexOf(_.get(deviceNameResponse,"super-user-role")) > -1)
                        });
                        console.log("......user about to set",user);
                        this.mainservice.setUser(user);
                        this.user = user;
                    }
                    return deviceNameResponse;
                }),
                switchMap((res:any) => {
                    deviceName = (res.UIConfigurationDeviceName || res.dicomDeviceName);
                    archiveDeviceName = res.dicomDeviceName;
                    return this.$http.get('../devices/' + deviceName);
                }),
                map((res:any)=>{
                    try{
                        this.configChecked = true;
                        this.uiConfig = res.dcmDevice.dcmuiConfig["0"];
                        let global = _.cloneDeep(this.mainservice.global) || {};
                        global["uiConfig"] = res.dcmDevice.dcmuiConfig["0"];
                        global["myDevice"] = res;
                        this.mainservice.archiveDeviceName = archiveDeviceName;
                        this.mainservice.deviceName = deviceName;
                        this.mainservice.setGlobal(global);
                    }catch(e){
                        console.warn("Permission not found!",e);
                        if(this.mainservice.global.notSecure || (this.mainservice.user && !this.mainservice.user.user && this.mainservice.user.roles && this.mainservice.user.roles.length === 0)){
                            this.mainservice.global.notSecure = true;
                            // this.mainservice.setSecured(false)
                        }else
                            this.mainservice.showError($localize `:@@permission_not_found:Permission not found!`);
                        return response.apply(this,[]);
                    }
                    // return this.checkMenuTabAccess(url);
                    return response.apply(this,[]);
                })
            )
        }else{
            return response.apply(this,[]);
        }
    }

    checkMenuTabAccess(url){
        if(_.hasIn(this.mainservice,"global.notSecure") && this.mainservice.global.notSecure){
            return true;
        }else{
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
                this.mainservice.showError($localize `:@@are_permission_configured:Are you sure you configured the permissions?`)
            }
        }
    }
    checkVisibility(permissionObject){
        if(this.mainservice.user && this.mainservice.user.roles && this.mainservice.user.roles.length > 0 && this.mainservice.user.su)
            return true;
        else
            if((this.mainservice.user && !this.mainservice.user.user && this.mainservice.user.roles && this.mainservice.user.roles.length === 0) || (this.mainservice.global && this.mainservice.global.notSecure))
                return true; //not secured
            else{
                if(permissionObject){
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
                }else{
                    return true;
                }
            }
    }
    comparePermissionObjectWithRoles(object){
        if(this.mainservice.global && this.mainservice.global.notSecure){
            return true;
        }else{
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
    }
    filterAetDependingOnUiConfig(aets, mode){
        if(this.uiConfig && this.uiConfig.dcmuiAetConfig){
            try{
                let aetConfig = this.uiConfig.dcmuiAetConfig.filter(config=>{
                    let oneAetRolesHasUser = false;
                    if(_.hasIn(config, "dcmAcceptedUserRole") && config.dcmAcceptedUserRole && _.hasIn(this.mainservice,"user.roles") && this.mainservice.user.roles.length > 0){
                        config.dcmAcceptedUserRole.forEach(role=>{
                            if(this.mainservice.user.roles.indexOf(role) > -1){
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
