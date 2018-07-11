import {Component, OnInit, ViewContainerRef} from '@angular/core';
import {DiffMonitorService} from "./diff-monitor.service";
import {AppService} from "../../app.service";
import {ActivatedRoute} from "@angular/router";
import * as _ from 'lodash';
import {LoadingBarService} from "@ngx-loading-bar/core";
import {AeListService} from "../../ae-list/ae-list.service";
import {Observable} from "rxjs/Observable";
import {j4care} from "../../helpers/j4care.service";
import {HttpErrorHandler} from "../../helpers/http-error-handler";
import {WindowRefService} from "../../helpers/window-ref.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {ConfirmComponent} from "../../widgets/dialogs/confirm/confirm.component";
import {MatDialogConfig, MatDialog, MatDialogRef} from "@angular/material";
import {Globalvar} from "../../constants/globalvar";

@Component({
    selector: 'diff-monitor',
    templateUrl: './diff-monitor.component.html',
    styleUrls: ['./diff-monitor.component.scss']
})
export class DiffMonitorComponent implements OnInit {

    filterObject = {};
    filterSchema;
    urlParam;
    aes;
    aets;
    devices;
    batchGrouped = false;
    tasks = [];
    config;
    moreTasks;
    dialogRef: MatDialogRef<any>;
    count;
    statusValues = {};
    refreshInterval;
    interval = 10;
    timer = {
        started:false,
        startText:"Start Auto Refresh",
        stopText:"Stop Auto Refresh"
    };
    Object = Object;
    tableHovered = false;
    constructor(
        private service:DiffMonitorService,
        private mainservice:AppService,
        private route: ActivatedRoute,
        private cfpLoadingBar: LoadingBarService,
        private aeListService:AeListService,
        private httpErrorHandler:HttpErrorHandler,
        private $http:J4careHttpService,
        public viewContainerRef: ViewContainerRef,
        public dialog: MatDialog,
        public dialogConfig: MatDialogConfig,
    ){}

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
            this.aeListService.getAes(),
            this.aeListService.getAets(),
            this.service.getDevices()
        ).subscribe((response)=>{
            this.aes = (<any[]>j4care.extendAetObjectWithAlias(response[0])).map(ae => {
                return {
                  value:ae.dicomAETitle,
                  text:ae.dicomAETitle
                }
            });
            this.aets = (<any[]>j4care.extendAetObjectWithAlias(response[1])).map(ae => {
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
    }
    initSchema(){
        this.filterSchema = j4care.prepareFlatFilterObject(this.service.getFormSchema(this.aes, this.aets,`COUNT ${((this.count || this.count == 0)?this.count:'')}`,this.devices),3);
    }
    onSubmit(e){
        console.log("e",e);
        //[{"pk":"1","LocalAET":"DCM4CHEE2","PrimaryAET":"DCM4CHEE2","SecondaryAET":"DCM4CHEE_ADMIN","QueryString":"includefield=all&offset=0&limit=21&returnempty=false&queue=true&missing=true&different=true&batchID=test","checkMissing":true,"checkDifferent":true,"matches":31,"createdTime":"2018-06-06T14:03:04.136+0200","updatedTime":"2018-06-06T14:03:04.701+0200","queue":"DiffTasks","JMSMessageID":"ID:911a5646-6981-11e8-897e-0242ac120003","dicomDeviceName":"dcm4chee-arc","status":"COMPLETED","batchID":"test","scheduledTime":"2018-06-06T14:03:04.121+0200","processingStartTime":"2018-06-06T14:03:04.264+0200","processingEndTime":"2018-06-06T14:03:04.742+0200","outcomeMessage":"31 studies compared"}]
        if(e.id){
            if(e.id === "search"){
                let filter = Object.assign({},this.filterObject);
                if(filter['limit'])
                    filter['limit']++;
                this.getDiffTasks(filter);
            }
            if(e.id === "count"){
                let filter = Object.assign({},this.filterObject);
                delete filter["limit"];
                delete filter["offset"];
                this.getDiffTasksCount(filter);
            }
        }
    }
    getDiffTasks(filter){
        this.cfpLoadingBar.start();
        this.tasks = [];
        this.service.getDiffTask(filter,this.batchGrouped).subscribe(tasks=>{
            if(tasks && tasks.length && tasks.length > 0){
                if(this.batchGrouped){
                    this.config = {
                        table:j4care.calculateWidthOfTable(this.service.getTableBatchGroupedColumens((e)=>{
                            this.showDetails(e)
                        })),
                        filter:filter
                    };
                    this.tasks = tasks.map(taskObject=>{
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
                    this.config = {
                        table:j4care.calculateWidthOfTable(this.service.getTableColumens()),
                        filter:filter
                    };
                    this.tasks = tasks;
                }
                    /*= [
                    {"pk":"1","LocalAET":"DCM4CHEE2","PrimaryAET":"DCM4CHEE2","SecondaryAET":"DCM4CHEE_ADMIN","QueryString":"includefield=all&offset=0&limit=21&returnempty=false&queue=true&missing=true&different=true&batchID=test","checkMissing":true,"checkDifferent":true,"matches":31,"createdTime":"2018-06-06T14:03:04.136+0200","updatedTime":"2018-06-06T14:03:04.701+0200","queue":"DiffTasks","JMSMessageID":"ID:911a5646-6981-11e8-897e-0242ac120003","dicomDeviceName":"dcm4chee-arc","status":"COMPLETED","batchID":"test","scheduledTime":"2018-06-06T14:03:04.121+0200","processingStartTime":"2018-06-06T14:03:04.264+0200","processingEndTime":"2018-06-06T14:03:04.742+0200","outcomeMessage":"31 studies compared"},
                    {"pk":"2","LocalAET":"DCM4CHEE4","PrimaryAET":"DCM4CHEE4","SecondaryAET":"DCM4CHEE_ADMIN5","QueryString":"includefield=all&offset=0&limit=21&returnempty=false&queue=true&missing=true&different=true&batchID=test","checkMissing":true,"checkDifferent":true,"matches":31,"createdTime":"2018-06-06T14:03:04.136+0200","updatedTime":"2018-06-06T14:03:04.701+0200","queue":"DiffTasks","JMSMessageID":"ID:911a5646-6981-11e8-897e-0242ac120003","dicomDeviceName":"dcm4chee-arc","status":"COMPLETED","batchID":"test","scheduledTime":"2018-06-06T14:03:04.121+0200","processingStartTime":"2018-06-06T14:03:04.264+0200","processingEndTime":"2018-06-06T14:03:04.742+0200","outcomeMessage":"31 studies compared"},
                    {"pk":"2","LocalAET":"DCM4CHEE4","PrimaryAET":"DCM4CHEE4","SecondaryAET":"DCM4CHEE_ADMIN5","QueryString":"includefield=all&offset=0&limit=21&returnempty=false&queue=true&missing=true&different=true&batchID=test","checkMissing":true,"checkDifferent":true,"matches":31,"createdTime":"2018-06-06T14:03:04.136+0200","updatedTime":"2018-06-06T14:03:04.701+0200","queue":"DiffTasks","JMSMessageID":"ID:911a5646-6981-11e8-897e-0242ac120003","dicomDeviceName":"dcm4chee-arc","status":"COMPLETED","batchID":"test","scheduledTime":"2018-06-06T14:03:04.121+0200","processingStartTime":"2018-06-06T14:03:04.264+0200","processingEndTime":"2018-06-06T14:03:04.742+0200","outcomeMessage":"31 studies compared"},
                    {"pk":"2","LocalAET":"DCM4CHEE4","PrimaryAET":"DCM4CHEE4","SecondaryAET":"DCM4CHEE_ADMIN5","QueryString":"includefield=all&offset=0&limit=21&returnempty=false&queue=true&missing=true&different=true&batchID=test","checkMissing":true,"checkDifferent":true,"matches":31,"createdTime":"2018-06-06T14:03:04.136+0200","updatedTime":"2018-06-06T14:03:04.701+0200","queue":"DiffTasks","JMSMessageID":"ID:911a5646-6981-11e8-897e-0242ac120003","dicomDeviceName":"dcm4chee-arc","status":"COMPLETED","batchID":"test","scheduledTime":"2018-06-06T14:03:04.121+0200","processingStartTime":"2018-06-06T14:03:04.264+0200","processingEndTime":"2018-06-06T14:03:04.742+0200","outcomeMessage":"31 studies compared"},
                    {"pk":"2","LocalAET":"DCM4CHEE4","PrimaryAET":"DCM4CHEE4","SecondaryAET":"DCM4CHEE_ADMIN5","QueryString":"includefield=all&offset=0&limit=21&returnempty=false&queue=true&missing=true&different=true&batchID=test","checkMissing":true,"checkDifferent":true,"matches":31,"createdTime":"2018-06-06T14:03:04.136+0200","updatedTime":"2018-06-06T14:03:04.701+0200","queue":"DiffTasks","JMSMessageID":"ID:911a5646-6981-11e8-897e-0242ac120003","dicomDeviceName":"dcm4chee-arc","status":"COMPLETED","batchID":"test","scheduledTime":"2018-06-06T14:03:04.121+0200","processingStartTime":"2018-06-06T14:03:04.264+0200","processingEndTime":"2018-06-06T14:03:04.742+0200","outcomeMessage":"31 studies compared"},
                    {"pk":"2","LocalAET":"DCM4CHEE4","PrimaryAET":"DCM4CHEE4","SecondaryAET":"DCM4CHEE_ADMIN5","QueryString":"includefield=all&offset=0&limit=21&returnempty=false&queue=true&missing=true&different=true&batchID=test","checkMissing":true,"checkDifferent":true,"matches":31,"createdTime":"2018-06-06T14:03:04.136+0200","updatedTime":"2018-06-06T14:03:04.701+0200","queue":"DiffTasks","JMSMessageID":"ID:911a5646-6981-11e8-897e-0242ac120003","dicomDeviceName":"dcm4chee-arc","status":"COMPLETED","batchID":"test","scheduledTime":"2018-06-06T14:03:04.121+0200","processingStartTime":"2018-06-06T14:03:04.264+0200","processingEndTime":"2018-06-06T14:03:04.742+0200","outcomeMessage":"31 studies compared"},
                    {"pk":"2","LocalAET":"DCM4CHEE4","PrimaryAET":"DCM4CHEE4","SecondaryAET":"DCM4CHEE_ADMIN5","QueryString":"includefield=all&offset=0&limit=21&returnempty=false&queue=true&missing=true&different=true&batchID=test","checkMissing":true,"checkDifferent":true,"matches":31,"createdTime":"2018-06-06T14:03:04.136+0200","updatedTime":"2018-06-06T14:03:04.701+0200","queue":"DiffTasks","JMSMessageID":"ID:911a5646-6981-11e8-897e-0242ac120003","dicomDeviceName":"dcm4chee-arc","status":"COMPLETED","batchID":"test","scheduledTime":"2018-06-06T14:03:04.121+0200","processingStartTime":"2018-06-06T14:03:04.264+0200","processingEndTime":"2018-06-06T14:03:04.742+0200","outcomeMessage":"31 studies compared"},
                    {"pk":"2","LocalAET":"DCM4CHEE4","PrimaryAET":"DCM4CHEE4","SecondaryAET":"DCM4CHEE_ADMIN5","QueryString":"includefield=all&offset=0&limit=21&returnempty=false&queue=true&missing=true&different=true&batchID=test","checkMissing":true,"checkDifferent":true,"matches":31,"createdTime":"2018-06-06T14:03:04.136+0200","updatedTime":"2018-06-06T14:03:04.701+0200","queue":"DiffTasks","JMSMessageID":"ID:911a5646-6981-11e8-897e-0242ac120003","dicomDeviceName":"dcm4chee-arc","status":"COMPLETED","batchID":"test","scheduledTime":"2018-06-06T14:03:04.121+0200","processingStartTime":"2018-06-06T14:03:04.264+0200","processingEndTime":"2018-06-06T14:03:04.742+0200","outcomeMessage":"31 studies compared"},
                    {"pk":"2","LocalAET":"DCM4CHEE4","PrimaryAET":"DCM4CHEE4","SecondaryAET":"DCM4CHEE_ADMIN5","QueryString":"includefield=all&offset=0&limit=21&returnempty=false&queue=true&missing=true&different=true&batchID=test","checkMissing":true,"checkDifferent":true,"matches":31,"createdTime":"2018-06-06T14:03:04.136+0200","updatedTime":"2018-06-06T14:03:04.701+0200","queue":"DiffTasks","JMSMessageID":"ID:911a5646-6981-11e8-897e-0242ac120003","dicomDeviceName":"dcm4chee-arc","status":"COMPLETED","batchID":"test","scheduledTime":"2018-06-06T14:03:04.121+0200","processingStartTime":"2018-06-06T14:03:04.264+0200","processingEndTime":"2018-06-06T14:03:04.742+0200","outcomeMessage":"31 studies compared"},
                    {"pk":"2","LocalAET":"DCM4CHEE4","PrimaryAET":"DCM4CHEE4","SecondaryAET":"DCM4CHEE_ADMIN5","QueryString":"includefield=all&offset=0&limit=21&returnempty=false&queue=true&missing=true&different=true&batchID=test","checkMissing":true,"checkDifferent":true,"matches":31,"createdTime":"2018-06-06T14:03:04.136+0200","updatedTime":"2018-06-06T14:03:04.701+0200","queue":"DiffTasks","JMSMessageID":"ID:911a5646-6981-11e8-897e-0242ac120003","dicomDeviceName":"dcm4chee-arc","status":"COMPLETED","batchID":"test","scheduledTime":"2018-06-06T14:03:04.121+0200","processingStartTime":"2018-06-06T14:03:04.264+0200","processingEndTime":"2018-06-06T14:03:04.742+0200","outcomeMessage":"31 studies compared"},
                    {"pk":"2","LocalAET":"DCM4CHEE4","PrimaryAET":"DCM4CHEE4","SecondaryAET":"DCM4CHEE_ADMIN5","QueryString":"includefield=all&offset=0&limit=21&returnempty=false&queue=true&missing=true&different=true&batchID=test","checkMissing":true,"checkDifferent":true,"matches":31,"createdTime":"2018-06-06T14:03:04.136+0200","updatedTime":"2018-06-06T14:03:04.701+0200","queue":"DiffTasks","JMSMessageID":"ID:911a5646-6981-11e8-897e-0242ac120003","dicomDeviceName":"dcm4chee-arc","status":"COMPLETED","batchID":"test","scheduledTime":"2018-06-06T14:03:04.121+0200","processingStartTime":"2018-06-06T14:03:04.264+0200","processingEndTime":"2018-06-06T14:03:04.742+0200","outcomeMessage":"31 studies compared"},
                    {"pk":"2","LocalAET":"DCM4CHEE4","PrimaryAET":"DCM4CHEE4","SecondaryAET":"DCM4CHEE_ADMIN5","QueryString":"includefield=all&offset=0&limit=21&returnempty=false&queue=true&missing=true&different=true&batchID=test","checkMissing":true,"checkDifferent":true,"matches":31,"createdTime":"2018-06-06T14:03:04.136+0200","updatedTime":"2018-06-06T14:03:04.701+0200","queue":"DiffTasks","JMSMessageID":"ID:911a5646-6981-11e8-897e-0242ac120003","dicomDeviceName":"dcm4chee-arc","status":"COMPLETED","batchID":"test","scheduledTime":"2018-06-06T14:03:04.121+0200","processingStartTime":"2018-06-06T14:03:04.264+0200","processingEndTime":"2018-06-06T14:03:04.742+0200","outcomeMessage":"31 studies compared"},
                    {"pk":"2","LocalAET":"DCM4CHEE4","PrimaryAET":"DCM4CHEE4","SecondaryAET":"DCM4CHEE_ADMIN5","QueryString":"includefield=all&offset=0&limit=21&returnempty=false&queue=true&missing=true&different=true&batchID=test","checkMissing":true,"checkDifferent":true,"matches":31,"createdTime":"2018-06-06T14:03:04.136+0200","updatedTime":"2018-06-06T14:03:04.701+0200","queue":"DiffTasks","JMSMessageID":"ID:911a5646-6981-11e8-897e-0242ac120003","dicomDeviceName":"dcm4chee-arc","status":"COMPLETED","batchID":"test","scheduledTime":"2018-06-06T14:03:04.121+0200","processingStartTime":"2018-06-06T14:03:04.264+0200","processingEndTime":"2018-06-06T14:03:04.742+0200","outcomeMessage":"31 studies compared"},
                    {"pk":"2","LocalAET":"DCM4CHEE4","PrimaryAET":"DCM4CHEE4","SecondaryAET":"DCM4CHEE_ADMIN5","QueryString":"includefield=all&offset=0&limit=21&returnempty=false&queue=true&missing=true&different=true&batchID=test","checkMissing":true,"checkDifferent":true,"matches":31,"createdTime":"2018-06-06T14:03:04.136+0200","updatedTime":"2018-06-06T14:03:04.701+0200","queue":"DiffTasks","JMSMessageID":"ID:911a5646-6981-11e8-897e-0242ac120003","dicomDeviceName":"dcm4chee-arc","status":"COMPLETED","batchID":"test","scheduledTime":"2018-06-06T14:03:04.121+0200","processingStartTime":"2018-06-06T14:03:04.264+0200","processingEndTime":"2018-06-06T14:03:04.742+0200","outcomeMessage":"31 studies compared"},
                    {"pk":"2","LocalAET":"DCM4CHEE4","PrimaryAET":"DCM4CHEE4","SecondaryAET":"DCM4CHEE_ADMIN5","QueryString":"includefield=all&offset=0&limit=21&returnempty=false&queue=true&missing=true&different=true&batchID=test","checkMissing":true,"checkDifferent":true,"matches":31,"createdTime":"2018-06-06T14:03:04.136+0200","updatedTime":"2018-06-06T14:03:04.701+0200","queue":"DiffTasks","JMSMessageID":"ID:911a5646-6981-11e8-897e-0242ac120003","dicomDeviceName":"dcm4chee-arc","status":"COMPLETED","batchID":"test","scheduledTime":"2018-06-06T14:03:04.121+0200","processingStartTime":"2018-06-06T14:03:04.264+0200","processingEndTime":"2018-06-06T14:03:04.742+0200","outcomeMessage":"31 studies compared"},
                    {"pk":"2","LocalAET":"DCM4CHEE4","PrimaryAET":"DCM4CHEE4","SecondaryAET":"DCM4CHEE_ADMIN5","QueryString":"includefield=all&offset=0&limit=21&returnempty=false&queue=true&missing=true&different=true&batchID=test","checkMissing":true,"checkDifferent":true,"matches":31,"createdTime":"2018-06-06T14:03:04.136+0200","updatedTime":"2018-06-06T14:03:04.701+0200","queue":"DiffTasks","JMSMessageID":"ID:911a5646-6981-11e8-897e-0242ac120003","dicomDeviceName":"dcm4chee-arc","status":"COMPLETED","batchID":"test","scheduledTime":"2018-06-06T14:03:04.121+0200","processingStartTime":"2018-06-06T14:03:04.264+0200","processingEndTime":"2018-06-06T14:03:04.742+0200","outcomeMessage":"31 studies compared"},
                    {"pk":"2","LocalAET":"DCM4CHEE4","PrimaryAET":"DCM4CHEE4","SecondaryAET":"DCM4CHEE_ADMIN5","QueryString":"includefield=all&offset=0&limit=21&returnempty=false&queue=true&missing=true&different=true&batchID=test","checkMissing":true,"checkDifferent":true,"matches":31,"createdTime":"2018-06-06T14:03:04.136+0200","updatedTime":"2018-06-06T14:03:04.701+0200","queue":"DiffTasks","JMSMessageID":"ID:911a5646-6981-11e8-897e-0242ac120003","dicomDeviceName":"dcm4chee-arc","status":"COMPLETED","batchID":"test","scheduledTime":"2018-06-06T14:03:04.121+0200","processingStartTime":"2018-06-06T14:03:04.264+0200","processingEndTime":"2018-06-06T14:03:04.742+0200","outcomeMessage":"31 studies compared"},
                    {"pk":"2","LocalAET":"DCM4CHEE4","PrimaryAET":"DCM4CHEE4","SecondaryAET":"DCM4CHEE_ADMIN5","QueryString":"includefield=all&offset=0&limit=21&returnempty=false&queue=true&missing=true&different=true&batchID=test","checkMissing":true,"checkDifferent":true,"matches":31,"createdTime":"2018-06-06T14:03:04.136+0200","updatedTime":"2018-06-06T14:03:04.701+0200","queue":"DiffTasks","JMSMessageID":"ID:911a5646-6981-11e8-897e-0242ac120003","dicomDeviceName":"dcm4chee-arc","status":"COMPLETED","batchID":"test","scheduledTime":"2018-06-06T14:03:04.121+0200","processingStartTime":"2018-06-06T14:03:04.264+0200","processingEndTime":"2018-06-06T14:03:04.742+0200","outcomeMessage":"31 studies compared"},
                    {"pk":"2","LocalAET":"DCM4CHEE4","PrimaryAET":"DCM4CHEE4","SecondaryAET":"DCM4CHEE_ADMIN5","QueryString":"includefield=all&offset=0&limit=21&returnempty=false&queue=true&missing=true&different=true&batchID=test","checkMissing":true,"checkDifferent":true,"matches":31,"createdTime":"2018-06-06T14:03:04.136+0200","updatedTime":"2018-06-06T14:03:04.701+0200","queue":"DiffTasks","JMSMessageID":"ID:911a5646-6981-11e8-897e-0242ac120003","dicomDeviceName":"dcm4chee-arc","status":"COMPLETED","batchID":"test","scheduledTime":"2018-06-06T14:03:04.121+0200","processingStartTime":"2018-06-06T14:03:04.264+0200","processingEndTime":"2018-06-06T14:03:04.742+0200","outcomeMessage":"31 studies compared"},
                    {"pk":"2","LocalAET":"DCM4CHEE4","PrimaryAET":"DCM4CHEE4","SecondaryAET":"DCM4CHEE_ADMIN5","QueryString":"includefield=all&offset=0&limit=21&returnempty=false&queue=true&missing=true&different=true&batchID=test","checkMissing":true,"checkDifferent":true,"matches":31,"createdTime":"2018-06-06T14:03:04.136+0200","updatedTime":"2018-06-06T14:03:04.701+0200","queue":"DiffTasks","JMSMessageID":"ID:911a5646-6981-11e8-897e-0242ac120003","dicomDeviceName":"dcm4chee-arc","status":"COMPLETED","batchID":"test","scheduledTime":"2018-06-06T14:03:04.121+0200","processingStartTime":"2018-06-06T14:03:04.264+0200","processingEndTime":"2018-06-06T14:03:04.742+0200","outcomeMessage":"31 studies compared"},
                    {"pk":"3","LocalAET":"DCM4CHEE23","PrimaryAET":"DCM4CHEE3","SecondaryAET":"DCM4CHEE_ADMIN2","QueryString":"includefield=all&offset=0&limit=21&returnempty=false&queue=true&missing=true&different=true&batchID=test2","checkMissing":true,"checkDifferent":true,"matches":31,"createdTime":"2018-06-06T14:03:04.136+0200","updatedTime":"2018-06-06T14:03:04.701+0200","queue":"DiffTasks","JMSMessageID":"ID:911a5646-6981-11e8-897e-0242ac120003","dicomDeviceName":"dcm4chee-arc","status":"COMPLETED","batchID":"test","scheduledTime":"2018-06-06T14:03:04.121+0200","processingStartTime":"2018-06-06T14:03:04.264+0200","processingEndTime":"2018-06-06T14:03:04.742+0200","outcomeMessage":"31 studies compared"}
                ];*/
                this.moreTasks = tasks.length > this.filterObject['limit'];
                if(this.moreTasks)
                    this.tasks.splice(this.tasks.length-1,1);
                // this.tasks = tasks;
            }else{
                this.mainservice.setMessage({
                    'title': 'Info',
                    'text': 'No diff tasks found!',
                    'status': 'info'
                });
            }
            this.cfpLoadingBar.complete();
        },err=>{
            this.cfpLoadingBar.complete();
            this.httpErrorHandler.handleError(err);
        })
    }
    getDiffTasksCount(filters){
        this.cfpLoadingBar.start();
        this.service.getDiffTasksCount(filters).subscribe((res)=>{
            try{
                this.count = res.count;
            }catch (e){
                this.count = "";
            }
            this.initSchema();
            this.cfpLoadingBar.complete();
        },(err)=>{
            this.cfpLoadingBar.complete();
            this.httpErrorHandler.handleError(err);
        });
    }
    showDetails(e){
        console.log("in show details",e);
        this.batchGrouped = false;
        this.filterObject['batchID'] = e.batchID;
        let filter = Object.assign({},this.filterObject);
        if(filter['limit'])
            filter['limit']++;
        this.getDiffTasks(filter);
    }
    next(){
        if(this.moreTasks){
            let filter = Object.assign({},this.filterObject);
            if(filter['limit']){
                this.filterObject['offset'] = filter['offset'] = filter['offset']*1 + this.filterObject['limit']*1;
                filter['limit']++;
            }
            this.getDiffTasks(filter);
        }
    }
    prev(){
        if(this.filterObject["offset"] > 0){
            let filter = Object.assign({},this.filterObject);
            if(filter['limit'])
                this.filterObject['offset'] = filter['offset'] = filter['offset']*1 - this.filterObject['limit']*1;
            this.getDiffTasks(filter);
        }
    }
    confirm(confirmparameters){
        this.dialogConfig.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(ConfirmComponent, {
            height: 'auto',
            width: '500px'
        });
        this.dialogRef.componentInstance.parameters = confirmparameters;
        return this.dialogRef.afterClosed();
    };
    tableMouseEnter(){
        this.tableHovered = true;
    }
    tableMouseLeave(){
        this.tableHovered = false;
    }
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
                delete filterClone['offset'];
                delete filterClone['limit'];
                if(!this.mainservice.global.notSecure){
                    WindowRefService.nativeWindow.open(`../monitor/diff?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&access_token=${token}&${this.mainservice.param(filterClone)}`);
                }else{
                    WindowRefService.nativeWindow.open(`../monitor/diff?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&${this.mainservice.param(filterClone)}`);
                }
            });
        })
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
    getCounts(){
        let filters = Object.assign({},this.filterObject);
        if(!this.tableHovered)
            this.getDiffTasks(filters);
        Object.keys(this.statusValues).forEach(status=>{
            filters['status'] = status;
            this.statusValues[status].loader = true;
            this.service.getDiffTasksCount(filters).subscribe((count)=>{
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
    ngOnDestroy(){
        if(this.timer.started){
            this.timer.started = false;
            clearInterval(this.refreshInterval);
        }
    }
}
