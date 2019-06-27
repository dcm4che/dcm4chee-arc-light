import {Component, OnInit, ViewContainerRef} from '@angular/core';
import {Http, Headers} from '@angular/http';
import * as _ from 'lodash';
import {ConfirmComponent} from '../../widgets/dialogs/confirm/confirm.component';
import {AppService} from '../../app.service';
import {MatDialog, MatDialogConfig, MatDialogRef} from '@angular/material';
import {DevicesService} from './devices.service';
import {HostListener} from '@angular/core';
import {CreateExporterComponent} from '../../widgets/dialogs/create-exporter/create-exporter.component';
import {Router} from '@angular/router';
import {WindowRefService} from "../../helpers/window-ref.service";
import {Hl7ApplicationsService} from "../hl7-applications/hl7-applications.service";
import {HttpErrorHandler} from "../../helpers/http-error-handler";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {DeviceConfiguratorService} from "../device-configurator/device-configurator.service";
import {LoadingBarService} from "@ngx-loading-bar/core";
import {Globalvar} from "../../constants/globalvar";
import {HttpHeaders} from "@angular/common/http";
import {KeycloakService} from "../../helpers/keycloak-service/keycloak.service";

@Component({
  selector: 'app-devices',
  templateUrl: './devices.component.html',
  styleUrls: ['./devices.component.css']
})
export class DevicesComponent implements OnInit{
    debugpre = false;
    _ = _;
    devices;
    advancedConfig = false;
    showDeviceList= true;
    devicefilter = '';
    filter = {
        dicomDeviceName: undefined,
        dicomDescription: undefined,
        dicomManufacturer: undefined,
        dicomManufacturerModelName: undefined,
        dicomSoftwareVersion: undefined,
        dicomStationName: undefined,
        dicomPrimaryDeviceType: undefined,
        dicomInstitutionName: undefined,
        dicomInstitutionDepartmentName: undefined,
        dicomInstalled: undefined
    };
    moreDevices = {
        limit: 30,
        start: 0,
        loaderActive: false
    };
    aes;
    dialogRef: MatDialogRef<any>;

