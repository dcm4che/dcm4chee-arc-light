import {Component, OnDestroy, OnInit, ViewContainerRef} from '@angular/core';
import {LoadingBarService} from "@ngx-loading-bar/core";
import {AppService} from "../../app.service";
import * as _ from 'lodash';
import {StorageVerificationService} from "./storage-verification.service";
import {Globalvar} from "../../constants/globalvar";
import {j4care} from "../../helpers/j4care.service";
import {HttpErrorHandler} from "../../helpers/http-error-handler";
import {Observable} from "rxjs/Observable";
import {AeListService} from "../../ae-list/ae-list.service";
import {ConfirmComponent} from "../../widgets/dialogs/confirm/confirm.component";
import {WindowRefService} from "../../helpers/window-ref.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {MatDialog, MatDialogConfig, MatDialogRef} from "@angular/material";

@Component({
  selector: 'app-storage-verification',
  templateUrl: './storage-verification.component.html',
  styleUrls: ['./storage-verification.component.scss']
})
export class StorageVerificationComponent implements OnInit, OnDestroy {
    filterObject;
    filterSchema;
    localAET;
    destinationAET;
    remoteAET;
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
    dialogRef: MatDialogRef<any>;
    storageVerifications;
    externalRetrieveEntries;
    tableConfig;
    moreTasks;
    constructor(
      private cfpLoadingBar: LoadingBarService,
      private mainservice: AppService,
      private aeListService:AeListService,
      private httpErrorHandler:HttpErrorHandler,
      private service:StorageVerificationService,
      private $http:J4careHttpService,
      public dialog: MatDialog,
      public config: MatDialogConfig,
      public viewContainerRef: ViewContainerRef
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
        this.service.statusValues().forEach(val =>{
            this.statusValues[val.value] = {
                count: 0,
                loader: false
            };
        });
        this.filterObject = {
            limit:20,
            offset:0
        };
        Observable.forkJoin(
            this.aeListService.getAets(),
            this.service.getDevices()
        ).subscribe((response)=>{
            this.localAET = (<any[]>j4care.extendAetObjectWithAlias(response[0])).map(ae => {
                return {
                    value:ae.dicomAETitle,
                    text:ae.dicomAETitle
                }
            });
            this.devices = (<any[]>response[1])
                .filter(dev => dev.hasArcDevExt)
                .map(device => {
                    return {
                        value:device.dicomDeviceName,
                        text:device.dicomDeviceName
                    }
                });
            this.initSchema();
        });
        this.onFormChange(this.filterObject);
    }
    initSchema(){
        this.filterSchema = j4care.prepareFlatFilterObject(this.service.getFilterSchema( this.devices, this.localAET,`COUNT ${((this.count || this.count == 0)?this.count:'')}`),3);
/*        if(this.urlParam){
            this.filterObject = this.urlParam;
            this.filterObject["limit"] = 20;
            this.getTasks(0);
        }*/
        this.tableConfig = {
            table:j4care.calculateWidthOfTable(this.service.getTableSchema(this, this.action)),
            filter:this.filterObject
        };
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
    onSubmit(object){
        if(_.hasIn(object,"id") && _.hasIn(object,"model")){
            if(object.id === "submit"){
                let filter = Object.assign({},this.filterObject);
                if(filter['limit'])
                    filter['limit']++;
                this.getTasks(filter);
            }else{
                // this.getTasks(0);
                let filter = Object.assign({},this.filterObject);
                delete filter["limit"];
                delete filter["offset"];
                this.getVerificationCounts(filter);
            }
        }
    }
    getVerificationCounts(filter){
        this.cfpLoadingBar.start();
        this.service.getSorageVerificationsCount(filter).subscribe((count)=>{
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
    uploadCsv(){
      //TODO
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
    getCounts(){
        let filters = Object.assign({},this.filterObject);
        if(!this.tableHovered)
            this.getTasks(0);
        Object.keys(this.statusValues).forEach(status=>{
            filters.status = status;
            this.statusValues[status].loader = true;
            this.service.getSorageVerificationsCount(filters).subscribe((count)=>{
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
                    WindowRefService.nativeWindow.open(`../monitor/stgver?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&access_token=${token}&${this.mainservice.param(filterClone)}`);
                }else{
                    WindowRefService.nativeWindow.open(`../monitor/stgver?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&${this.mainservice.param(filterClone)}`);
                }
            });
        })
    }
    next(){
        if(this.moreTasks){
            let filter = Object.assign({},this.filterObject);
            if(filter['limit']){
                this.filterObject['offset'] = filter['offset'] = filter['offset']*1 + this.filterObject['limit']*1;
                filter['limit']++;
            }
            this.getTasks(filter);
        }
    }
    prev(){
        if(this.filterObject["offset"] > 0){
            let filter = Object.assign({},this.filterObject);
            if(filter['limit']){
                this.filterObject['offset'] = filter['offset'] = filter['offset']*1 - this.filterObject['limit']*1;
                filter['limit']++;
            }
            this.getTasks(filter);
        }
    }
    getTasks(filter){
        let $this = this;
        $this.cfpLoadingBar.start();
        this.service.getSorageVerifications(filter, this.batchGrouped).subscribe(
            res =>  {
                $this.cfpLoadingBar.complete();
                if (res && res.length > 0){
                    this.storageVerifications =  res;
                    $this.count = undefined;
                    if(this.batchGrouped){
                        this.tableConfig = {
                            table:j4care.calculateWidthOfTable(this.service.getTableBatchGroupedColumens((e)=>{
                                this.showDetails(e)
                            })),
                            filter:this.filterObject
                        };
                        this.storageVerifications = res.map(taskObject=>{
                            if(_.hasIn(taskObject, 'tasks')){
                                let taskPrepared = [];
                                Globalvar.TASK_NAMES.forEach(task=>{
                                    if(taskObject.tasks[task])
                                        taskPrepared.push({[task]:taskObject.tasks[task]});
                                });
                                taskObject.tasks = taskPrepared;
                            }
                            return taskObject;
                        });
                    }else{
                        this.tableConfig = {
                            table:j4care.calculateWidthOfTable(this.service.getTableSchema(this, this.action)),
                            filter:this.filterObject
                        };
                    }
                }else{
                    $this.cfpLoadingBar.complete();
                    $this.storageVerifications = [];
                    $this.mainservice.setMessage({
                        'title': 'Info',
                        'text': 'No tasks found!',
                        'status': 'info'
                    });
                }
                this.moreTasks = res.length > this.filterObject['limit'];
                if(this.moreTasks)
                    this.storageVerifications.splice(this.storageVerifications.length-1,1);
            },
            err => {
                $this.storageVerifications = [];
                $this.cfpLoadingBar.complete();
                $this.httpErrorHandler.handleError(err);
            });
    }
    showDetails(e){
        this.batchGrouped = false;
        this.filterObject['batchID'] = e.batchID;
        let filter = Object.assign({},this.filterObject);
        if(filter['limit'])
            filter['limit']++;
        this.getTasks(filter);
    }
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
    deleteAllTasks(filter){
        this.service.deleteAll(filter).subscribe((res)=>{
            this.mainservice.setMessage({
                'title': 'Info',
                'text': res.deleted + ' tasks deleted successfully!',
                'status': 'info'
            });
            this.cfpLoadingBar.complete();
            let filters = Object.assign({},this.filterObject);
            this.getTasks(filters);
        }, (err) => {
            this.cfpLoadingBar.complete();
            this.httpErrorHandler.handleError(err);
        });
    }
    action(mode, match){
        console.log("in action",mode,"match",match);
        if(mode && match && match.pk){
            this.confirm({
                content: `Are you sure you want to ${mode} this task?`
            }).subscribe(ok => {
                if (ok){
                    switch (mode) {
                        case 'reschedule':
                            this.cfpLoadingBar.start();
                            this.service.reschedule(match.pk)
                                .subscribe(
                                    (res) => {
                                        this.getTasks(this.filterObject['offset'] || 0);
                                        this.cfpLoadingBar.complete();
                                        this.mainservice.setMessage({
                                            'title': 'Info',
                                            'text': 'Task rescheduled successfully!',
                                            'status': 'info'
                                        });
                                    },
                                    (err) => {
                                        this.cfpLoadingBar.complete();
                                        this.httpErrorHandler.handleError(err);
                                    });
                            break;
                        case 'delete':
                            this.cfpLoadingBar.start();
                            this.service.delete(match.pk)
                                .subscribe(
                                    (res) => {
                                        // match.properties.status = 'CANCELED';
                                        this.cfpLoadingBar.complete();
                                        this.getTasks(this.filterObject['offset'] || 0);
                                        this.mainservice.setMessage({
                                            'title': 'Info',
                                            'text': 'Task deleted successfully!',
                                            'status': 'info'
                                        });
                                    },
                                    (err) => {
                                        this.cfpLoadingBar.complete();
                                        this.httpErrorHandler.handleError(err);
                                    });
                            break;
                        case 'cancel':
                            this.cfpLoadingBar.start();
                            this.service.cancel(match.pk)
                                .subscribe(
                                    (res) => {
                                        match.status = 'CANCELED';
                                        this.cfpLoadingBar.complete();
                                        this.mainservice.setMessage({
                                            'title': 'Info',
                                            'text': 'Task canceled successfully!',
                                            'status': 'info'
                                        });
                                    },
                                    (err) => {
                                        this.cfpLoadingBar.complete();
                                        console.log('cancleerr', err);
                                        this.httpErrorHandler.handleError(err);
                                    });
                            break;
                        default:
                            console.error("Not knowen mode=",mode);
                    }
                }
            });
        }
    }
    ngOnDestroy(){
        if(this.timer.started){
            this.timer.started = false;
            clearInterval(this.refreshInterval);
        }
        // localStorage.setItem('externalRetrieveFilters',JSON.stringify(this.filterObject));
    }
}
