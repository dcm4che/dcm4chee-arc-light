import { Injectable } from '@angular/core';
import {ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot} from '@angular/router';
import {Globalvar} from "../../constants/globalvar";
import {Observable} from "rxjs/Observable";
import "rxjs/add/observable/of";
import {Subscription} from "rxjs/Subscription";
import {PermissionService} from "./permission.service";
import {AppService} from "../../app.service";

// let keycloak: any;

@Injectable()
export class AuthGuard implements CanActivate {

    constructor(
        private permissionService:PermissionService,
        private appservice:AppService,
        private router: Router,
    ) {}

    canActivate(route : ActivatedRouteSnapshot, state : RouterStateSnapshot){
        if(this.appservice.global && this.appservice.global.notSecure){
            return true;
        }else{
            let check = this.permissionService.getPermission(state.url);
            if(!check){
                this.router.navigateByUrl('/permission-denied');
                this.appservice.setMessage({
                    'title': 'Permission denied',
                    'text': `You don\'t have permission to access ${state.url}`,
                    'status': 'error'
                });
            }
            if(check && check.redirect)
                this.router.navigateByUrl(check.redirect);
            return check;
        }
    }
}