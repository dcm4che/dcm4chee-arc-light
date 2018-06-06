import { Component, OnInit } from '@angular/core';
import {DiffMonitorService} from "./diff-monitor.service";
import {AppService} from "../../app.service";
import {ActivatedRoute} from "@angular/router";
import * as _ from 'lodash';
import {LoadingBarService} from "@ngx-loading-bar/core";
import {AeListService} from "../../ae-list/ae-list.service";
import {Observable} from "rxjs/Observable";
import {j4care} from "../../helpers/j4care.service";
import {HttpErrorHandler} from "../../helpers/http-error-handler";

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
    constructor(
        private service:DiffMonitorService,
        private mainservice:AppService,
        private route: ActivatedRoute,
        private cfpLoadingBar: LoadingBarService,
        private aeListService:AeListService,
        private httpErrorHandler:HttpErrorHandler
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
        this.filterSchema = j4care.prepareFlatFilterObject(this.service.getFormSchema(this.aes, this.aets,"Size",this.devices),3);
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
        }
    }
    getDiffTasks(filter){
        this.service.getDiffTask(filter).subscribe(tasks=>{
            this.config = {
                table:j4care.calculateWidthOfTable(this.service.getTableColumens()),
                filter:filter
            };
            this.tasks = tasks; /*= [
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
        },err=>{
            this.httpErrorHandler.handleError(err);
        })
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
    onFormChange(e){

    }

    downloadCsv(){

    }
    ngOnDestroy(){
    }
}
