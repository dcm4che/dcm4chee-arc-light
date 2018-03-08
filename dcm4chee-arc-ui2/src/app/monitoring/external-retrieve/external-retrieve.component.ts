import {Component, OnDestroy, OnInit, ViewContainerRef} from '@angular/core';
import {AppService} from "../../app.service";
import * as _ from 'lodash';
import {AeListService} from "../../ae-list/ae-list.service";
import {Observable} from "rxjs/Observable";
import {ExternalRetrieveService} from "./external-retrieve.service";
import {HttpErrorHandler} from "../../helpers/http-error-handler";
import {ExportDialogComponent} from "../../widgets/dialogs/export/export.component";
import {MatDialog, MatDialogConfig, MatDialogRef} from "@angular/material";
import {ConfirmComponent} from "../../widgets/dialogs/confirm/confirm.component";
import {DatePipe} from "@angular/common";
import {j4care} from "../../helpers/j4care.service";
import * as FileSaver from 'file-saver';
import {WindowRefService} from "../../helpers/window-ref.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import "rxjs/add/observable/forkJoin";
import {LoadingBarService} from '@ngx-loading-bar/core';
import {environment} from "../../../environments/environment";

@Component({
  selector: 'external-retrieve',
  templateUrl: './external-retrieve.component.html'
})
export class ExternalRetrieveComponent implements OnInit,OnDestroy {
    before;
    localAET;
    remoteAET;
    destinationAET;
    filterSchema;
    filterObject;
    isRole: any = (user)=>{return false;};
    user;
    externalRetrieveEntries;
    _ = _;
    dialogRef: MatDialogRef<any>;
    exporters;
    exporterID;
    datePipe = new DatePipe('us-US');
    devices;
    count;
    timer = {
        started:false,
        startText:"Start Auto Refresh",
        stopText:"Stop Auto Refresh"
    };
    allActionsActive = [];
    allActionsOptions = [
        {
            value:"cancel",
            label:"Cancel all matching tasks"
        },{
            value:"reschedule",
            label:"Reschedule all matching tasks"
        },{
            value:"delete",
            label:"Delete all matching tasks"
        }
    ];
    allAction;
    statusValues = {};
    refreshInterval;
    interval = 15;
    tableHovered = false;
    Object = Object;
    constructor(
      public cfpLoadingBar: LoadingBarService,
      public mainservice: AppService,
      public aeListService:AeListService,
      public service:ExternalRetrieveService,
      public httpErrorHandler:HttpErrorHandler,
      public dialog: MatDialog,
      public config: MatDialogConfig,
      public viewContainerRef: ViewContainerRef,
      private $http:J4careHttpService
    ) { }

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
        let $this = this;
        // console.log("in if studies ajax"); epx
        this.service.statusValues().forEach(val =>{
            this.statusValues[val.value] = {
                count: 0,
                loader: false
            };
        });
        if (!this.mainservice.user){
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
/*        console.log("localStorage",localStorage.getItem('externalRetrieveFilters'));
        console.log("localStorageTES",localStorage.getItem('externalRetrieveFiltersTESG'));
        let savedFilters = localStorage.getItem('externalRetrieveFilters');
        if(savedFilters)
            this.filterObject = JSON.parse(savedFilters);
        else*/
            this.filterObject = {
                limit:20
            };
        Observable.forkJoin(
            this.aeListService.getAes(),
            this.aeListService.getAets(),
            this.service.getDevices()
        ).subscribe((response)=>{
            this.remoteAET = this.destinationAET = (<any[]>j4care.extendAetObjectWithAlias(response[0])).map(ae => {
                return {
                    value:ae.dicomAETitle,
                    text:ae.dicomAETitle
                }
            });
            this.localAET = (<any[]>j4care.extendAetObjectWithAlias(response[1])).map(ae => {
                return {
                    value:ae.dicomAETitle,
                    text:ae.dicomAETitle
                }
            });
            this.devices = (<any[]>response[2])
                .filter(dev => dev.hasArcDevExt)
                .map(device => {
                return {
                    value:device.dicomDeviceName,
                    text:device.dicomDeviceName
                }
            });
            this.initSchema();
        });
        this.initExporters(2);
        this.onFormChange(this.filterObject);
    }
    toggleAutoRefresh(){
        this.timer.started = !this.timer.started;
        if(this.timer.started){
            this.getCounts();
            this.refreshInterval = setInterval(()=>{
                this.getCounts();
            },this.interval*1000);
        }else
            clearInterval(this.refreshInterval);
    }
    tableMousEnter(){
        this.tableHovered = true;
    }
    tableMousLeave(){
        this.tableHovered = false;
    }
    getCounts(){
        let filters = Object.assign({},this.filterObject);
        if(!this.tableHovered)
            this.getTasks(0);
        Object.keys(this.statusValues).forEach(status=>{
            filters.status = status;
            this.statusValues[status].loader = true;
            this.service.getCount(filters).subscribe((count)=>{
            this.statusValues[status].loader = false;
                try{
                    this.statusValues[status].count = count.count;
                }catch (e){
                    this.statusValues[status].count = "";
                }
            },(err)=>{
                this.statusValues[status].loader = false;
                this.statusValues[status].count = "!";
            });
        });
    }
    hasOlder(objs) {
        return objs && (objs.length == this.filterObject.limit);
    };
    hasNewer(objs) {
        return objs && objs.length && objs[0].offset;
    };
    newerOffset(objs) {
        return Math.max(0, objs[0].offset - this.filterObject.limit*1);
    };
    olderOffset(objs) {
        return objs[0].offset + this.filterObject.limit*1;
    };
    initSchema(){
        this.filterSchema = this.service.getFilterSchema(this.localAET,this.destinationAET,this.remoteAET, this.devices,`COUNT ${((this.count || this.count == 0)?this.count:'')}`);
    }
    confirm(confirmparameters){
        this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(ConfirmComponent, {
            height: 'auto',
            width: '500px'
        });
        this.dialogRef.componentInstance.parameters = confirmparameters;
        return this.dialogRef.afterClosed();
    };
    downloadCsv(){
        let token;
        this.$http.refreshToken().subscribe((response)=>{
            if(!this.mainservice.global.notSecure){
                if(response && response.length != 0){
                    this.$http.resetAuthenticationInfo(response);
                    token = response['token'];
                }else{
                    token = this.mainservice.global.authentication.token;
                }
            }
            let filterClone = _.cloneDeep(this.filterObject);
            delete filterClone.offset;
            delete filterClone.limit;
            if(!this.mainservice.global.notSecure){
                WindowRefService.nativeWindow.open(`../monitor/retrieve?accept=text/csv&access_token=${token}&${this.mainservice.param(filterClone)}`);
            }else{
                WindowRefService.nativeWindow.open(`../monitor/retrieve?accept=text/csv&${this.mainservice.param(filterClone)}`);
            }
        });
    }
    allActionChanged(e){
        let text = `Are you sure, you want to ${this.allAction} all matching tasks?`;
        let filter = Object.assign(this.filterObject);
        delete filter.limit;
        delete filter.offset;
        this.confirm({
            content: text
        }).subscribe((ok)=>{
            if(ok){
                this.cfpLoadingBar.start();
                switch (this.allAction){
                case "cancel":
                            this.service.cancelAll(this.filterObject).subscribe((res)=>{
                                this.mainservice.setMessage({
                                    'title': 'Info',
                                    'text': res.count + ' tasks deleted successfully!',
                                    'status': 'info'
                                });
                                this.cfpLoadingBar.complete();
                            }, (err) => {
                                this.cfpLoadingBar.complete();
                                this.httpErrorHandler.handleError(err);
                            });

                    break;
                case "reschedule":
                        this.service.rescheduleAll(this.filterObject).subscribe((res)=>{
                            this.mainservice.setMessage({
                                'title': 'Info',
                                'text': res.count + ' queues rescheduled successfully!',
                                'status': 'info'
                            });
                            this.cfpLoadingBar.complete();
                        }, (err) => {
                            this.cfpLoadingBar.complete();
                            this.httpErrorHandler.handleError(err);
                        });
                    break;
                case "delete":
                        this.service.deleteAll(this.filterObject).subscribe((res)=>{
                            this.mainservice.setMessage({
                                'title': 'Info',
                                'text': res.deleted + ' tasks deleted successfully!',
                                'status': 'info'
                            });
                            this.cfpLoadingBar.complete();
                        }, (err) => {
                            this.cfpLoadingBar.complete();
                            this.httpErrorHandler.handleError(err);
                        });
                    break;
                }
                this.cfpLoadingBar.complete();
                this.allAction = "";
                this.allAction = undefined;
            }
        });
    }
    delete(match){
        let $this = this;
        let parameters: any = {
            content: 'Are you sure you want to delete this task?'
        };
        this.confirm(parameters).subscribe(result => {
            if (result){
                $this.cfpLoadingBar.start();
                this.service.delete(match.properties.pk)
                    .subscribe(
                        (res) => {
                            // match.properties.status = 'CANCELED';
                            $this.cfpLoadingBar.complete();
                            $this.getTasks(match.offset||0);
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
    cancel(match) {
        let $this = this;
        let parameters: any = {
            content: 'Are you sure you want to cancel this task?'
        };
        this.confirm(parameters).subscribe(result => {
            if (result){
                $this.cfpLoadingBar.start();
                this.service.cancel(match.properties.pk)
                    .subscribe(
                        (res) => {
                            match.properties.status = 'CANCELED';
                            $this.cfpLoadingBar.complete();
                            $this.mainservice.setMessage({
                                'title': 'Info',
                                'text': 'Task canceled successfully!',
                                'status': 'info'
                            });
                        },
                        (err) => {
                            $this.cfpLoadingBar.complete();
                            console.log('cancleerr', err);
                            $this.httpErrorHandler.handleError(err);
                        });
            }
        });
    };
    onFormChange(filters){
        this.allActionsActive = this.allActionsOptions.filter((o)=>{
            if(filters.status == "SCHEDULED" || filters.status == "IN PROCESS"){
                return o.value != 'reschedule';
            }else{
                if(filters.status === '*' || !filters.status || filters.status === '')
                    return o.value != 'cancel' && o.value != 'reschedule';
                else
                    return o.value != 'cancel';
            }
        });
    }
    reschedule(match) {
        let $this = this;
        let parameters: any = {
            content: 'Are you sure you want to reschedule this task?'
        };
        this.confirm(parameters).subscribe(result => {
            if (result){
                $this.cfpLoadingBar.start();
                this.service.reschedule(match.properties.pk)
                    .subscribe(
                        (res) => {
                            $this.getTasks(match.offset||0);
                            $this.cfpLoadingBar.complete();
                            $this.mainservice.setMessage({
                                'title': 'Info',
                                'text': 'Task rescheduled successfully!',
                                'status': 'info'
                            });
                        },
                        (err) => {
                            $this.cfpLoadingBar.complete();
                            $this.httpErrorHandler.handleError(err);
                        });
            }
        });
    };
    checkAll(event){
        this.externalRetrieveEntries.forEach((match)=>{
            match.checked = event.target.checked;
        });
    }
    executeAll(mode){
        this.confirm({
            content: `Are you sure you want to ${mode} selected entries?`
        }).subscribe(result => {
            if (result){
                this.cfpLoadingBar.start();
                this.externalRetrieveEntries.forEach((match)=>{
                    if(match.checked){
                        this.service[mode](match.properties.pk)
                            .subscribe((res) => {
                            console.log("execute result=",res);
                            },(err)=>{
                                this.httpErrorHandler.handleError(err);
                            });
                    }
                });
                //TODO
                setTimeout(()=>{
                    this.getTasks(this.externalRetrieveEntries[0].offset || 0);
                    this.cfpLoadingBar.complete();
                },300);
            }
        });
    }
    onSubmit(object){
        if(_.hasIn(object,"id") && _.hasIn(object,"model")){
            if(object.id === "count"){
                this.getCount();
            }else{
                this.getTasks(0);
            }
        }
    }
    getTasks(offset){
        let $this = this;
        $this.cfpLoadingBar.start();
        this.service.getExternalRetrieveEntries(this.filterObject,offset).subscribe(
            res =>  {
                $this.cfpLoadingBar.complete();
/*                if(!environment.production){
                    res = [{"pk":4893785,"createdTime":"2018-02-27T11:58:02.760+0200","updatedTime":"2018-03-07T12:12:28.635+0200","LocalAET":"ARCHIVE1TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.2.124.113532.80.22199.5762.20161222.101001.49162699","remaining":68,"completed":3042,"failed":3,"statusCode":"FF00","queue":"CMoveSCU","JMSMessageID":"ID:b31033e4-1ba4-11e8-9859-0242ac110003","dicomDeviceName":"archive1trt","status":"IN PROCESS","scheduledTime":"2018-02-27T11:58:02.757+0200","processingStartTime":"2018-03-07T12:01:00.200+0200"},{"pk":4892465,"createdTime":"2018-02-27T11:57:12.080+0200","updatedTime":"2018-03-07T12:12:27.185+0200","LocalAET":"ARCHIVE1TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.2.124.113532.80.22199.5762.20170201.182548.342912969","remaining":374,"completed":468,"statusCode":"FF00","queue":"CMoveSCU","JMSMessageID":"ID:94dae4f7-1ba4-11e8-9859-0242ac110003","dicomDeviceName":"archive1trt","status":"IN PROCESS","scheduledTime":"2018-02-27T11:57:12.077+0200","processingStartTime":"2018-03-07T12:10:45.965+0200"},{"pk":4894309,"createdTime":"2018-02-27T11:58:19.820+0200","updatedTime":"2018-03-07T12:12:27.135+0200","LocalAET":"ARCHIVE1TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.2.124.113532.80.22199.5762.20170131.101440.313107820","remaining":244,"completed":1064,"statusCode":"FF00","queue":"CMoveSCU","JMSMessageID":"ID:bd3b8143-1ba4-11e8-9859-0242ac110003","dicomDeviceName":"archive1trt","status":"IN PROCESS","scheduledTime":"2018-02-27T11:58:19.818+0200","processingStartTime":"2018-03-07T12:08:21.305+0200"},{"pk":4891892,"createdTime":"2018-02-27T11:56:53.285+0200","updatedTime":"2018-03-07T12:12:26.440+0200","LocalAET":"ARCHIVE1TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.3.46.670589.33.1.63621562306829624900001.5271717421314862736","remaining":911,"completed":850,"statusCode":"FF00","queue":"CMoveSCU","JMSMessageID":"ID:89a74f3c-1ba4-11e8-9859-0242ac110003","dicomDeviceName":"archive1trt","status":"IN PROCESS","scheduledTime":"2018-02-27T11:56:53.284+0200","processingStartTime":"2018-03-07T12:08:49.655+0200"},{"pk":4894972,"createdTime":"2018-02-27T11:58:42.109+0200","updatedTime":"2018-03-07T12:12:24.674+0200","LocalAET":"ARCHIVE1TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.2.840.113619.2.55.3.2831156993.992.1485429329.708","remaining":165,"completed":871,"statusCode":"FF00","queue":"CMoveSCU","JMSMessageID":"ID:ca848996-1ba4-11e8-9859-0242ac110003","dicomDeviceName":"archive1trt","status":"IN PROCESS","scheduledTime":"2018-02-27T11:58:42.107+0200","processingStartTime":"2018-03-07T12:09:13.674+0200"}];
                }*/
                if (res && res.length > 0){
                    this.externalRetrieveEntries =  res.map((properties, index) => {
                        if (_.hasIn(properties, 'Modality')){
                            properties.Modality = properties.Modality.join(',');
                        }
                        properties.taskState = (properties.completed ? properties.completed*1:0) + ' / ' + (properties.remaining ? properties.remaining*1:0) + ' / '+ (properties.failed ? properties.failed*1:0);
                        let endTime = properties.processingEndTime || this.mainservice.serverTime;
                        try{
                            properties.NumberOfInstances = properties.NumberOfInstances || ((properties.completed ? properties.completed*1:0) + (properties.remaining ? properties.remaining*1:0) + (properties.failed ? properties.failed*1:0));
                            properties.InstancePerSec = (Math.round(((properties.completed ? properties.completed*1:0)/((new Date(endTime).getTime()/1000) - (new Date(properties.processingStartTime).getTime()/1000)))*1000)/1000) || '-';
                            if(!properties.processingEndTime)
                                properties.approximatelyEndTime = Math.round((properties.remaining / properties.InstancePerSec)*100)/100 ? `in ~ ${Math.round((properties.remaining / properties.InstancePerSec)*100)/100} s`:'-';
                        }catch (e){
                            properties.InstancePerSec = '';
                        }
                        return {
                            offset: offset + index,
                            properties: properties,
                            showProperties: false
                        };
                    });
                    $this.count = undefined;
                }else{
                    $this.cfpLoadingBar.complete();
                    $this.externalRetrieveEntries = [];
                    $this.mainservice.setMessage({
                        'title': 'Info',
                        'text': 'No tasks found!',
                        'status': 'info'
                    });
                }
            },
            err => {
                $this.externalRetrieveEntries = [];
                $this.cfpLoadingBar.complete();
                $this.httpErrorHandler.handleError(err);
            });
    }
    getCount(){
        this.cfpLoadingBar.start();
        this.service.getCount(this.filterObject).subscribe((count)=>{

            try{
                this.count = count.count;
            }catch (e){
                this.count = "";
            }
            this.initSchema();
            this.cfpLoadingBar.complete();
        },(err)=>{
            this.cfpLoadingBar.complete();
            this.initSchema();
            this.httpErrorHandler.handleError(err);
        });
    }
    initExporters(retries) {
        let $this = this;
        this.service.getExporters().subscribe(
                (res) => {
                    $this.exporters = res;
                    if (res && res[0] && res[0].id){
                        $this.exporterID = res[0].id;
                    }
                    // $this.mainservice.setGlobal({exporterID:$this.exporterID});
                },
                (res) => {
                    if (retries)
                        $this.initExporters(retries - 1);
                });
    }
    getDevices(){
        this.cfpLoadingBar.start();
        this.service.getDevices().subscribe(devices=>{
            this.cfpLoadingBar.complete();
            this.devices = devices.filter(dev => dev.hasArcDevExt);
        },(err)=>{
            this.cfpLoadingBar.complete();
            console.error("Could not get devices",err);
        });
    }
    ngOnDestroy(){
        clearInterval(this.refreshInterval);
        // localStorage.setItem('externalRetrieveFilters',JSON.stringify(this.filterObject));
    }
}
