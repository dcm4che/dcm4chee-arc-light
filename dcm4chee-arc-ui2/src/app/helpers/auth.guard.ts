import { Injectable } from '@angular/core';
import {ActivatedRouteSnapshot, CanActivate, RouterStateSnapshot} from '@angular/router';
import {Globalvar} from "../constants/globalvar";
import {Observable} from "rxjs/Observable";
import "rxjs/add/observable/of";
import {StudiesService} from "../studies/studies.service";
import {Subscription} from "rxjs/Subscription";


@Injectable()
export class AuthGuard implements CanActivate {

    constructor(private studiesService:StudiesService) {}

    canActivate(route : ActivatedRouteSnapshot, state : RouterStateSnapshot){

        // return this.studiesService.getPermission(state.url);
        return true;
    }
}