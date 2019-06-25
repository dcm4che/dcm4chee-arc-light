import {Component, OnDestroy, OnInit, ViewContainerRef} from '@angular/core';
import {AppService} from "../../app.service";
import * as _ from 'lodash';
import {AeListService} from "../../configuration/ae-list/ae-list.service";
import {Observable} from "rxjs/Observable";
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
import {CsvUploadComponent} from "../../widgets/dialogs/csv-upload/csv-upload.component";
import {Globalvar} from "../../constants/globalvar";
import {ActivatedRoute} from "@angular/router";
import {PermissionService} from "../../helpers/permissions/permission.service";
import {Validators} from "@angular/forms";
import {AppComponent} from "../../app.component";
import {DropdownList} from "../../helpers/form/dropdown-list";
import {SelectDropdown} from "../../interfaces";
import {RetrieveMonitoringService} from "./retrieve-monitoring.service";
import {DevicesService} from "../../configuration/devices/devices.service";
import {KeycloakService} from "../../helpers/keycloak-service/keycloak.service";

@Component({
  selector: 'retrieve-monitoring',
  templateUrl: './retrieve-monitoring.component.html'
})
export class RetrieveMonitoringComponent implements OnInit,OnDestroy {
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
    queueNames;
    constructor(
      public cfpLoadingBar: LoadingBarService,
      public mainservice: AppService,
      private appComponent:AppComponent,
      private $http:J4careHttpService,
      private route: ActivatedRoute,
      public aeListService:AeListService,
      public service:RetrieveMonitoringService,
      public httpErrorHandler:HttpErrorHandler,
      public dialog: MatDialog,
      public config: MatDialogConfig,
      public viewContainerRef: ViewContainerRef,
      private permissionService:PermissionService,
      private deviceService:DevicesService,
      private _keycloakService: KeycloakService
    ) { }

