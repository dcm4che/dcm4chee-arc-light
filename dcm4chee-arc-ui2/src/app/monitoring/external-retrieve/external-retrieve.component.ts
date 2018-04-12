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
import {CsvRetrieveComponent} from "../../widgets/dialogs/csv-retrieve/csv-retrieve.component";

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
    interval = 10;
    tableHovered = false;
    Object = Object;
    batchGrouped = false;
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
        this.confirm({
            content:"Do you want to use semicolon as delimiter?",
            cancelButton:"No",
            saveButton:"Yes",
            result:"yes"
        }).subscribe((ok)=>{
            let semicolon = false;
            if(ok)
                semicolon = true;
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
                    WindowRefService.nativeWindow.open(`../monitor/retrieve?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&access_token=${token}&${this.mainservice.param(filterClone)}`);
                }else{
                    WindowRefService.nativeWindow.open(`../monitor/retrieve?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&${this.mainservice.param(filterClone)}`);
                }
            });
        })
    }
    uploadCsv(){
        this.dialogRef = this.dialog.open(CsvRetrieveComponent, {
            height: 'auto',
            width: '500px'
        });
        this.dialogRef.componentInstance.aes = this.remoteAET ;
        this.dialogRef.componentInstance.params = {
            aet:this.filterObject['LocalAET']||'',
            externalAET:this.filterObject['RemoteAET']||'',
            destinationAET:this.filterObject['DestinationAET']||'',
            batchID:this.filterObject['batchID']||'',
        };
        this.dialogRef.afterClosed().subscribe((ok)=>{
            if(ok){
                console.log("ok",ok);
                //TODO
            }
        });
    }
    allActionChanged(e){
        let text = `Are you sure, you want to ${this.allAction} all matching tasks?`;
        let filter = Object.assign({}, this.filterObject);
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
            }
        });
        this.allAction = "";
        this.allAction = undefined;
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
    testModel = {
        "completed":60,
        "in-process":5,
        "warning":24,
        "failed":12,
        "scheduled":123,
        "canceled":26
    }
    getTasks(offset){
        let $this = this;
        $this.cfpLoadingBar.start();
        this.service.getExternalRetrieveEntries(this.filterObject,offset, this.batchGrouped).subscribe(
            res =>  {
                // res = [{"batchID":"test12","tasks":{"completed":"4"},"dicomDeviceName":["dcm4chee-arc", "dcm4chee-arc2"],"LocalAET":["DCM4CHEE"],"RemoteAET":["DCM4CHEE", "DCM4CHEE2","DCM4CHEE3","DCM4CHEE", "DCM4CHEE2","DCM4CHEE3","DCM4CHEE", "DCM4CHEE2","DCM4CHEE3","DCM4CHEE", "DCM4CHEE2","DCM4CHEE3"],"DestinationAET":["DCM4CHEE", "DCM4CHEE3","DCM4CHEE4","DCM4CHEE5"],"createdTimeRange":["2018-04-10 18:02:06.936","2018-04-10 18:02:07.049"],"updatedTimeRange":["2018-04-10 18:02:08.300311","2018-04-10 18:02:08.553547"],"scheduledTimeRange":["2018-04-10 18:02:06.935","2018-04-10 18:02:07.049"],"processingStartTimeRange":["2018-04-10 18:02:06.989","2018-04-10 18:02:07.079"],"processingEndTimeRange":["2018-04-10 18:02:08.31","2018-04-10 18:02:08.559"]},{"batchID":"test2","tasks":{"completed":"12","failed":3},"dicomDeviceName":["dcm4chee-arc"],"LocalAET":["DCM4CHEE"],"RemoteAET":["DCM4CHEE"],"DestinationAET":["DCM4CHEE"],"createdTimeRange":["2018-04-10 18:02:25.71","2018-04-10 18:02:26.206"],"updatedTimeRange":["2018-04-10 18:02:25.932859","2018-04-10 18:02:27.335741"],"scheduledTimeRange":["2018-04-10 18:02:25.709","2018-04-10 18:02:26.204"],"processingStartTimeRange":["2018-04-10 18:02:25.739","2018-04-10 18:02:26.622"],"processingEndTimeRange":["2018-04-10 18:02:25.943","2018-04-10 18:02:27.344"]}];


                $this.cfpLoadingBar.complete();
/*                if(!environment.production){
                    res = [{"pk":4992267,"createdTime":"2018-02-27T12:56:14.119+0200","updatedTime":"2018-03-09T18:48:29.636+0200","LocalAET":"ARCHIVE2TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.2.124.113532.80.22199.5762.20170508.101222.475483216","remaining":2145,"failed":3,"completed":1253,"statusCode":"FF00","queue":"CMoveSCU","JMSMessageID":"ID:d4134e6e-1bac-11e8-9d08-0242ac110002","dicomDeviceName":"archive2trt","status":"IN PROCESS","scheduledTime":"2018-02-27T12:56:14.117+0200","processingStartTime":"2018-03-09T18:43:27.255+0200"},{"pk":5433468,"createdTime":"2018-02-28T14:26:01.480+0200","updatedTime":"2018-03-09T18:48:29.007+0200","LocalAET":"ARCHIVE4TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.3.6.1.4.1.28284.1.1.2.2.1.28970401","remaining":167,"completed":2418,"statusCode":"FF00","queue":"CMoveSCU","JMSMessageID":"ID:899a7fab-1c82-11e8-b6f5-0242ac110002","dicomDeviceName":"archive4trt","status":"IN PROCESS","scheduledTime":"2018-02-28T14:26:01.473+0200","processingStartTime":"2018-03-09T18:38:51.761+0200"},{"pk":5616182,"createdTime":"2018-03-06T17:07:54.602+0200","updatedTime":"2018-03-09T18:48:28.723+0200","LocalAET":"ARCHIVE3TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.2.124.113532.80.22199.5762.20170714.100217.490558851","remaining":631,"completed":565,"statusCode":"FF00","queue":"CMoveSCU","JMSMessageID":"ID:258e6849-2150-11e8-93b4-0242ac110002","dicomDeviceName":"archive3trt","status":"IN PROCESS","scheduledTime":"2018-03-06T17:07:54.599+0200","processingStartTime":"2018-03-09T18:46:18.164+0200"},{"pk":4956824,"createdTime":"2018-02-27T12:35:14.484+0200","updatedTime":"2018-03-09T18:48:28.684+0200","LocalAET":"ARCHIVE1TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.2.124.113532.80.22199.5762.20170209.125434.510801978","remaining":580,"statusCode":"FF00","queue":"CMoveSCU","JMSMessageID":"ID:e5466948-1ba9-11e8-9859-0242ac110003","dicomDeviceName":"archive1trt","status":"IN PROCESS","scheduledTime":"2018-02-27T12:35:14.482+0200","processingStartTime":"2018-03-09T18:48:27.065+0200"},{"pk":5433080,"createdTime":"2018-02-28T14:25:35.517+0200","updatedTime":"2018-03-09T18:48:28.217+0200","LocalAET":"ARCHIVE4TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.2.124.113532.80.22199.5762.20170505.110957.398071053","remaining":1921,"completed":569,"statusCode":"FF00","queue":"CMoveSCU","JMSMessageID":"ID:7a2177fe-1c82-11e8-b6f5-0242ac110002","dicomDeviceName":"archive4trt","status":"IN PROCESS","scheduledTime":"2018-02-28T14:25:35.514+0200","processingStartTime":"2018-03-09T18:46:10.798+0200"},{"pk":4991136,"createdTime":"2018-02-27T12:55:36.184+0200","updatedTime":"2018-03-09T18:48:28.157+0200","LocalAET":"ARCHIVE2TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.2.124.113532.80.22199.5762.20170413.112017.48416062","remaining":883,"completed":990,"statusCode":"FF00","queue":"CMoveSCU","JMSMessageID":"ID:bd769519-1bac-11e8-9d08-0242ac110002","dicomDeviceName":"archive2trt","status":"IN PROCESS","scheduledTime":"2018-02-27T12:55:36.179+0200","processingStartTime":"2018-03-09T18:44:37.170+0200"},{"pk":5616069,"createdTime":"2018-03-06T17:07:47.484+0200","updatedTime":"2018-03-09T18:48:28.149+0200","LocalAET":"ARCHIVE3TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.2.840.113619.2.300.4367.1499996829.0.21","remaining":9,"completed":7,"statusCode":"FF00","queue":"CMoveSCU","JMSMessageID":"ID:21507008-2150-11e8-93b4-0242ac110002","dicomDeviceName":"archive3trt","status":"IN PROCESS","scheduledTime":"2018-03-06T17:07:47.482+0200","processingStartTime":"2018-03-09T18:48:12.955+0200"},{"pk":4955446,"createdTime":"2018-02-27T12:34:29.086+0200","updatedTime":"2018-03-09T18:48:27.958+0200","LocalAET":"ARCHIVE1TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.2.124.113532.80.22199.5762.20170208.155223.490716668","remaining":1094,"completed":1684,"statusCode":"FF00","queue":"CMoveSCU","JMSMessageID":"ID:ca371346-1ba9-11e8-9859-0242ac110003","dicomDeviceName":"archive1trt","status":"IN PROCESS","scheduledTime":"2018-02-27T12:34:29.084+0200","processingStartTime":"2018-03-09T18:41:29.925+0200"},{"pk":5432612,"createdTime":"2018-02-28T14:25:03.080+0200","updatedTime":"2018-03-09T18:48:27.842+0200","LocalAET":"ARCHIVE4TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.2.124.113532.80.22199.5762.20170505.132821.401278439","remaining":542,"completed":964,"statusCode":"FF00","queue":"CMoveSCU","JMSMessageID":"ID:66cc20ea-1c82-11e8-b6f5-0242ac110002","dicomDeviceName":"archive4trt","status":"IN PROCESS","scheduledTime":"2018-02-28T14:25:03.078+0200","processingStartTime":"2018-03-09T18:44:32.275+0200"},{"pk":4992290,"createdTime":"2018-02-27T12:56:14.773+0200","updatedTime":"2018-03-09T18:48:27.566+0200","LocalAET":"ARCHIVE2TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.2.246.561.2.11.1.6565451000113908","remaining":4,"statusCode":"FF00","queue":"CMoveSCU","JMSMessageID":"ID:d4771959-1bac-11e8-9d08-0242ac110002","dicomDeviceName":"archive2trt","status":"IN PROCESS","scheduledTime":"2018-02-27T12:56:14.770+0200","processingStartTime":"2018-03-09T18:48:27.358+0200"},{"pk":4992279,"createdTime":"2018-02-27T12:56:14.475+0200","updatedTime":"2018-03-09T18:48:27.342+0200","LocalAET":"ARCHIVE2TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"2.25.313335791585497060122836424244464187411","completed":1,"statusCode":"0000","queue":"CMoveSCU","JMSMessageID":"ID:d44979a4-1bac-11e8-9d08-0242ac110002","dicomDeviceName":"archive2trt","status":"COMPLETED","scheduledTime":"2018-02-27T12:56:14.471+0200","processingStartTime":"2018-03-09T18:48:26.827+0200","processingEndTime":"2018-03-09T18:48:27.350+0200","outcomeMessage":"Export STUDY[suid:2.25.313335791585497060122836424244464187411] from ARCPAS1TRT_MIGR to EEVNATRT - completed:1"},{"pk":4992225,"createdTime":"2018-02-27T12:56:12.873+0200","updatedTime":"2018-03-09T18:48:27.297+0200","LocalAET":"ARCHIVE2TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.3.46.670589.33.1.63630189811050451200001.4769016958661333985","remaining":138,"completed":1477,"statusCode":"FF00","queue":"CMoveSCU","JMSMessageID":"ID:d3552e79-1bac-11e8-9d08-0242ac110002","dicomDeviceName":"archive2trt","status":"IN PROCESS","scheduledTime":"2018-02-27T12:56:12.870+0200","processingStartTime":"2018-03-09T18:42:30.981+0200"},{"pk":5616746,"createdTime":"2018-03-06T17:08:49.754+0200","updatedTime":"2018-03-09T18:48:27.099+0200","LocalAET":"ARCHIVE3TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.3.6.1.4.1.28284.1.5.2.2.1.14938961","remaining":1146,"completed":126,"statusCode":"FF00","queue":"CMoveSCU","JMSMessageID":"ID:466e159a-2150-11e8-93b4-0242ac110002","dicomDeviceName":"archive3trt","status":"IN PROCESS","scheduledTime":"2018-03-06T17:08:49.752+0200","processingStartTime":"2018-03-09T18:47:55.605+0200"},{"pk":4956814,"createdTime":"2018-02-27T12:35:14.177+0200","updatedTime":"2018-03-09T18:48:27.038+0200","LocalAET":"ARCHIVE1TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.2.124.113532.80.22199.5762.20170209.132855.511536570","completed":1,"statusCode":"0000","queue":"CMoveSCU","JMSMessageID":"ID:e5176a03-1ba9-11e8-9859-0242ac110003","dicomDeviceName":"archive1trt","status":"COMPLETED","scheduledTime":"2018-02-27T12:35:14.174+0200","processingStartTime":"2018-03-09T18:48:26.357+0200","processingEndTime":"2018-03-09T18:48:27.048+0200","outcomeMessage":"Export STUDY[suid:1.2.124.113532.80.22199.5762.20170209.132855.511536570] from ARCPAS1TRT_MIGR to EEVNATRT - completed:1"},{"pk":5432566,"createdTime":"2018-02-28T14:25:00.119+0200","updatedTime":"2018-03-09T18:48:27.027+0200","LocalAET":"ARCHIVE4TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.2.250.1.59.470.13.452.20170505123024.12519.141","remaining":943,"completed":251,"statusCode":"FF00","queue":"CMoveSCU","JMSMessageID":"ID:6508028c-1c82-11e8-b6f5-0242ac110002","dicomDeviceName":"archive4trt","status":"IN PROCESS","scheduledTime":"2018-02-28T14:25:00.115+0200","processingStartTime":"2018-03-09T18:47:25.199+0200"},{"pk":5616450,"createdTime":"2018-03-06T17:08:10.800+0200","updatedTime":"2018-03-09T18:48:27.003+0200","LocalAET":"ARCHIVE3TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.2.124.113532.80.22199.5762.20170713.150449.478900578","remaining":322,"completed":409,"statusCode":"FF00","queue":"CMoveSCU","JMSMessageID":"ID:2f362dc5-2150-11e8-93b4-0242ac110002","dicomDeviceName":"archive3trt","status":"IN PROCESS","scheduledTime":"2018-03-06T17:08:10.798+0200","processingStartTime":"2018-03-09T18:46:49.974+0200"},{"pk":4992269,"createdTime":"2018-02-27T12:56:14.170+0200","updatedTime":"2018-03-09T18:48:26.813+0200","LocalAET":"ARCHIVE2TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.2.124.113532.80.22199.5762.20170512.130758.590078160","completed":1,"statusCode":"0000","queue":"CMoveSCU","JMSMessageID":"ID:d41b3daf-1bac-11e8-9d08-0242ac110002","dicomDeviceName":"archive2trt","status":"COMPLETED","scheduledTime":"2018-02-27T12:56:14.168+0200","processingStartTime":"2018-03-09T18:48:26.330+0200","processingEndTime":"2018-03-09T18:48:26.821+0200","outcomeMessage":"Export STUDY[suid:1.2.124.113532.80.22199.5762.20170512.130758.590078160] from ARCPAS1TRT_MIGR to EEVNATRT - completed:1"},{"pk":4954731,"createdTime":"2018-02-27T12:34:07.553+0200","updatedTime":"2018-03-09T18:48:26.756+0200","LocalAET":"ARCHIVE1TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.3.6.1.4.1.28284.1.1.2.2.1.28479772","remaining":518,"completed":1133,"statusCode":"FF00","queue":"CMoveSCU","JMSMessageID":"ID:bd613efc-1ba9-11e8-9859-0242ac110003","dicomDeviceName":"archive1trt","status":"IN PROCESS","scheduledTime":"2018-02-27T12:34:07.549+0200","processingStartTime":"2018-03-09T18:43:54.803+0200"},{"pk":4956466,"createdTime":"2018-02-27T12:35:03.492+0200","updatedTime":"2018-03-09T18:48:26.566+0200","LocalAET":"ARCHIVE1TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.2.124.113532.80.22199.5762.20170209.130455.511029795","remaining":53,"completed":755,"statusCode":"FF00","queue":"CMoveSCU","JMSMessageID":"ID:deb92a97-1ba9-11e8-9859-0242ac110003","dicomDeviceName":"archive1trt","status":"IN PROCESS","scheduledTime":"2018-02-27T12:35:03.490+0200","processingStartTime":"2018-03-09T18:45:10.522+0200"},{"pk":4956804,"createdTime":"2018-02-27T12:35:13.885+0200","updatedTime":"2018-03-09T18:48:26.338+0200","LocalAET":"ARCHIVE1TRT","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATRT","StudyInstanceUID":"1.3.51.0.7.11066312730.58404.24384.35414.44745.27011.41107","completed":1,"statusCode":"0000","queue":"CMoveSCU","JMSMessageID":"ID:e4eadbbe-1ba9-11e8-9859-0242ac110003","dicomDeviceName":"archive1trt","status":"COMPLETED","scheduledTime":"2018-02-27T12:35:13.882+0200","processingStartTime":"2018-03-09T18:48:24.942+0200","processingEndTime":"2018-03-09T18:48:26.347+0200","outcomeMessage":"Export STUDY[suid:1.3.51.0.7.11066312730.58404.24384.35414.44745.27011.41107] from ARCPAS1TRT_MIGR to EEVNATRT - completed:1"}];
                }*/
                if (res && res.length > 0){
                    this.externalRetrieveEntries =  res.map((properties, index) => {
                        if (_.hasIn(properties, 'Modality')){
                            properties.Modality = properties.Modality.join(',');
                        }
                        properties.taskState = (properties.completed ? properties.completed*1:0) + ' / ' + (properties.remaining ? properties.remaining*1:0) + ' / '+ (properties.failed ? properties.failed*1:0);
                        let endTime =  properties.processingEndTime ? new Date(j4care.splitTimeAndTimezone(properties.processingEndTime).time) :  this.mainservice.serverTime;
/*                        if(!environment.production){
                            console.log("processingEndTime1",properties.processingEndTime);
                            if(properties.processingEndTime)
                            console.log("processingEndTime2",new Date(j4care.splitTimeAndTimezone(properties.processingEndTime).time));
                            endTime = properties.processingEndTime ? new Date(j4care.splitTimeAndTimezone(properties.processingEndTime).time) : new Date(j4care.splitTimeAndTimezone("2018-03-09T18:48:23.346+0200").time);
                        }*/
                        try{
                            properties.NumberOfInstances = properties.NumberOfInstances || ((properties.completed ? properties.completed*1:0) + (properties.remaining ? properties.remaining*1:0) + (properties.failed ? properties.failed*1:0));
                            properties.InstancePerSec = (Math.round(((properties.completed ? properties.completed*1:0)/((endTime.getTime()/1000) - (new Date(properties.processingStartTime).getTime()/1000)))*100)/100) || '-';
                            if(!properties.processingEndTime)
                                properties.approximatelyEndTime = Math.round((properties.remaining / properties.InstancePerSec)*100)/100 ? `${this.secToMinSecString((properties.remaining / properties.InstancePerSec))}`:'-';
                        }catch (e){
                            properties.InstancePerSec = '';
                        }
                        if(this.batchGrouped)
                            this.service.stringifyArrayOrObject(properties);
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
/*    mscFormat(duration){
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
    }*/
    secToMinSecString(rawSec){
        let min;
        let sec;
        try {
            if (rawSec > 59) {
                min = parseInt(((rawSec / 60)).toString());
                sec = Math.round(((rawSec / 60) - min) * 60);
                return `${min} min ${sec} s`;
            } else {
                return `${Math.round(rawSec)} s`;
            }
        }catch(e){
            return "-";
        }
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
