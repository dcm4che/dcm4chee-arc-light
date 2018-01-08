import { Component, OnInit, ViewContainerRef } from '@angular/core';
import {AppService} from "../../app.service";
import {SlimLoadingBarService} from "ng2-slim-loading-bar";
import * as _ from 'lodash';
import {AeListService} from "../../ae-list/ae-list.service";
import {Observable} from "rxjs/Observable";
import {ExternalRetrieveService} from "./external-retrieve.service";
import {HttpErrorHandler} from "../../helpers/http-error-handler";
import {ExportDialogComponent} from "../../widgets/dialogs/export/export.component";
import {MdDialog, MdDialogConfig, MdDialogRef} from "@angular/material";
import {ConfirmComponent} from "../../widgets/dialogs/confirm/confirm.component";
import {DatePipe} from "@angular/common";
import {j4care} from "../../helpers/j4care.service";
import * as FileSaver from 'file-saver';

@Component({
  selector: 'external-retrieve',
  templateUrl: './external-retrieve.component.html'
})
export class ExternalRetrieveComponent implements OnInit {
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
    dialogRef: MdDialogRef<any>;
    exporters;
    exporterID;
    datePipe = new DatePipe('us-US');
    devices;
    count;
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
    constructor(
      public cfpLoadingBar: SlimLoadingBarService,
      public mainservice: AppService,
      public aeListService:AeListService,
      public service:ExternalRetrieveService,
      public httpErrorHandler:HttpErrorHandler,
      public dialog: MdDialog,
      public config: MdDialogConfig,
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

            /*

            status
            StudyInstanceUID
            RemoteAET
            DestinationAET
            scheduledTime
            processingStartTime
            processingEndTime,
            NumberOfInstances (failures + completed + warnings),

            Instance/sec (processingEndTime - processingStartTime)/NumberOfInstances
            */
        }
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
    }
    hasOlder(objs) {
        // console.log("objs.length",objs.length);
        // console.log("this.filterObject.limit",this.filterObject.limit);
        // console.log("hasOlder",(objs && (objs.length == this.filterObject.limit)));
        return objs && (objs.length == this.filterObject.limit);
    };
    hasNewer(objs) {
        // console.log("hasNewer",(objs && objs.length && objs[0].offset));
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
        this.service.downloadCsv(this.filterObject).subscribe((csv)=>{
            let file = new File([csv._body], `export_${new Date().toDateString()}.csv`, {type: 'text/csv;charset=utf-8'});
            FileSaver.saveAs(file);
        },(err)=>{
            this.httpErrorHandler.handleError(err);
        });
    }
    allActionChanged(e){
        let text = `Are you sure, you want to ${this.allAction} all matching tasks?`;
        let filter = Object.assign(this.filterObject);
        delete filter.limit;
        switch (this.allAction){
            case "cancel":
                this.confirm({
                    content: text
                }).subscribe((ok)=>{
                    if(ok){
                        this.cfpLoadingBar.start();
                        this.service.cancelAll(this.filterObject).subscribe((res)=>{
                            this.mainservice.setMessage({
                                'title': 'Info',
                                'text': res.cancel + ' queues deleted successfully!',
                                'status': 'info'
                            });
                            this.cfpLoadingBar.complete();
                        }, (err) => {
                            this.cfpLoadingBar.complete();
                            this.httpErrorHandler.handleError(err);
                        });
                    }
                    this.allAction = "";
                    this.allAction = undefined;
                });
                break;
            case "reschedule":
                this.confirm({
                    content: text
                }).subscribe((ok)=>{
                    if(ok){
                        this.cfpLoadingBar.start();
                        this.service.rescheduleAll(this.filterObject).subscribe((res)=>{
                            this.mainservice.setMessage({
                                'title': 'Info',
                                'text': res.reschedule + ' queues deleted successfully!',
                                'status': 'info'
                            });
                            this.cfpLoadingBar.complete();
                        }, (err) => {
                            this.cfpLoadingBar.complete();
                            this.httpErrorHandler.handleError(err);
                        });
                    }
                    this.allAction = "";
                    this.allAction = undefined;
                });
                break;
            case "delete":
                this.confirm({
                    content: text
                }).subscribe((ok)=>{
                    if(ok){
                        this.cfpLoadingBar.start();
                        this.service.deleteAll(this.filterObject).subscribe((res)=>{
                            this.mainservice.setMessage({
                                'title': 'Info',
                                'text': res.deleted + ' queues deleted successfully!',
                                'status': 'info'
                            });
                            this.cfpLoadingBar.complete();
                        }, (err) => {
                            this.cfpLoadingBar.complete();
                            this.httpErrorHandler.handleError(err);
                        });
                    }
                    this.allAction = "";
                    this.allAction = undefined;
                });
                break;
        }
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
                setTimeout(()=>{
                    this.getTasks(this.externalRetrieveEntries[0].offset || 0);
                    this.cfpLoadingBar.complete();
                },300);

            }
        });
    }
    onSubmit(object){
        if(_.hasIn(object,"id") && _.hasIn(object,"model")){
            if(_.hasIn(object,"model.createdTime")){
                object.model.createdTime = this.datePipe.transform(object.model.createdTime,'yyyyMMdd');
            }
            if(_.hasIn(object,"model.updatedTime")){
                object.model.updatedTime = this.datePipe.transform(object.model.updatedTime,'yyyyMMdd');
            }
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
                if (res && res.length > 0){
                    this.externalRetrieveEntries =  res.map((properties, index) => {
                        if (_.hasIn(properties, 'Modality')){
                            properties.Modality = properties.Modality.join(',');
                        }
                        properties.NumberOfInstances = ((properties.failed ? properties.failed*1:0) + (properties.completed ? properties.completed*1:0) + (properties.warning ? properties.warning*1:0));
                        try{
                            properties.InstancePerSec = Math.round((((new Date(properties.processingEndTime).getTime()/1000) - (new Date(properties.processingStartTime).getTime()/1000)) / properties.NumberOfInstances)*1000)/1000;
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
}
