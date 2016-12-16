import {Injectable, OnInit} from '@angular/core';
import {MessagingComponent} from "./widgets/messaging/messaging.component";
import {Http} from "@angular/http";
import {Observer, Observable} from "rxjs";
import {User} from "./models/user";
import * as _ from "lodash";

@Injectable()
export class AppService implements OnInit{
    private _user:User;

    constructor(public $http:Http) {
    }
    private _isRole = function(role){
        if(this.user){
            if(this.user.user === null && this.user.roles.length === 0){
                return true;
            }else{
                if(this.user.roles && this.user.roles.indexOf(role) > -1){
                    return true;
                }else{
                    return false;
                }
            }
        }else{
            if(role === "admin"){
                return false;
            }else{
                return true;
            }
        }
    };

    getUserInfo():Observable<User>{
        return this.$http.get("/dcm4chee-arc/ui/rs/realm")
            .map((response) => response.json());
    }
    get user(): any {
        console.log("ingetuser");
        return this._user;
    }

    set user(value: any) {
        console.log("user set",value);
        this._user = value;
    }

    get isRole(): (role)=>boolean {
        return this._isRole;
    }

    set isRole(value: (role)=>boolean) {
        this._isRole = value;
    }

    ngOnInit(): void {
        console.log("in appservice on init before hhtp");
    }
    // getUserObservable():Observable<User>{
    //     return Observable.create(()=>{
    //         return this._user;
    //     })
    // }
    // setMessag(show, msg){
    //     this.messaging.showMessageBlock = show;
    //     this.messaging.msg = msg;
    // }


}
