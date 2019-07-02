import {Component, OnInit} from '@angular/core';
import 'rxjs/add/operator/map';
import 'rxjs/add/operator/catch';
import {ControlService} from './control.service';
import * as _ from 'lodash';
import {LoadingBarService} from "@ngx-loading-bar/core";
import {AppService} from "../../app.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {WindowRefService} from "../../helpers/window-ref.service";
import {KeycloakService} from "../../helpers/keycloak-service/keycloak.service";

@Component({
    selector: 'app-control',
    templateUrl: './control.component.html',
    styleUrls: ['./control.component.css']
})
export class ControlComponent implements OnInit{
    status: any;
    message = '';
    constructor(
        public $http:J4careHttpService,
        public appservices: AppService,
        private cfpLoadingBar: LoadingBarService,
        private service: ControlService
    ) {}
    ngOnInit(){
        this.initCheck(10);
    }
    initCheck(retries){
        let $this = this;
        if(KeycloakService.keycloakAuth.authenticated || (_.hasIn(this.appservices,"global.notSecure") && this.appservices.global.notSecure)){
            this.init();
        }else{
            if (retries){
                setTimeout(()=>{
                    $this.initCheck(retries-1);
                },20);
            }else{
                this.init();
            }
        }
    }
    init(){
        this.fetchStatus();
    }
    // reverse = false;
    fetchStatus() {
        let $this = this;
        this.$http.get('/dcm4chee-arc/ctrl/status')
            // .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res; }catch (e){ resjson = [];} return resjson;})
            .subscribe( (res) => {
                $this.status = res['status'];
                $this.message = '';
                $this.appservices.setMessage({
                    'title': 'Info',
                    'text': 'Status:' + $this.status,
                    'status': 'info'
                });
            });
    };
    start(){
        let $this = this;
        this.$http.post('/dcm4chee-arc/ctrl/start', {}).subscribe((res) => {
            $this.status = 'STARTED';
            $this.message = '';
            $this.appservices.setMessage({
                'title': 'Info',
                'text': 'Status:' + $this.status,
                'status': 'info'
            });
        });
    };
    stop() {
        console.log('stop');
        let $this = this;
        this.$http.post('/dcm4chee-arc/ctrl/stop', {}).subscribe((res) => {
            $this.status = 'STOPPED';
            $this.message = '';
            $this.appservices.setMessage({
                'title': 'Info',
                'text': 'Status:' + $this.status,
                'status': 'info'
            });
        });
    };
    reload() {
        let $this = this;
        this.service.reloadArchive().subscribe((res) => {
            console.log('res', res);
                // $this.message = 'Reload successful';
            $this.appservices.setMessage({
                'title': 'Info',
                'text': 'Reload successful',
                'status': 'info'
            });
        });
    };

}