    constructor(
        public $http:J4careHttpService,
        public cfpLoadingBar: LoadingBarService,
        public mainservice: AppService,
        public viewContainerRef: ViewContainerRef ,
        public dialog: MatDialog,
        public config: MatDialogConfig,
        public service: DevicesService,
        private router: Router,
        private hl7service:Hl7ApplicationsService,
        public httpErrorHandler:HttpErrorHandler,
        private deviceConfigurator:DeviceConfiguratorService
    ) {}
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
    init(){
        this.getDevices();
        this.getAes();
        this.getHl7ApplicationsList(2);
        console.log("deviceconfiguratorservice paginantion",this.deviceConfigurator.pagination)
        if(this.deviceConfigurator.pagination){
            this.deviceConfigurator.pagination = [
                {
                    url: '/device/devicelist',
                    title: 'devicelist',
                    devicereff: undefined
                }
            ];
        }
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
    editDevice(devicename){
        if (devicename && devicename != ''){
            this.router.navigateByUrl('/device/edit/' + devicename);
        }
    }
    loadMoreDevices(){
        this.moreDevices.loaderActive = true;
        this.moreDevices.limit += 20;
        // if(this.moreDevices.limit > 50){
            // this.moreAes.start +=20;
        // }
        this.moreDevices.loaderActive = false;
    }
    searchDevices(){
        this.cfpLoadingBar.start();
        let $this = this;
        let urlParam = this.mainservice.param(this.filter);
        // urlParam = urlParam.join("&");
        if (urlParam){
            urlParam = '?' + urlParam;
        }
        this.$http.get(
            '../devices' + urlParam
        )
            // .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res; }catch (e){ resjson = [];} return resjson;})
        .subscribe((response) => {
            $this.devices = response;
            $this.cfpLoadingBar.complete();
        }, function errorCallback(response) {
/*            $log.error("Error loading device names", response);
            vex.dialog.alert("Error loading device names, please reload the page and try again!");*/
        });
    };

    clearForm(){
        let $this = this;
        _.forEach($this.filter, (m, i) => {
            $this.filter[i] = '';
        });
        this.searchDevices();
    };
    scrollToDialog(){
        let counter = 0;
        let i = setInterval(function(){
            if (($('.md-overlay-pane').length > 0)) {
                clearInterval(i);
                $('html, body').animate({
                    scrollTop: ($('.md-overlay-pane').offset().top)
                }, 200);
            }
            if (counter > 200){
                clearInterval(i);
            }else{
                counter++;
            }
        }, 50);
    }
    confirm(confirmparameters){
        this.scrollToDialog();
        this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(ConfirmComponent, {
            height: 'auto',
            width: '500px'
        });
        this.dialogRef.componentInstance.parameters = confirmparameters;
        return this.dialogRef.afterClosed();
    };
    deleteDevice(device) {
        if (device && device.dicomDeviceName) {
            let $this = this;
            this.confirm({
                content: 'Are you sure you want to delete the device ' + device.dicomDeviceName + '?'
            }).subscribe(result => {
                if (result){
                    $this.cfpLoadingBar.start();
                    $this.$http.delete('../devices/' + device.dicomDeviceName).subscribe((res) => {
                        $this.mainservice.setMessage({
                            'title': 'Info',
                            'text': 'Device deleted successfully!',
                            'status': 'info'
                        });
                        $this.getDevices();
                        $this.cfpLoadingBar.complete();
                    }, (err) => {
                        $this.httpErrorHandler.handleError(err);
                        $this.cfpLoadingBar.complete();
                    });
                }
            });
        }
    };

    cloneDevice(devicename){
        let headers = new HttpHeaders({ 'Content-Type': 'application/json' });
        let deviceNameList = this.devices.map(res => {
            return res.dicomDeviceName;
        });
        console.log('deviceNameList', deviceNameList);
        let parameters: any = {
            content: 'Set the name for the new device to clone ' + devicename.dicomDeviceName,
            input: {
                name: 'newdevice',
                type: 'text'
            },
            result: {input: ''},
            saveButton: 'CLONE'
        };
        let $this = this;
        this.confirm(parameters).subscribe(result => {
            if (result){
                $this.cfpLoadingBar.start();
                console.log('result', result);
                console.log('param', parameters);
                console.log('devicename', devicename.dicomDeviceName);
                console.log('indexof', _.indexOf(deviceNameList, parameters.result.input));
                if (_.indexOf(deviceNameList, parameters.result.input) > -1){
                    $this.mainservice.setMessage({
                        'title': 'Error',
                        'text': 'This name already exists, please chose another one!',
                        'status': 'error'
                    });
                    $this.cfpLoadingBar.complete();
                }else{
                    $this.$http.get(
                        '../devices/' + devicename.dicomDeviceName
                    )
                    // .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res; }catch (e){ resjson = [];} return resjson;})
                    .subscribe(
                        (device) => {
                            console.log('response', device);
                            $this.service.changeAetOnClone(device,$this.aes);
                            $this.service.changeHl7ApplicationNameOnClone(device, $this.mainservice.global.hl7);
                            console.log('device afterchange', device);
                            device.dicomDeviceName = parameters.result.input;
                            this.service.createDevice(parameters.result.input, device)
                            // $this.$http.post('../devices/' + parameters.result.input, device, headers)
                                .subscribe(res => {
                                        console.log('res succes', res);
                                        $this.cfpLoadingBar.complete();
                                        $this.mainservice.setMessage({
                                            'title': 'Info',
                                            'text': 'Device cloned successfully!',
                                            'status': 'info'
                                        });
                                        $this.getDevices();
                                        $this.$http.get(
                                            '../aes'
                                            // './assets/dummydata/aes.json'
                                        )
                                            // .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res; }catch (e){ resjson = [];} return resjson;})
                                            .subscribe((response) => {
                                                $this.aes = response;
                                                if ($this.mainservice.global && !$this.mainservice.global.aes){
                                                    let global = _.cloneDeep($this.mainservice.global);
                                                    global.aes = response;
                                                    $this.mainservice.setGlobal(global);
                                                }else{
                                                    if ($this.mainservice.global && $this.mainservice.global.aes){
                                                        $this.mainservice.global.aes = response;
                                                    }else{
                                                        $this.mainservice.setGlobal({aes: response});
                                                    }
                                                }
                                            }, (response) => {
                                                // vex.dialog.alert("Error loading aes, please reload the page and try again!");
                                            });
                                    },
                                    err => {
                                        console.log('error');
                                        $this.cfpLoadingBar.complete();
                                        $this.httpErrorHandler.handleError(err);
                                    });
                        },
                        (err) => {
                            $this.httpErrorHandler.handleError(err);
                            $this.cfpLoadingBar.complete();
                        }
                    );
                }
            }
        });
    };
    createExporter(){
        this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(CreateExporterComponent, {
            height: 'auto',
            width: '90%'
        });
        let $this = this;
        this.dialogRef.componentInstance.devices = this.devices.filter((dev)=>{
            return (_.hasIn(dev,"hasArcDevExt") && dev.hasArcDevExt === true);
        });
        this.dialogRef.componentInstance.aes = this.aes;
        this.dialogRef.afterClosed().subscribe(re => {
            console.log('re', re);
            if (re && re.device && re.device.dicomDeviceName && re.exporter){
                let headers = new HttpHeaders({ 'Content-Type': 'application/json' });
                let i = 0;
                if(_.hasIn(re.device,Globalvar.EXPORTER_CONFIG_PATH)){
                    i = (<any>_.get(re.device,Globalvar.EXPORTER_CONFIG_PATH)).length;
                }
                this.deviceConfigurator.addChangesToDevice(re.exporter,`${Globalvar.EXPORTER_CONFIG_PATH}[${i}]`,re.device);
                $this.$http.put('../devices/' + re.device.dicomDeviceName,re.device, headers).subscribe(res => {
                    $this.mainservice.setMessage({
                        'title': 'Info',
                        'text': 'The new exporter description appended successfully to the device: ' + re.device.dicomDeviceName,
                        'status': 'info'
                    });
                    $this.$http.post('../ctrl/reload', {}, headers).subscribe((res) => {
                        $this.mainservice.setMessage({
                            'title': 'Info',
                            'text': 'Archive reloaded successfully!',
                            'status': 'info'
                        });
                    });
                }, (err) => {
                    $this.httpErrorHandler.handleError(err);
                });
            }
        });
    }
    getAes(){
        let $this = this;
        if ($this.mainservice.global && $this.mainservice.global.aes) {
            this.aes = this.mainservice.global.aes;
        }else{
            this.$http.get(
                '../aes'
                // './assets/dummydata/aes.json'
            )
                // .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res; }catch (e){ resjson = [];} return resjson;})
                .subscribe((response) => {
                    $this.aes = response;
                    if ($this.mainservice.global && !$this.mainservice.global.aes){
                        let global = _.cloneDeep($this.mainservice.global);
                        global.aes = response;
                        $this.mainservice.setGlobal(global);
                    }else{
                        if ($this.mainservice.global && $this.mainservice.global.aes){
                            $this.mainservice.global.aes = response;
                        }else{
                            $this.mainservice.setGlobal({aes: response});
                        }
                    }
                }, (response) => {
                    // vex.dialog.alert("Error loading aes, please reload the page and try again!");
                });
        }
    }
    createDevice(){
        this.router.navigateByUrl('/device/edit/[new_device]');
    }
    getDevices(){
        let $this = this;
        // if(this.mainservice.global && this.mainservice.global.devices){
        //     this.devices = this.mainservice.global.devices;
        // }else{
        this.service.getDevices().subscribe((response) => {
                    console.log('getdevices response', response);
                    console.log('global', $this.mainservice.global);
                    $this.devices = response;
                    if ($this.mainservice.global && !$this.mainservice.global.devices){
                        let global = _.cloneDeep($this.mainservice.global); //,...[{devices:response}]];
                        global.devices = response;
                        $this.mainservice.setGlobal(global);
                    }else{
                        if ($this.mainservice.global && $this.mainservice.global.devices){
                            $this.mainservice.global.devices = response;
                        }else{
                            $this.mainservice.setGlobal({devices: response});
                        }
                    }
                }, (err) => {
                    // vex.dialog.alert("Error loading device names, please reload the page and try again!");
                });
        // }
    };
    getHl7ApplicationsList(retries){
        let $this = this;
        this.hl7service.getHl7ApplicationsList('').subscribe(
            (response)=>{
                if ($this.mainservice.global && !$this.mainservice.global.hl7){
                    let global = _.cloneDeep($this.mainservice.global); //,...[{hl7:response}]];
                    global.hl7 = response;
                    $this.mainservice.setGlobal(global);
                }else{
                    if ($this.mainservice.global && $this.mainservice.global.hl7){
                        $this.mainservice.global.hl7 = response;
                    }else{
                        $this.mainservice.setGlobal({hl7: response});
                    }
                }
            },
            (err)=>{
                if(retries){
                    $this.getHl7ApplicationsList(retries - 1);
                }
            }
        );
    }


}
