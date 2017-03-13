import { Component, OnInit } from '@angular/core';
import {Http} from "@angular/http";
import {QueuesService} from "./queues.service";
import {map} from "rxjs/operator/map";
import {AppService} from "../app.service";
import {User} from "../models/user";

@Component({
  selector: 'app-queues',
  templateUrl: './queues.component.html',
  styleUrls: ['./queues.component.css']
})
export class QueuesComponent {
    matches = [];
    limit = 20;
    queues = [];
    queueName = null;
    status = "*";
    before = new Date();
    isRole:any;
    user:User;
    constructor(public $http: Http, public service:QueuesService,public mainservice:AppService) {
        this.init();
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
            // console.log("isroletest",this.user.applyisRole("admin"));
        }
    }

    search(offset) {
        let $this = this;
        this.service.search(this.queueName, this.status, offset, this.limit)
            .map(res => res.json())
            .subscribe((res) => {
                $this.matches = res.map((properties, index) => {
                    return {
                        offset: offset + index,
                        properties: properties,
                        showProperties: false
                    };
                });
            });
    };
    cancel(match) {
        this.service.cancel(this.queueName, match.properties.id)
            .subscribe(function (res) {
                match.properties.status = 'CANCELED';
            });
    };
    reschedule(match) {
        let $this = this;
        this.service.reschedule(this.queueName, match.properties.id)
            .subscribe((res) => {
                $this.search(0);
            });
    };
    delete(match) {
        let $this = this;
        this.service.delete(this.queueName, match.properties.id)
            .subscribe((res) => {
                $this.search($this.matches[0].offset);
            });
    };
    flushBefore() {
        let $this = this;
        this.service.flush(this.queueName, this.status, this.before)
            .subscribe((res) => {
                $this.search(0);
            });
    };
    hasOlder(objs) {
        return objs && (objs.length === this.limit);
    };
    hasNewer(objs) {
        return objs && objs.length && objs[0].offset;
    };
    newerOffset(objs) {
        return Math.max(0, objs[0].offset - this.limit);
    };
    olderOffset(objs) {
        return objs[0].offset + this.limit;
    };

    init() {
        let $this = this;
        this.$http.get("../queue")
            .map(res => res.json())
            .subscribe((res) => {
            $this.queues = res;
            $this.queueName = res[0].name;
        })
    }
}
