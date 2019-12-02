import {Component, OnInit} from '@angular/core';
import 'rxjs/add/operator/map';
import 'rxjs/add/operator/catch';
import {ControlService} from './control.service';
import * as _ from 'lodash';
import {LoadingBarService} from "@ngx-loading-bar/core";
import {AppService} from "../../app.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {WindowRefService} from "../../helpers/window-ref.service";
import "rxjs/add/observable/forkJoin";
import {Observable} from "rxjs/Observable";
import {DevicesService} from "../devices/devices.service";
import {HttpErrorHandler} from "../../helpers/http-error-handler";
import {KeycloakService} from "../../helpers/keycloak-service/keycloak.service";

@Component({
    selector: 'app-control',
    templateUrl: './control.component.html',
    styleUrls: ['./control.component.css']
})
export class ControlComponent implements OnInit{
    status: any;
    message = '';
    devices = {};
    allDevices;
    Object = Object;
    tableSchema;
    constructor(
        public $http:J4careHttpService,
        public appservices: AppService,
        private cfpLoadingBar: LoadingBarService,
        private service: ControlService,
        private devicesService:DevicesService,
        public httpErrorHandler:HttpErrorHandler
    ) {}
    ngOnInit(){
        this.initCheck(10);
    }
    initCheck(retries){
        let $this = this;
        if((KeycloakService.keycloakAuth && KeycloakService.keycloakAuth.authenticated) || (_.hasIn(this.appservices,"global.notSecure") && this.appservices.global.notSecure)){
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
        this.getDevices();
        this.tableSchema = this.service.getTableSchema();
        this.calculateWidthOfTable("tableSchema");
    }
    fetchStatus(d?) {
        Object.keys(this.devices).forEach((device)=>{
            this.service.fetchStatus(this.devices[device].dcmuiDeviceURL).subscribe(res=>{
                this.devices[device].status = res.status;
                this.appservices.setMessage({
                    'title': 'Info',
                    'text': `Status of ${this.devices[device].dcmuiDeviceURLName} was successfully refetched!`,
                    'status': 'info'
                });
            },err=>{
                console.error("Status not fetchable",err);
                this.httpErrorHandler.handleError(err);
            })
        });
    };
    start(object){
        this.cfpLoadingBar.start();
        this.service.startArchive(object.dcmuiDeviceURL).subscribe((res) => {
            this.fetchStatus();
            this.appservices.setMessage({
                'title': 'Info',
                'text': `Archive ${object.dcmuiDeviceURLName} started successfully`,
                'status': 'info'
            });
            this.cfpLoadingBar.complete();
        },(err)=>{
            this.cfpLoadingBar.complete();
            this.httpErrorHandler.handleError(err);
        });
    };
    stop(object) {
        this.cfpLoadingBar.start();
        this.service.stopArchive(object.dcmuiDeviceURL).subscribe((res) => {
            this.fetchStatus();
            this.appservices.setMessage({
                'title': 'Info',
                'text': `Archive ${object.dcmuiDeviceURLName} stoped successfully`,
                'status': 'info'
            });
            this.cfpLoadingBar.complete();
        },(err)=>{
            this.cfpLoadingBar.complete();
            this.httpErrorHandler.handleError(err);
        });
    };
    reload(object) {
        this.cfpLoadingBar.start();
        this.service.reloadArchive().subscribe((res) => {
            this.appservices.setMessage({
                'title': 'Info',
                'text': `Archive ${object.dcmuiDeviceURLName} reloaded successfully`,
                'status': 'info'
            });
            this.cfpLoadingBar.complete();
        },(err)=>{
            this.cfpLoadingBar.complete();
            this.httpErrorHandler.handleError(err);
        });
    };
    toggleState(object){
        if(object.status && object.status === "STARTED"){
            this.stop(object);
        }else{
            this.start(object);
        }
    }

    getDevices(){
        this.devicesService.getDevices().subscribe((devices)=>{
            this.service.getMyArchivesFromConfig(this, devices,(devices)=>{
                this.devices = devices;
                this.fetchStatus();
            });
        },(err)=>{
            this.httpErrorHandler.handleError(err);
        });
    }
    calculateWidthOfTable(tableName){
        let summ = 0;
        _.forEach(this[tableName],(m,i)=>{
            summ += m.widthWeight;
        });
        _.forEach(this[tableName],(m,i)=>{
            m.calculatedWidth =  ((m.widthWeight * 100)/summ)+"%";
        });
    };
}
