import { Component } from '@angular/core';
import {Http} from "@angular/http";
import 'rxjs/add/operator/map';
import 'rxjs/add/operator/catch';
import * as FileSaver from "file-saver";
import {AppService} from "../app.service";
import {SlimLoadingBarService} from "ng2-slim-loading-bar";
import {MessagingComponent} from "../widgets/messaging/messaging.component";

@Component({
    selector: 'app-control',
    templateUrl: './control.component.html',
    styleUrls: ['./control.component.css']
})
export class ControlComponent {
    status:any;
    message = '';
    constructor(public $http: Http, public appservices:AppService, private cfpLoadingBar:SlimLoadingBarService,public messaging:MessagingComponent) {
        this.fetchStatus();
        this.cfpLoadingBar.interval = 200;
    }

    // reverse = false;
    fetchStatus() {
        let $this = this;
        this.$http.get("/dcm4chee-arc/ctrl/status")
            .map(response => response.json())
            .subscribe( (res) => {
                $this.status = res["status"];
                $this.message = '';
                $this.appservices.setMessage({
                    "title": "Info",
                    "text":"Status:" +$this.status,
                    "status":'info'
                });
            });
    };
    start(){
        let $this = this;
        this.$http.post("/dcm4chee-arc/ctrl/start",{}).subscribe((res) => {
            $this.status = 'STARTED';
            $this.message = '';
            $this.appservices.setMessage({
                "title": "Info",
                "text":"Status:" +$this.status,
                "status":'info'
            });
        });
    };
    stop() {
        console.log("stop");
        let $this = this;
        this.$http.post("/dcm4chee-arc/ctrl/stop",{}).subscribe((res) => {
            $this.status = 'STOPPED';
            $this.message = '';
            $this.appservices.setMessage({
                "title": "Info",
                "text":"Status:" +$this.status,
                "status":'info'
            });
        });
    };
    reload() {
        let $this = this;
        this.$http.post("/dcm4chee-arc/ctrl/reload",{}).subscribe((res) => {
            console.log("res",res);
                // $this.message = 'Reload successful';
            $this.appservices.setMessage({
                "title": "Info",
                "text": "Reload successful",
                "status":'info'
            });
        });
    };

}
