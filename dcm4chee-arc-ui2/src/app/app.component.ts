import {Component, ViewContainerRef, Output} from '@angular/core';
import {MdDialog, MdDialogRef, MdDialogConfig} from "@angular/material";
import {MessagingComponent} from "./widgets/messaging/messaging.component";
import {AppService} from "./app.service";
import {ViewChild} from "@angular/core/src/metadata/di";
import {User} from "./models/user";
import 'rxjs/add/operator/catch';
import {Http} from "@angular/http";
// import {DCM4CHE} from "./constants/dcm4-che";
// declare var $:JQueryStatic;
// import * as vex from "vex-js";
// declare var vex: any;
// const vex = require("vex-js");
declare var DCM4CHE: any;
@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
    progressValue = 30;
    //Detect witch header should be shown.
    user:any = {};
    dialogRef: MdDialogRef<any>;
    showUserMenu:boolean = false;
    url = "/auth";
    logoutUrl = '';
    isRole:any;
    @ViewChild(MessagingComponent) msg;
    // vex["defaultOptions"]["className"] = 'vex-theme-os';
    constructor( public viewContainerRef: ViewContainerRef, public dialog: MdDialog, public config: MdDialogConfig, public messaging:MessagingComponent,public mainservice:AppService,public $http:Http){
        let $this = this;
        if(!this.mainservice.user){
            this.mainservice.user = this.mainservice.getUserInfo().share();
            this.mainservice.user
                .subscribe(
                    (response) => {
                        console.log("in userauth response",response);
                        $this.mainservice.user.user = response.user;
                        $this.mainservice.user.roles = response.roles;
                        $this.mainservice.isRole = function(role){
                            if(response.user === null && response.roles.length === 0){
                                return true;
                            }else{
                                if(response.roles && response.roles.indexOf(role) > -1){
                                    return true;
                                }else{
                                    return false;
                                }
                            }
                        };
                        $this.user = $this.mainservice.user;
                        $this.isRole = $this.mainservice.isRole;
                    },
                    (response) => {
                        // this.user = this.user || {};
                        console.log("in user auth errorespons",response);
                        $this.mainservice.user.user = "user";
                        $this.mainservice.user.roles = ["user","admin"];
                        $this.mainservice.isRole = (role)=>{
                            if(role === "admin"){
                                return false;
                            }else{
                                return true;
                            }
                        };
                        $this.isRole = $this.mainservice.isRole;
                    }
                );
        }


        this.$http.get('../auth')
            .map(res => res.json())
            .subscribe(
            (response) => {
            $this.url  = response.url;
            var host    = location.protocol + "//" + location.host

            $this.logoutUrl = response.url + "/realms/dcm4che/protocol/openid-connect/logout?redirect_uri="
                + encodeURIComponent(host + location.pathname);
        }, (response) => {
            // vex.dialog.alert("Error loading device names, please reload the page and try again!");
            $this.url = "/auth";
            let host = location.protocol + "//" + location.host
            $this.logoutUrl =  host + "/auth/realms/dcm4che/protocol/openid-connect/logout?redirect_uri="
                + encodeURIComponent(host + location.pathname);
        });


    }

    progress(){
        let changeTo = function (t) {
            console.log("t",t);
            this.progressValue = t;
        }
        // let getValue = function(){
        //   return this.value;
        // }
        // let changeTo =  function(d){
        //     this.value = d;
        // }
        // let getVal = function () {
        //     return this.value;
        // }
        return{
            getValue:this.progressValue,
            setValue:(v)=>{
                this.progressValue = v;
            }
        }
    };
    createPatient(){
        this.mainservice.createPatient({});
    }

    onClick() {
        // this.dcm4che.elementName.forTag()
        console.log("dcm4chetest",DCM4CHE.elementName.forTag("00000000"));

        this.msg.setMsg({
            "title": "Warning",
            "text": "Attribute already exists!",
            "status": "warning",
            "timeout":50000
        });
        setTimeout(()=>{
            this.msg.setMsg({
                "title": "Info ",
                "text": "Info message!",
                "status": "info"
            });
        },500);
        // this.messaging.showMessageBlock = true;
        // this.messaging.change.emit(new MessagingComponent(true, "testmsg"));
        // this.messaging.showMessageBlock.emit(false);
        // console.log("showmessging=",this.messaging.showMessageBlock);
    }



    sidenavopen = false;
}


