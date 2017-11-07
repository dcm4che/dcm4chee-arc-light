import {Component, OnInit, ViewContainerRef} from '@angular/core';
import {User} from '../../models/user';
import {MdDialogRef, MdDialog, MdDialogConfig} from '@angular/material';
import {Http} from '@angular/http';
import {SlimLoadingBarService} from 'ng2-slim-loading-bar';
import {AppService} from '../../app.service';
import * as _ from 'lodash';
import {ConfirmComponent} from '../../widgets/dialogs/confirm/confirm.component';
import {StorageSystemsService} from './storage-systems.service';
import {WindowRefService} from "../../helpers/window-ref.service";
import {HttpErrorHandler} from "../../helpers/http-error-handler";
import {J4careHttpService} from "../../helpers/j4care-http.service";

@Component({
  selector: 'app-storage-systems',
  templateUrl: './storage-systems.component.html'
})
export class StorageSystemsComponent implements OnInit {
    matches = [];
    user: User;
    exporterID;
    exportTasks = [];
    filters = {
        offset: undefined,
        uriScheme: '',
        dicomAETitle: '',
        usage: '',
        usableSpaceBelow: undefined
    };
    isRole: any;
    dialogRef: MdDialogRef<any>;
    _ = _;
    aets;
    usableSpaceBelow;
    usableSpaceBelowMode = "GB";

