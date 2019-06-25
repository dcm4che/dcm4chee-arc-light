import { Component, OnInit } from '@angular/core';
import {Hl7ApplicationsService} from "./hl7-applications.service";
import {AppService} from "../../app.service";
import {HostListener} from "@angular/core";
import * as _ from 'lodash';
import {Router} from "@angular/router";
import {HttpErrorHandler} from "../../helpers/http-error-handler";
import {KeycloakService} from "../../helpers/keycloak-service/keycloak.service";

@Component({
  selector: 'app-hl7-applications',
  templateUrl: './hl7-applications.component.html'
})
export class Hl7ApplicationsComponent implements OnInit {

    hl7Applications;
    moreHl7 = {
        limit: 30,
        start: 0,
        loaderActive: false
    };
    advancedConfig = false;
    filter = {
        dicomDeviceName: undefined,
        hl7ApplicationName: undefined,
        dicomApplicationCluster: undefined,
    };
    devicefilter = '';
    urlParam = "";
    constructor(
        private service:Hl7ApplicationsService,
        private mainservice:AppService,
        private router: Router,
        private httpErrorHandler:HttpErrorHandler
    ) { }
    ngOnInit(){
        this.initCheck(10);
    }
    initCheck(retries){
        let $this = this;
        if((KeycloakService.keycloakAuth && KeycloakService.keycloakAuth.authenticated) || (_.hasIn(this.mainservice,"global.notSecure") && this.mainservice.global.notSecure)){
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
    init() {
        this.getHl7ApplicationsList(2);
    }

    @HostListener('window:scroll', ['$event'])
    loadMoreDeviceOnScroll(event) {
        let hT = ($('.load_more').offset()) ? $('.load_more').offset().top : 0,
            hH = $('.load_more').outerHeight(),
            wH = $(window).height(),
            wS = window.pageYOffset;
        if (wS > (hT + hH - wH)){
            this.loadMoreDevices();
        }
    }
    loadMoreDevices(){
        this.moreHl7.loaderActive = true;
        this.moreHl7.limit += 20;
        this.moreHl7.loaderActive = false;
    }
    searchHl7Applications(){
        this.urlParam = this.mainservice.param(this.filter);
        // urlParam = urlParam.join("&");
        if (this.urlParam){
            this.urlParam = '?' + this.urlParam;
        }else{
            this.urlParam = "";
        }
        this.getHl7ApplicationsList(0);
    }
    clearForm(){
        let $this = this;
        _.forEach($this.filter, (m, i) => {
            $this.filter[i] = '';
        });
    };
    editDevice(devicename){
        if (devicename && devicename != ''){
            this.router.navigateByUrl('/device/edit/' + devicename + '/dcmDevice/properties.dcmDevice');
        }
    }
    getHl7ApplicationsList(retries){
        let $this = this;
        this.service.getHl7ApplicationsList(this.urlParam).subscribe(
            (res)=>{
                $this.hl7Applications = res;
                console.log("res",res);
            },
            (err)=>{
                if(retries){
                    $this.getHl7ApplicationsList(retries - 1);
                }else{
                    $this.httpErrorHandler.handleError(err);
                }
            }
        );
    }

}
