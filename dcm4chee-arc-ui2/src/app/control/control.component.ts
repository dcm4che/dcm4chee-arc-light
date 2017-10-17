import {Component, OnInit} from '@angular/core';
import 'rxjs/add/operator/map';
import 'rxjs/add/operator/catch';
import {AppService} from '../app.service';
import {SlimLoadingBarService} from 'ng2-slim-loading-bar';
import {ControlService} from './control.service';
import {WindowRefService} from "../helpers/window-ref.service";
import {J4careHttpService} from "../helpers/j4care-http.service";
import * as _ from 'lodash';

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
        private cfpLoadingBar: SlimLoadingBarService,
        private service: ControlService
    ) {}
    ngOnInit(){
        this.initCheck(10);
    }
    initCheck(retries){
        let $this = this;
        if(_.hasIn(this.appservices,"global.authentication")){
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
        this.cfpLoadingBar.interval = 200;
    }
    // reverse = false;
    fetchStatus() {
        let $this = this;
        this.$http.get('/dcm4chee-arc/ctrl/status')
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
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
