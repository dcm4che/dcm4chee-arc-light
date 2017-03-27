import { Component, OnInit } from '@angular/core';
import {SlimLoadingBarService} from "ng2-slim-loading-bar";
import {User} from "../models/user";
import {Http} from "@angular/http";
import {AppService} from "../app.service";
import {MonitoringService} from "./monitoring.service";

@Component({
  selector: 'app-monitoring',
  templateUrl: './monitoring.component.html'
})
export class MonitoringComponent implements OnInit {
    matches = [];
    user:User;
    exportTasks = [];
    filters = {
        ExporterID:undefined,
        offset:undefined,
        limit:20,
        status:"*",
        updatedBefore:undefined,
        dicomDeviceName:undefined
    };
    isRole:any;
    constructor(public $http: Http, public cfpLoadingBar:SlimLoadingBarService, public mainservice:AppService,public  service:MonitoringService) {
        // this.init();
        let $this = this;
        if(!this.mainservice.user){
            // console.log("in if studies ajax");
            this.mainservice.user = this.mainservice.getUserInfo().share();
            this.mainservice.user
                .subscribe(
                    (response) => {
                        $this.user.user  = response.user;
                        $this.mainservice.user.user = response.user;
                        $this.user.roles = response.roles;
                        $this.mainservice.user.roles = response.roles;
                        $this.isRole = (role)=>{
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
                    },
                    (response) => {
                        // $this.user = $this.user || {};
                        console.log("get user error");
                        $this.user.user = "user";
                        $this.mainservice.user.user = "user";
                        $this.user.roles = ["user","admin"];
                        $this.mainservice.user.roles = ["user","admin"];
                        $this.isRole = (role)=>{
                            if(role === "admin"){
                                return false;
                            }else{
                                return true;
                            }
                        };
                    }
                );

        }else{
            this.user = this.mainservice.user;
            this.isRole = this.mainservice.isRole;
        }
    }
    search(offset) {
        let $this = this;
        $this.cfpLoadingBar.start();
        this.service.search(this.filters, offset)
            .map(res => res.json())
            .subscribe((res) => {
                console.log("res2",res);
                console.log("res",res.length);
                if(res && res.length > 0){
                    $this.matches = res.map((properties, index) => {
                        $this.cfpLoadingBar.complete();
                        return {
                            offset: offset + index,
                            properties: properties,
                            showProperties: false
                        };
                    });
                }else{
                    $this.cfpLoadingBar.complete();
                    $this.matches = [];
                    $this.mainservice.setMessage({
                        "title": "Info",
                        "text": "No queues found!",
                        "status":'info'
                    });
                }
            }, (err) =>{
                $this.cfpLoadingBar.complete();
                $this.matches = [];
                console.log("err",err);
            });
    };
    ngOnInit() {
    }
    hasOlder(objs) {
        return objs && (objs.length === this.filters.limit);
    };
    hasNewer(objs) {
        return objs && objs.length && objs[0].offset;
    };
    newerOffset(objs) {
        return Math.max(0, objs[0].offset - this.filters.limit);
    };
    olderOffset(objs) {
        return objs[0].offset + this.filters.limit;
    };

/*    init() {
        let $this = this;
        $this.cfpLoadingBar.start();
        this.$http.get("../monitor/export")
            .map(res => res.json())
            .subscribe((res) => {
                $this.exportTasks = res;
                // $this.queueName = res[0].name;
                $this.cfpLoadingBar.complete();
            })
    }*/
}
