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
import {Globalvar} from "../../constants/globalvar";
import {ActivatedRoute} from "@angular/router";

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
    urlParam;
    constructor(
      public cfpLoadingBar: LoadingBarService,
      public mainservice: AppService,
      public aeListService:AeListService,
      public service:ExternalRetrieveService,
      public httpErrorHandler:HttpErrorHandler,
      public dialog: MatDialog,
      public config: MatDialogConfig,
      public viewContainerRef: ViewContainerRef,
      private $http:J4careHttpService,
      private route: ActivatedRoute
    ) { }

    ngOnInit(){
        this.initCheck(10);
    }
    initCheck(retries){
        let $this = this;
        if(_.hasIn(this.mainservice,"global.authentication") || (_.hasIn(this.mainservice,"global.notSecure") && this.mainservice.global.notSecure)){

            this.route.queryParams.subscribe(params => {
                console.log("params",params);
                this.urlParam = Object.assign({},params);
                this.init();
            });
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
        if(this.urlParam){
            this.filterObject = this.urlParam;
            this.filterObject["limit"] = 20;
            this.getTasks(0);
        }
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
                                'text': res.count + ' tasks rescheduled successfully!',
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
            this.allAction = "";
            this.allAction = undefined;
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
    showTaskDetail(task){
        this.filterObject.batchID = task.properties.batchID;
        this.batchGrouped = false;
        this.getTasks(0);
    }
    getTasks(offset){
        let $this = this;
        $this.cfpLoadingBar.start();
        this.service.getExternalRetrieveEntries(this.filterObject,offset, this.batchGrouped).subscribe(
            res =>  {
                //[{"pk":"7379485","createdTime":"2018-06-22T12:26:31.444+0300","updatedTime":"2018-06-22T13:43:30.223+0300","LocalAET":"ARCHIVE1TLN","RemoteAET":"ARCACT1TLN_MIGR","DestinationAET":"EEVNATLN","StudyInstanceUID":"1.2.124.113532.192.168.100.131.20070423.114940.1365933","completed":"1","statusCode":"0000","queue":"CMoveSCU","JMSMessageID":"ID:5941889d-75fe-11e8-a098-0242ac110002","dicomDeviceName":"archive1tln","status":"COMPLETED","scheduledTime":"2018-06-22T12:26:31.441+0300","processingStartTime":"2018-06-22T13:42:26.735+0300","processingEndTime":"2018-06-22T13:43:30.225+0300","outcomeMessage":"Export STUDY[suid:1.2.124.113532.192.168.100.131.20070423.114940.1365933] from ARCACT1TLN_MIGR to EEVNATLN - completed:1"},{"pk":"7379454","createdTime":"2018-06-22T12:26:28.390+0300","updatedTime":"2018-06-22T13:42:26.725+0300","LocalAET":"ARCHIVE1TLN","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATLN","StudyInstanceUID":"10049310031116","completed":"1","statusCode":"0000","queue":"CMoveSCU","JMSMessageID":"ID:576faeae-75fe-11e8-a098-0242ac110002","dicomDeviceName":"archive1tln","status":"COMPLETED","scheduledTime":"2018-06-22T12:26:28.388+0300","processingStartTime":"2018-06-22T13:42:26.099+0300","processingEndTime":"2018-06-22T13:42:26.727+0300","outcomeMessage":"Export STUDY[suid:10049310031116] from ARCPAS1TRT_MIGR to EEVNATLN - completed:1"},{"pk":"7379422","createdTime":"2018-06-22T12:26:25.089+0300","updatedTime":"2018-06-22T13:42:26.090+0300","LocalAET":"ARCHIVE1TLN","RemoteAET":"MAS1TRT","DestinationAET":"EEVNATLN","StudyInstanceUID":"1.2.124.113532.19.29266.60041.20100217.111233.10921337","completed":"1","statusCode":"0000","queue":"CMoveSCU","JMSMessageID":"ID:5577d62e-75fe-11e8-a098-0242ac110002","dicomDeviceName":"archive1tln","status":"COMPLETED","scheduledTime":"2018-06-22T12:26:25.086+0300","processingStartTime":"2018-06-22T13:42:25.436+0300","processingEndTime":"2018-06-22T13:42:26.091+0300","outcomeMessage":"Export STUDY[suid:1.2.124.113532.19.29266.60041.20100217.111233.10921337] from MAS1TRT to EEVNATLN - completed:1"},{"pk":"7379386","createdTime":"2018-06-22T12:26:21.002+0300","updatedTime":"2018-06-22T13:42:25.427+0300","LocalAET":"ARCHIVE1TLN","RemoteAET":"MAS1TRT","DestinationAET":"EEVNATLN","StudyInstanceUID":"1.3.51.0.7.13968436934.62311.16989.39067.19359.33989.36362","completed":"2","statusCode":"0000","queue":"CMoveSCU","JMSMessageID":"ID:5308359a-75fe-11e8-a098-0242ac110002","dicomDeviceName":"archive1tln","status":"COMPLETED","scheduledTime":"2018-06-22T12:26:21.000+0300","processingStartTime":"2018-06-22T13:42:23.848+0300","processingEndTime":"2018-06-22T13:42:25.428+0300","outcomeMessage":"Export STUDY[suid:1.3.51.0.7.13968436934.62311.16989.39067.19359.33989.36362] from MAS1TRT to EEVNATLN - completed:2"},{"pk":"7379354","createdTime":"2018-06-22T12:26:17.660+0300","updatedTime":"2018-06-22T13:42:23.832+0300","LocalAET":"ARCHIVE1TLN","RemoteAET":"MAS1TRT","DestinationAET":"EEVNATLN","StudyInstanceUID":"1.3.51.0.7.13968436934.62311.16989.39067.19359.33989.36362","completed":"2","statusCode":"0000","queue":"CMoveSCU","JMSMessageID":"ID:5109f47a-75fe-11e8-a098-0242ac110002","dicomDeviceName":"archive1tln","status":"COMPLETED","scheduledTime":"2018-06-22T12:26:17.656+0300","processingStartTime":"2018-06-22T13:42:22.042+0300","processingEndTime":"2018-06-22T13:42:23.840+0300","outcomeMessage":"Export STUDY[suid:1.3.51.0.7.13968436934.62311.16989.39067.19359.33989.36362] from MAS1TRT to EEVNATLN - completed:2"},{"pk":"7379321","createdTime":"2018-06-22T12:26:14.337+0300","updatedTime":"2018-06-22T13:42:22.031+0300","LocalAET":"ARCHIVE1TLN","RemoteAET":"ARCACT1TLN_MIGR","DestinationAET":"EEVNATLN","StudyInstanceUID":"1.2.840.113619.2.55.3.2831166052.15.1200891260.991","completed":"438","statusCode":"0000","queue":"CMoveSCU","JMSMessageID":"ID:4f0f36c9-75fe-11e8-a098-0242ac110002","dicomDeviceName":"archive1tln","status":"COMPLETED","scheduledTime":"2018-06-22T12:26:14.335+0300","processingStartTime":"2018-06-22T13:41:58.054+0300","processingEndTime":"2018-06-22T13:42:22.033+0300","outcomeMessage":"Export STUDY[suid:1.2.840.113619.2.55.3.2831166052.15.1200891260.991] from ARCACT1TLN_MIGR to EEVNATLN - completed:438"},{"pk":"7379284","createdTime":"2018-06-22T12:26:10.778+0300","updatedTime":"2018-06-22T13:41:58.044+0300","LocalAET":"ARCHIVE1TLN","RemoteAET":"MAS1TRT","DestinationAET":"EEVNATLN","StudyInstanceUID":"1.2.840.113619.2.55.3.2831166052.15.1200891260.991","completed":"438","statusCode":"0000","queue":"CMoveSCU","JMSMessageID":"ID:4cf04e44-75fe-11e8-a098-0242ac110002","dicomDeviceName":"archive1tln","status":"COMPLETED","scheduledTime":"2018-06-22T12:26:10.776+0300","processingStartTime":"2018-06-22T13:41:27.999+0300","processingEndTime":"2018-06-22T13:41:58.047+0300","outcomeMessage":"Export STUDY[suid:1.2.840.113619.2.55.3.2831166052.15.1200891260.991] from MAS1TRT to EEVNATLN - completed:438"},{"pk":"7379250","createdTime":"2018-06-22T12:26:07.267+0300","updatedTime":"2018-06-22T13:41:27.988+0300","LocalAET":"ARCHIVE1TLN","RemoteAET":"MAS1TRT","DestinationAET":"EEVNATLN","StudyInstanceUID":"1.3.51.0.7.4249694433.55527.4417.43132.49927.13272.61857","completed":"1","statusCode":"0000","queue":"CMoveSCU","JMSMessageID":"ID:4ad86aa2-75fe-11e8-a098-0242ac110002","dicomDeviceName":"archive1tln","status":"COMPLETED","scheduledTime":"2018-06-22T12:26:07.264+0300","processingStartTime":"2018-06-22T13:41:26.986+0300","processingEndTime":"2018-06-22T13:41:27.991+0300","outcomeMessage":"Export STUDY[suid:1.3.51.0.7.4249694433.55527.4417.43132.49927.13272.61857] from MAS1TRT to EEVNATLN - completed:1"},{"pk":"7379200","createdTime":"2018-06-22T12:26:02.138+0300","updatedTime":"2018-06-22T13:41:26.969+0300","LocalAET":"ARCHIVE1TLN","RemoteAET":"MAS1TRT","DestinationAET":"EEVNATLN","StudyInstanceUID":"1.2.840.113619.2.55.3.279719183.51.1232598781.305","completed":"5820","failed":"9","statusCode":"B000","queue":"CMoveSCU","JMSMessageID":"ID:47c9f1f0-75fe-11e8-a098-0242ac110002","dicomDeviceName":"archive1tln","status":"WARNING","scheduledTime":"2018-06-22T12:26:02.136+0300","processingStartTime":"2018-06-22T13:28:00.609+0300","processingEndTime":"2018-06-22T13:41:26.977+0300","outcomeMessage":"Export STUDY[suid:1.2.840.113619.2.55.3.279719183.51.1232598781.305] from MAS1TRT to EEVNATLN - completed:5820, failed:9"},{"pk":"7379484","createdTime":"2018-06-22T12:26:31.357+0300","updatedTime":"2018-06-22T13:38:09.487+0300","LocalAET":"ARCHIVE1TLN","RemoteAET":"MAS1TRT","DestinationAET":"EEVNATLN","StudyInstanceUID":"1.2.124.113532.192.168.100.131.20070423.114940.1365933","completed":"1","statusCode":"0000","queue":"CMoveSCU","JMSMessageID":"ID:5934693c-75fe-11e8-a098-0242ac110002","dicomDeviceName":"archive1tln","status":"COMPLETED","scheduledTime":"2018-06-22T12:26:31.355+0300","processingStartTime":"2018-06-22T13:38:08.605+0300","processingEndTime":"2018-06-22T13:38:09.490+0300","outcomeMessage":"Export STUDY[suid:1.2.124.113532.192.168.100.131.20070423.114940.1365933] from MAS1TRT to EEVNATLN - completed:1"},{"pk":"7379453","createdTime":"2018-06-22T12:26:28.301+0300","updatedTime":"2018-06-22T13:38:08.593+0300","LocalAET":"ARCHIVE1TLN","RemoteAET":"ARCACT1TLN_MIGR","DestinationAET":"EEVNATLN","StudyInstanceUID":"10049310031116","completed":"1","statusCode":"0000","queue":"CMoveSCU","JMSMessageID":"ID:5761f30d-75fe-11e8-a098-0242ac110002","dicomDeviceName":"archive1tln","status":"COMPLETED","scheduledTime":"2018-06-22T12:26:28.298+0300","processingStartTime":"2018-06-22T13:38:08.108+0300","processingEndTime":"2018-06-22T13:38:08.596+0300","outcomeMessage":"Export STUDY[suid:10049310031116] from ARCACT1TLN_MIGR to EEVNATLN - completed:1"},{"pk":"7379421","createdTime":"2018-06-22T12:26:24.998+0300","updatedTime":"2018-06-22T13:38:08.096+0300","LocalAET":"ARCHIVE1TLN","RemoteAET":"MAS1TLN","DestinationAET":"EEVNATLN","StudyInstanceUID":"1.2.124.113532.19.29266.60041.20100217.111233.10921337","completed":"1","statusCode":"0000","queue":"CMoveSCU","JMSMessageID":"ID:556a1a8d-75fe-11e8-a098-0242ac110002","dicomDeviceName":"archive1tln","status":"COMPLETED","scheduledTime":"2018-06-22T12:26:24.996+0300","processingStartTime":"2018-06-22T13:38:07.327+0300","processingEndTime":"2018-06-22T13:38:08.098+0300","outcomeMessage":"Export STUDY[suid:1.2.124.113532.19.29266.60041.20100217.111233.10921337] from MAS1TLN to EEVNATLN - completed:1"},{"pk":"7379385","createdTime":"2018-06-22T12:26:20.920+0300","updatedTime":"2018-06-22T13:38:07.316+0300","LocalAET":"ARCHIVE1TLN","RemoteAET":"MAS1TLN","DestinationAET":"EEVNATLN","StudyInstanceUID":"1.3.51.0.7.13968436934.62311.16989.39067.19359.33989.36362","completed":"2","statusCode":"0000","queue":"CMoveSCU","JMSMessageID":"ID:52fbd989-75fe-11e8-a098-0242ac110002","dicomDeviceName":"archive1tln","status":"COMPLETED","scheduledTime":"2018-06-22T12:26:20.918+0300","processingStartTime":"2018-06-22T13:38:05.582+0300","processingEndTime":"2018-06-22T13:38:07.319+0300","outcomeMessage":"Export STUDY[suid:1.3.51.0.7.13968436934.62311.16989.39067.19359.33989.36362] from MAS1TLN to EEVNATLN - completed:2"},{"pk":"7379353","createdTime":"2018-06-22T12:26:17.571+0300","updatedTime":"2018-06-22T13:38:05.570+0300","LocalAET":"ARCHIVE1TLN","RemoteAET":"MAS1TLN","DestinationAET":"EEVNATLN","StudyInstanceUID":"1.3.51.0.7.13968436934.62311.16989.39067.19359.33989.36362","completed":"2","statusCode":"0000","queue":"CMoveSCU","JMSMessageID":"ID:50fcae09-75fe-11e8-a098-0242ac110002","dicomDeviceName":"archive1tln","status":"COMPLETED","scheduledTime":"2018-06-22T12:26:17.569+0300","processingStartTime":"2018-06-22T13:38:03.645+0300","processingEndTime":"2018-06-22T13:38:05.573+0300","outcomeMessage":"Export STUDY[suid:1.3.51.0.7.13968436934.62311.16989.39067.19359.33989.36362] from MAS1TLN to EEVNATLN - completed:2"},{"pk":"7379320","createdTime":"2018-06-22T12:26:14.229+0300","updatedTime":"2018-06-22T13:38:03.634+0300","LocalAET":"ARCHIVE1TLN","RemoteAET":"MAS1TRT","DestinationAET":"EEVNATLN","StudyInstanceUID":"1.2.840.113619.2.55.3.2831166052.15.1200891260.991","completed":"438","statusCode":"0000","queue":"CMoveSCU","JMSMessageID":"ID:4efebc08-75fe-11e8-a098-0242ac110002","dicomDeviceName":"archive1tln","status":"COMPLETED","scheduledTime":"2018-06-22T12:26:14.226+0300","processingStartTime":"2018-06-22T13:37:33.382+0300","processingEndTime":"2018-06-22T13:38:03.636+0300","outcomeMessage":"Export STUDY[suid:1.2.840.113619.2.55.3.2831166052.15.1200891260.991] from MAS1TRT to EEVNATLN - completed:438"},{"pk":"7379283","createdTime":"2018-06-22T12:26:10.695+0300","updatedTime":"2018-06-22T13:37:33.371+0300","LocalAET":"ARCHIVE1TLN","RemoteAET":"MAS1TLN","DestinationAET":"EEVNATLN","StudyInstanceUID":"1.2.840.113619.2.55.3.2831166052.15.1200891260.991","completed":"438","statusCode":"0000","queue":"CMoveSCU","JMSMessageID":"ID:4ce37d03-75fe-11e8-a098-0242ac110002","dicomDeviceName":"archive1tln","status":"COMPLETED","scheduledTime":"2018-06-22T12:26:10.692+0300","processingStartTime":"2018-06-22T13:37:00.024+0300","processingEndTime":"2018-06-22T13:37:33.374+0300","outcomeMessage":"Export STUDY[suid:1.2.840.113619.2.55.3.2831166052.15.1200891260.991] from MAS1TLN to EEVNATLN - completed:438"},{"pk":"7379248","createdTime":"2018-06-22T12:26:07.057+0300","updatedTime":"2018-06-22T13:37:00.012+0300","LocalAET":"ARCHIVE1TLN","RemoteAET":"MAS1TRT","DestinationAET":"EEVNATLN","StudyInstanceUID":"1.3.12.2.1107.5.13.2.2262.20212.0.3772328611202502","completed":"7","statusCode":"0000","queue":"CMoveSCU","JMSMessageID":"ID:4ab85f80-75fe-11e8-a098-0242ac110002","dicomDeviceName":"archive1tln","status":"COMPLETED","scheduledTime":"2018-06-22T12:26:07.054+0300","processingStartTime":"2018-06-22T13:36:52.806+0300","processingEndTime":"2018-06-22T13:37:00.015+0300","outcomeMessage":"Export STUDY[suid:1.3.12.2.1107.5.13.2.2262.20212.0.3772328611202502] from MAS1TRT to EEVNATLN - completed:7"},{"pk":"7379199","createdTime":"2018-06-22T12:26:02.057+0300","updatedTime":"2018-06-22T13:36:52.795+0300","LocalAET":"ARCHIVE1TLN","RemoteAET":"MAS1TLN","DestinationAET":"EEVNATLN","StudyInstanceUID":"1.2.840.113619.2.55.3.279719183.51.1232598781.305","completed":"5816","failed":"13","statusCode":"B000","queue":"CMoveSCU","JMSMessageID":"ID:47bd95df-75fe-11e8-a098-0242ac110002","dicomDeviceName":"archive1tln","status":"WARNING","scheduledTime":"2018-06-22T12:26:02.055+0300","processingStartTime":"2018-06-22T13:27:59.407+0300","processingEndTime":"2018-06-22T13:36:52.796+0300","outcomeMessage":"Export STUDY[suid:1.2.840.113619.2.55.3.279719183.51.1232598781.305] from MAS1TLN to EEVNATLN - completed:5816, failed:13"},{"pk":"7379474","createdTime":"2018-06-22T12:26:30.312+0300","updatedTime":"2018-06-22T13:31:46.720+0300","LocalAET":"ARCHIVE1TLN","RemoteAET":"ARCPAS1TRT_MIGR","DestinationAET":"EEVNATLN","StudyInstanceUID":"1.2.840.113564.3.1.2.192.168.1.210.2007030914395535761","completed":"1","statusCode":"0000","queue":"CMoveSCU","JMSMessageID":"ID:5894f4e2-75fe-11e8-a098-0242ac110002","dicomDeviceName":"archive1tln","status":"COMPLETED","scheduledTime":"2018-06-22T12:26:30.310+0300","processingStartTime":"2018-06-22T13:31:46.212+0300","processingEndTime":"2018-06-22T13:31:46.724+0300","outcomeMessage":"Export STUDY[suid:1.2.840.113564.3.1.2.192.168.1.210.2007030914395535761] from ARCPAS1TRT_MIGR to EEVNATLN - completed:1"},{"pk":"7379439","createdTime":"2018-06-22T12:26:26.804+0300","updatedTime":"2018-06-22T13:31:46.200+0300","LocalAET":"ARCHIVE1TLN","RemoteAET":"MAS1TLN","DestinationAET":"EEVNATLN","StudyInstanceUID":"1.2.250.1.59.470.13.452.20100205092312.7773.141","completed":"2","statusCode":"0000","queue":"CMoveSCU","JMSMessageID":"ID:567d866f-75fe-11e8-a098-0242ac110002","dicomDeviceName":"archive1tln","status":"COMPLETED","scheduledTime":"2018-06-22T12:26:26.801+0300","processingStartTime":"2018-06-22T13:31:44.918+0300","processingEndTime":"2018-06-22T13:31:46.203+0300","outcomeMessage":"Export STUDY[suid:1.2.250.1.59.470.13.452.20100205092312.7773.141] from MAS1TLN to EEVNATLN - completed:2"}]
/*
                res = [{"batchID":"test12","tasks":{
                    "completed":60,
                    "warning":24,
                    "failed":12,
                    "in-process":5,
                    "scheduled":123,
                    "canceled":26
                },"dicomDeviceName":["dcm4chee-arc", "dcm4chee-arc2"],"LocalAET":["DCM4CHEE"],"RemoteAET":["DCM4CHEE"],"DestinationAET":["DCM4CHEE"],"createdTimeRange":["2018-04-10 18:02:06.936","2018-04-10 18:02:07.049"],"updatedTimeRange":["2018-04-10 18:02:08.300311","2018-04-10 18:02:08.553547"],"scheduledTimeRange":["2018-04-10 18:02:06.935","2018-04-10 18:02:07.049"],"processingStartTimeRange":["2018-04-10 18:02:06.989","2018-04-10 18:02:07.079"],"processingEndTimeRange":["2018-04-10 18:02:08.31","2018-04-10 18:02:08.559"]},{"batchID":"test2","tasks":{"completed":"12","failed":3,"warning":34},"dicomDeviceName":["dcm4chee-arc"],"LocalAET":["DCM4CHEE"],"RemoteAET":["DCM4CHEE"],"DestinationAET":["DCM4CHEE"],"createdTimeRange":["2018-04-10 18:02:25.71","2018-04-10 18:02:26.206"],"updatedTimeRange":["2018-04-10 18:02:25.932859","2018-04-10 18:02:27.335741"],"scheduledTimeRange":["2018-04-10 18:02:25.709","2018-04-10 18:02:26.204"],"processingStartTimeRange":["2018-04-10 18:02:25.739","2018-04-10 18:02:26.622"],"processingEndTimeRange":["2018-04-10 18:02:25.943","2018-04-10 18:02:27.344"]}];
*/


                $this.cfpLoadingBar.complete();
                if (res && res.length > 0){
                    this.externalRetrieveEntries =  res.map((properties, index) => {
                        if(this.batchGrouped){
                            let propertiesAttr = Object.assign({},properties);
                            if(_.hasIn(properties, 'tasks')){
                                let taskPrepared = [];
                                Globalvar.TASK_NAMES.forEach(task=>{
                                 if(properties.tasks[task])
                                     taskPrepared.push({[task]:properties.tasks[task]});
                                });
                                properties.tasks = taskPrepared;
                            }
                            j4care.stringifyArrayOrObject(properties, ['tasks']);
                            j4care.stringifyArrayOrObject(propertiesAttr,[]);
                            return {
                                offset: offset + index,
                                properties: properties,
                                propertiesAttr: propertiesAttr,
                                showProperties: false
                            };
                        }else{
                            if (_.hasIn(properties, 'Modality')){
                                properties.Modality = properties.Modality.join(', ');
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
                            return {
                                offset: offset + index,
                                properties: properties,
                                propertiesAttr: properties,
                                showProperties: false
                            };
                        }
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
        if(this.timer.started){
            this.timer.started = false;
            clearInterval(this.refreshInterval);
        }
        // localStorage.setItem('externalRetrieveFilters',JSON.stringify(this.filterObject));
    }
}
