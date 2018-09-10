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
            limit:20
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
            if(object.id === "count"){
                this.getCount();
            }else{
                // this.getTasks(0);
                this.getCounts();
            }
        }
    }
    getCount(){
        this.cfpLoadingBar.start();
        this.service.getSorageVerificationsCount(this.filterObject).subscribe((count)=>{
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
      //TODO
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
    getTasks(offset){
        let $this = this;
        $this.cfpLoadingBar.start();
        this.service.getSorageVerifications(this.filterObject,offset, this.batchGrouped).subscribe(
            res =>  {
                $this.cfpLoadingBar.complete();
                if (res && res.length > 0){
                    this.storageVerifications =  res.map((properties, index) => {
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
 /*                               if(!properties.processingEndTime)
                                    properties.approximatelyEndTime = Math.round((properties.remaining / properties.InstancePerSec)*100)/100 ? `${this.secToMinSecString((properties.remaining / properties.InstancePerSec))}`:'-';
 */                           }catch (e){
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
                    $this.storageVerifications = [];
                    $this.mainservice.setMessage({
                        'title': 'Info',
                        'text': 'No tasks found!',
                        'status': 'info'
                    });
                }
            },
            err => {
                $this.storageVerifications = [];
                $this.cfpLoadingBar.complete();
                $this.httpErrorHandler.handleError(err);
            });
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

    ngOnDestroy(){
        if(this.timer.started){
            this.timer.started = false;
            clearInterval(this.refreshInterval);
        }
        // localStorage.setItem('externalRetrieveFilters',JSON.stringify(this.filterObject));
    }
}