    constructor(
        public $http:J4careHttpService,
        public cfpLoadingBar: SlimLoadingBarService,
        public mainservice: AppService,
        public  service: StorageSystemsService,
        public viewContainerRef: ViewContainerRef,
        public dialog: MdDialog,
        public config: MdDialogConfig,
        public httpErrorHandler:HttpErrorHandler
    ){}
    ngOnInit(){
        this.initCheck(10);
    }
    initCheck(retries){
        let $this = this;
        if(_.hasIn(this.mainservice,"global.authentication") || (_.hasIn(this.mainservice,"global.notSecure") && this.mainservice.global.notSecure)){
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
        // this.initExporters(1);
        // this.init();
        this.getAets();
        let $this = this;
        if (!this.mainservice.user){
            // console.log("in if studies ajax");
            this.mainservice.user = this.mainservice.getUserInfo().share();
            this.mainservice.user
                .subscribe(
                    (response) => {
                        $this.user.user  = response.user;
                        $this.mainservice.user.user = response.user;
                        $this.user.roles = response.roles;
                        $this.mainservice.user.roles = response.roles;
                        $this.isRole = (role) => {
                            if (response.user === null && response.roles.length === 0){
                                return true;
                            }else{
                                if (response.roles && response.roles.indexOf(role) > -1){
                                    return true;
                                }else{
                                    return false;
                                }
                            }
                        };
                    },
                    (response) => {
                        // $this.user = $this.user || {};
                        console.log('get user error');
                        $this.user.user = 'user';
                        $this.mainservice.user.user = 'user';
                        $this.user.roles = ['user', 'admin'];
                        $this.mainservice.user.roles = ['user', 'admin'];
                        $this.isRole = (role) => {
                            if (role === 'admin'){
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
    };
    filterKeyUp(e){
        let code = (e.keyCode ? e.keyCode : e.which);
        if (code === 13){
            this.search(0);
        }
    };
    confirm(confirmparameters){
        this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(ConfirmComponent, this.config);
        this.dialogRef.componentInstance.parameters = confirmparameters;
        return this.dialogRef.afterClosed();
    };
    calculateUsableSpaceBelowFilter(){
        console.log("sl",this.usableSpaceBelow);
        console.log("sl",this.usableSpaceBelowMode);
        console.log("sl",this.filters.usableSpaceBelow);
        switch(this.usableSpaceBelowMode) {
            case "TB":
                this.filters.usableSpaceBelow = this.usableSpaceBelow * 1000000000000;
                break;
            case "GB":
                this.filters.usableSpaceBelow = this.usableSpaceBelow * 1000000000;
                break;
            case "MB":
                this.filters.usableSpaceBelow = this.usableSpaceBelow * 1000000;
                break;
            default:
                this.filters.usableSpaceBelow = this.usableSpaceBelow;
        }
        console.log("sl",this.filters.usableSpaceBelow);
    }
    search(offset) {
        let $this = this;
        $this.cfpLoadingBar.start();
        this.calculateUsableSpaceBelowFilter();
        this.service.search(this.filters, offset)
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
            .subscribe((res) => {
                if (res && res.length > 0){
                    $this.matches = res.map((properties, index) => {
/*                        if(_.hasIn(properties,'dicomAETitle')){
                            properties.dicomAETitle = properties.dicomAETitle.join(' | ');
                        }*/
                        if (_.hasIn(properties, 'deleterThreshold')){
                            properties.deleterThreshold = properties.deleterThreshold.map((deleter, i) => {
                                if (_.keys(deleter)[0] != ''){
                                    return _.keys(deleter)[0] + ':' + $this.convertBtoGBorMB(_.values(deleter)[0]);
                                }else{
                                    return $this.convertBtoGBorMB(_.values(deleter)[0]);
                                }
                            });
                        }
                        if (_.hasIn(properties, 'usableSpace')){
                            properties.usableSpace = $this.convertBtoGBorMB(properties.usableSpace);
                        }
                        if (_.hasIn(properties, 'totalSpace')){
                            properties.totalSpace = $this.convertBtoGBorMB(properties.totalSpace);
                        }
                        _.forEach(properties, (l, k) => {
                            if (_.isObject(l)){
                                properties[k] = l.join(' | ');
                            }
                        });

                        return {
                            offset: offset + index,
                            properties: properties,
                            showProperties: false
                        };
                    });
                        $this.cfpLoadingBar.complete();
                }else{
                    $this.cfpLoadingBar.complete();
                    $this.matches = [];
                    $this.mainservice.setMessage({
                        'title': 'Info',
                        'text': 'No storages found!',
                        'status': 'info'
                    });
                }
            }, (err) => {
                $this.cfpLoadingBar.complete();
                $this.matches = [];
                $this.httpErrorHandler.handleError(err);
            });
    };
    convertBtoGBorMB(value){
        if (value > 2000000000){
            return (Math.round((value / 1000 / 1000 / 1000) * 1000) / 1000 ) + ' GB';
        }else{
            return (Math.round((value / 1000 / 1000) * 1000) / 1000 ) + ' MB';
        }
    }
    getDifferenceTime(starttime, endtime){
        let start = new Date(starttime).getTime();
        let end = new Date(endtime).getTime();
        if (!start || !end || end < start){
            return null;
        }else{
            return this.msToTime(new Date(endtime).getTime() - new Date(starttime).getTime());
        }
    };
    msToTime(duration) {

        if (duration > 999){

            let milliseconds: any = parseInt((((duration % 1000))).toString())
                , seconds: any = parseInt(((duration / 1000) % 60).toString())
                , minutes: any = parseInt(((duration / (1000 * 60)) % 60).toString())
                , hours: any = parseInt(((duration / (1000 * 60 * 60))).toString());
            if (hours === 0){
                if (minutes === 0){
                    return seconds.toString() + '.' + milliseconds.toString() + ' sec';
                }else{
                    seconds = (seconds < 10) ? '0' + seconds : seconds;
                    return minutes.toString() + ':' + seconds.toString() + '.' + milliseconds.toString() + ' min';
                }
            }else{

                hours = (hours < 10) ? '0' + hours : hours;
                minutes = (minutes < 10) ? '0' + minutes : minutes;
                seconds = (seconds < 10) ? '0' + seconds : seconds;

                return hours.toString() + ':' + minutes.toString() + ':' + seconds.toString() + '.' + milliseconds.toString() + ' h';
            }
        }else{
            return duration.toString() + ' ms';
        }
    }
    flushBefore() {
        let select: any = [
            {
                title: 'PENDING',
                value: 'PENDING',
                label: 'PENDING'
            },
            {
                title: 'COMPLETED',
                value: 'COMPLETED',
                label: 'COMPLETED'
            },
            {
                title: 'WARNING',
                value: 'WARNING',
                label: 'WARNING'
            },
            {
                title: 'FAILED',
                value: 'FAILED',
                label: 'FAILED'
            }
        ];

        let parameters: any = {
            content: 'Select before date and status to delete all storage commitments',
            select: select,
            date: {
                placeholder: 'Updated before',
                format: 'yy-mm-dd'
            },
            result: {
                select: 'PENDING',
                date: undefined
            },
            saveButton: 'DELETE',
            saveButtonClass: 'btn-danger'
        };
        let $this = this;
        // let beforeDate = datePipeEn.transform(this.before,'yyyy-mm-dd');
        // console.log("beforeDate",beforeDate);
        this.confirm(parameters).subscribe(result => {
            if (result){
                // console.log("parametersdate",datePipeEn.transform(parameters.result.date,'yyyy-mm-dd'));
                $this.cfpLoadingBar.start();
                if (parameters.result.date === undefined){
                    $this.mainservice.setMessage({
                        'title': 'Error',
                        'text': '\'Updated before\'-date was not set',
                        'status': 'error'
                    });
                }else{

                    this.service.flush(parameters.result.select, parameters.result.date)
                        .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
                        .subscribe((res) => {
                            console.log('resflush', res);
                            $this.mainservice.setMessage({
                                'title': 'Info',
                                'text': res.deleted + ' queues deleted successfully!',
                                'status': 'info'
                            });
                            $this.search(0);
                            $this.cfpLoadingBar.complete();
                        }, (err) => {
                            $this.httpErrorHandler.handleError(err);
                        });
                }
            }
        });
    };
    delete(match){
        let $this = this;
        let parameters: any = {
            content: 'Are you sure you want to delete this task?',
            result: 'Ok',
            saveButton: 'DELETE',
            saveButtonClass: 'btn-danger'
        };
        this.confirm(parameters).subscribe(result => {
            if (result){
                $this.cfpLoadingBar.start();
                this.service.delete(match.properties.transactionUID)
                    .subscribe(
                        (res) => {
                            // match.properties.status = 'CANCELED';
                            $this.cfpLoadingBar.complete();
                            $this.search(0);
                            $this.mainservice.setMessage({
                                'title': 'Info',
                                'text': 'Task deleted successfully!',
                                'status': 'info'
                            });
                        },
                        (err) => {
                            $this.cfpLoadingBar.complete();
                            $this.httpErrorHandler.handleError(err);
                        });
            }
        });
    }
    getAets(){

        let $this = this;
        this.$http.get(
            '../aets'
        ).map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
            .subscribe((response) => {
                $this.aets = response;

            }, (err) => {
                console.log('error getting aets', err);
            });
    }

    /*    init() {
     let $this = this;
     $this.cfpLoadingBar.start();
     this.$http.get("../monitor/export")
     .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
     .subscribe((res) => {
     $this.exportTasks = res;
     // $this.queueName = res[0].name;
     $this.cfpLoadingBar.complete();
     })
     }*/
/*    initExporters(retries) {
        let $this = this;
        this.$http.get("../storage")
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
            .subscribe(
                (res) => {
                    console.log("res",res);
                    console.log("exporters",$this.exporters);
                    $this.exporters = res;
                    console.log("exporters2",$this.exporters);
                    if(res && res[0] && res[0].id){
                        $this.exporterID = res[0].id;
                    }
                    // $this.mainservice.setGlobal({exporterID:$this.exporterID});
                },
                (res) => {
                    if (retries)
                        this.initExporters(retries-1);
                });
    }*/
}