    ngOnInit(){
        this.initCheck(10);
    }
    initCheck(retries){
        let $this = this;
        if((KeycloakService.keycloakAuth && KeycloakService.keycloakAuth.authenticated) || (_.hasIn(this.mainservice,"global.notSecure") && this.mainservice.global.notSecure)){

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
        this.getQueueNames();
        this.appComponent.setServerTime();
        this.service.statusValues().forEach(val =>{
            this.statusValues[val.value] = {
                count: 0,
                loader: false
            };
        });
/*        if (!this.mainservice.user){
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
        }*/
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
            this.aeListService.getAes().map(aet=> this.permissionService.filterAetDependingOnUiConfig(aet,'external')),
            this.aeListService.getAets().map(aet=> this.permissionService.filterAetDependingOnUiConfig(aet,'internal')),
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
        this.filterSchema = this.service.getFilterSchema(this.localAET,this.destinationAET,this.remoteAET, this.devices,`COUNT ${((this.count || this.count == 0)?this.count:'')}`, this.queueNames);
        if(this.urlParam){
            // this.filterObject = this.urlParam;
            _.extend(this.filterObject, this.urlParam);
            this.filterObject["limit"] = 20;
            // this.getTasks(0);
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
            this._keycloakService.getToken().subscribe((response)=>{
                if(!this.mainservice.global.notSecure){
                    token = response.token;
                }
                let filterClone = _.cloneDeep(this.filterObject);
                delete filterClone.offset;
                delete filterClone.limit;
                if(!this.mainservice.global.notSecure){
                    // WindowRefService.nativeWindow.open(`../monitor/retrieve?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&access_token=${token}${Object.keys(filterClone).length > 0 ?'&':''}${this.mainservice.param(filterClone)}`);
                    j4care.downloadFile(`../monitor/retrieve?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&access_token=${token}${Object.keys(filterClone).length > 0 ?'&':''}${this.mainservice.param(filterClone)}`,"retrieve.csv")
                }else{
                    // WindowRefService.nativeWindow.open(`../monitor/retrieve?accept=text/csv${(semicolon?';delimiter=semicolon':'')}${Object.keys(filterClone).length > 0 ?'&':''}${this.mainservice.param(filterClone)}`);
                    j4care.downloadFile(`../monitor/retrieve?accept=text/csv${(semicolon?';delimiter=semicolon':'')}${Object.keys(filterClone).length > 0 ?'&':''}${this.mainservice.param(filterClone)}`,"retrieve.csv")
                }
            });
        })
    }
    uploadCsv(){
        this.dialogRef = this.dialog.open(CsvUploadComponent, {
            height: 'auto',
            width: '500px'
        });
        this.dialogRef.componentInstance.aes = this.remoteAET;
        this.dialogRef.componentInstance.params = {
            LocalAET:this.filterObject['LocalAET']||'',
            RemoteAET:this.filterObject['RemoteAET']||'',
            DestinationAET:this.filterObject['DestinationAET']||'',
            batchID:this.filterObject['batchID']||'',
            formSchema:[
                {
                    tag:"input",
                    type:"checkbox",
                    filterKey:"withoutScheduling",
                    description:"Without Scheduling"
                },
                {
                    tag:"input",
                    type:"checkbox",
                    filterKey:"semicolon",
                    description:"Use semicolon as delimiter"
                },
                {
                    tag:"select",
                    options:this.remoteAET,
                    showStar:true,
                    filterKey:"LocalAET",
                    description:"Local AET",
                    placeholder:"Local AET",
                    validation:Validators.required
                },{
                    tag:"select",
                    options:this.remoteAET,
                    showStar:true,
                    filterKey:"RemoteAET",
                    description:"Romote AET",
                    placeholder:"Romote AET",
                    validation:Validators.required
                },{
                    tag:"input",
                    type:"number",
                    filterKey:"field",
                    description:"Field",
                    placeholder:"Field",
                    validation:Validators.minLength(1),
                    defaultValue:1
                },{
                    tag:"select",
                    options:this.remoteAET,
                    showStar:true,
                    filterKey:"DestinationAET",
                    description:"Destination AET",
                    placeholder:"Destination AET",
                    validation:Validators.required
                }
                ,{
                    tag:"select",
                    options:this.queueNames,
                    showStar:true,
                    filterKey:"dcmQueueName",
                    placeholder:"Queue Name",
                    description:"Queue Name"
                }
                ,{
                    tag:"input",
                    type:"number",
                    filterKey:"priority",
                    description:"Priority",
                    placeholder:"Priority"
                },
                {
                    tag:"input",
                    type:"text",
                    filterKey:"batchID",
                    description:"Batch ID",
                    placeholder:"Batch ID"
                }
            ],
            prepareUrl:(filter)=>{
                let clonedFilters = {};
                if(filter['priority']) clonedFilters['priority'] = filter['priority'];
                if(filter['batchID']) clonedFilters['batchID'] = filter['batchID'];
                if(filter['dcmQueueName']) clonedFilters['dcmQueueName'] = filter['dcmQueueName'];
                if(filter.withoutScheduling){
                    return `../aets/${filter.LocalAET}/dimse/${filter.RemoteAET}/studies/csv:${filter.field}/mark4retrieve/dicom:${filter.DestinationAET}${j4care.getUrlParams(clonedFilters)}`;
                }else{
                    return `../aets/${filter.LocalAET}/dimse/${filter.RemoteAET}/studies/csv:${filter.field}/export/dicom:${filter.DestinationAET}${j4care.getUrlParams(clonedFilters)}`;
                }
            }
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
                                    'text': res.count + ' tasks canceled successfully!',
                                    'status': 'info'
                                });
                                this.cfpLoadingBar.complete();
                            }, (err) => {
                                this.cfpLoadingBar.complete();
                                this.httpErrorHandler.handleError(err);
                            });

                    break;
                case "reschedule":
                    this.deviceService.selectDevice((res)=>{
                            if(res){
                                let filter = Object.assign({},this.filterObject);
                                if(_.hasIn(res, "schema_model.newDeviceName") && res.schema_model.newDeviceName != ""){
                                    filter["newDeviceName"] = res.schema_model.newDeviceName;
                                }
                                this.service.rescheduleAll(filter).subscribe((res)=>{
                                    this.mainservice.showMsg(res.count + ' tasks rescheduled successfully!');
                                    this.cfpLoadingBar.complete();
                                }, (err) => {
                                    this.cfpLoadingBar.complete();
                                    this.httpErrorHandler.handleError(err);
                                });
                            }
                        },
                        this.devices);

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
    deleteBatchedTask(batchedTask){
        this.confirm({
            content: 'Are you sure you want to delete all tasks to this batch?'
        }).subscribe(ok=>{
            if(ok){
                if(batchedTask.properties.batchID){
                    let filter = Object.assign({},this.filterObject);
                    filter["batchID"] = batchedTask.properties.batchID;
                    delete filter["limit"];
                    delete filter["offset"];
                    this.service.deleteAll(filter).subscribe((res)=>{
                        this.mainservice.setMessage({
                            'title': 'Info',
                            'text': res.deleted + ' tasks deleted successfully!',
                            'status': 'info'
                        });
                        this.cfpLoadingBar.complete();
                        this.getTasks(0)
                    }, (err) => {
                        this.cfpLoadingBar.complete();
                        this.httpErrorHandler.handleError(err);
                    });
                }else{
                    this.mainservice.setMessage({
                        'title': 'Error',
                        'text': 'Batch ID not found!',
                        'status': 'error'
                    });
                }
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
 /*       this.allActionsActive = this.allActionsOptions.filter((o)=>{
            if(filters.status == "SCHEDULED" || filters.status == "IN PROCESS"){
                return o.value != 'reschedule';
            }else{
                if(filters.status === '*' || !filters.status || filters.status === '')
                    return o.value != 'cancel' && o.value != 'reschedule';
                else
                    return o.value != 'cancel';
            }
        });*/
    }
    reschedule(match) {
        let $this = this;
        let parameters: any = {
            content: 'Are you sure you want to reschedule this task?'
        };
        this.confirm(parameters).subscribe(result => {
            if (result){
                this.deviceService.selectDevice((res)=>{
                        if(res){
                            let filter = {};
                            if(_.hasIn(res, "schema_model.newDeviceName") && res.schema_model.newDeviceName != ""){
                                filter["newDeviceName"] = res.schema_model.newDeviceName;
                            }
                            $this.cfpLoadingBar.start();
                            this.service.reschedule(match.properties.pk, filter)
                                .subscribe(
                                    (res) => {
                                        $this.getTasks(match.offset||0);
                                        $this.cfpLoadingBar.complete();
                                        $this.mainservice.showMsg('Task rescheduled successfully!');
                                    },
                                    (err) => {
                                        $this.cfpLoadingBar.complete();
                                        $this.httpErrorHandler.handleError(err);
                                    });
                        }
                    },
                    this.devices.map(device=>{
                        return {
                            text:device.dicomDeviceName,
                            value:device.dicomDeviceName
                        }
                    }));
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
                setTimeout(()=>{
                    this.getTasks(this.externalRetrieveEntries[0].offset || 0);
                    this.cfpLoadingBar.complete();
                },300);
            }
        });
    }
    onSubmit(object){
        if(_.hasIn(object,"id") && _.hasIn(object,"model")){
            this.filterObject = object.model;
            if(object.id === "count"){
                this.getCount();
            }else{
                // this.getTasks(0);
                this.getCounts();
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
                            let endTime:Date =  properties.processingEndTime ? new Date(properties.processingEndTime) :  this.mainservice.serverTime;
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
            this.urlParam = {};
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
/*    getDevices(){
        this.cfpLoadingBar.start();
        this.service.getDevices().subscribe(devices=>{
            this.cfpLoadingBar.complete();
            this.devices = devices.filter(dev => dev.hasArcDevExt);
        },(err)=>{
            this.cfpLoadingBar.complete();
            console.error("Could not get devices",err);
        });
    }*/
    getQueueNames(){
        this.service.getQueueNames().subscribe(names=>{
            this.queueNames = names.filter(name=> name.name.toLowerCase().indexOf("retrieve") > -1).map(name=> new SelectDropdown(name.name, name.description));
        },err=>{
            this.httpErrorHandler.handleError(err);
        })
    }
    ngOnDestroy(){
        if(this.timer.started){
            this.timer.started = false;
            clearInterval(this.refreshInterval);
        }
        // localStorage.setItem('externalRetrieveFilters',JSON.stringify(this.filterObject));
    }
}
