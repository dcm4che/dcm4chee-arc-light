import {Component, OnInit, ViewContainerRef, OnDestroy} from '@angular/core';
import {QueuesService} from './queues.service';
import {AppService} from '../../app.service';
import {ConfirmComponent} from '../../widgets/dialogs/confirm/confirm.component';
// import { MatLegacyDialogRef as MatDialogRef, MatLegacyDialog as MatDialog, MatLegacyDialogConfig as MatDialogConfig } from '@angular/material/legacy-dialog';
import {MatDialog, MatDialogRef} from "@angular/material/dialog";
import * as _ from 'lodash-es';
import {WindowRefService} from "../../helpers/window-ref.service";
import {HttpErrorHandler} from "../../helpers/http-error-handler";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {LoadingBarService} from "@ngx-loading-bar/core";
import {ActivatedRoute} from "@angular/router";
import {j4care} from "../../helpers/j4care.service";
import {forkJoin, ReplaySubject} from "rxjs";
import {DevicesService} from "../../configuration/devices/devices.service";
import {KeycloakService} from "../../helpers/keycloak-service/keycloak.service";
import {Globalvar} from "../../constants/globalvar";
import {AeListService} from "../../configuration/ae-list/ae-list.service";
import {map} from "rxjs/operators";
import {PermissionService} from "../../helpers/permissions/permission.service";
import {environment} from "../../../environments/environment";
import {Device} from "../../models/device";
import {SelectDropdown} from "../../interfaces";


@Component({
    selector: 'app-queues',
    templateUrl: './queues.component.html',
    standalone: false
})
export class QueuesComponent implements OnInit, OnDestroy{
    private destroyed$: ReplaySubject<boolean> = new ReplaySubject(1);
    filterLoadFinished = false;
    matches = [];
    queues = [];
    dialogRef: MatDialogRef<any>;
    _ = _;
    devices;
    localAETs;
    remoteAETs;
    counText = $localize `:@@COUNT:COUNT`;
    allAction;
    allActionsOptions = [
        {
            value:"cancel",
            label:$localize `:@@cancel_all_matching_tasks:Cancel all matching tasks`
        },{
            value:"reschedule",
            label:$localize `:@@reschedule_all_matching_tasks:Reschedule all matching tasks`
        },{
            value:"delete",
            label:$localize `:@@delete_all_matching_tasks:Delete all matching tasks`
        }
    ];
    allActionsActive = [];
    urlParam;
    statusValues = {};
    refreshInterval;
    interval = 10;
    Object = Object;
    tableHovered = false;
    statuses = [
        {
            value:"SCHEDULED",
            text:$localize `:@@SCHEDULED:SCHEDULED`,
            key:"scheduled"
        },{
            value:"SCHEDULED FOR RETRY",
            text:$localize `:@@S_FOR_RETRY:S. FOR RETRY`,
            key:"scheduled-for-retry"
        },{
            value:"IN PROCESS",
            text:$localize `:@@in_process:IN PROCESS`,
            key:"in-process"
        },{
            value:"COMPLETED",
            text:$localize `:@@COMPLETED:COMPLETED`,
            key:"completed"
        },{
            value:"WARNING",
            text:$localize `:@@WARNING:WARNING`,
            key:"warning"
        },{
            value:"FAILED",
            text:$localize `:@@FAILED:FAILED`,
            key:"failed"
        },
        {
            value:"CANCELED",
            text:$localize `:@@CANCELED:CANCELED`,
            key:"canceled"
        }
    ];
    timer = {
        started:false,
        startText:$localize `:@@start_auto_refresh:Start Auto Refresh`,
        stopText:$localize `:@@stop_auto_refresh:Stop Auto Refresh`
    };
    filterObject:any = {
        status:undefined,
        orderby:undefined,
        queueName:undefined,
        dicomDeviceName:undefined,
        createdTime:undefined,
        updatedTime:undefined,
        batchID:undefined,
        localAET:undefined,
        remoteAET:undefined,
        StudyInstanceUID:undefined,
        limit:20,
        offset:0
    };
    filterSchema = [];
    tableConfig:any = {
        search:"",
        showAttributes:false
    };
    constructor(
        public $http:J4careHttpService,
        public service: QueuesService,
        public mainservice: AppService,
        public cfpLoadingBar: LoadingBarService,
        public viewContainerRef: ViewContainerRef,
        public dialog: MatDialog,
        private httpErrorHandler:HttpErrorHandler,
        private route: ActivatedRoute,
        private deviceService:DevicesService,
        public aeListService:AeListService,
        private permissionService:PermissionService,
    ) {};
    ngOnInit(){
        this.initCheck(10);
    }
    initCheck(retries){
        let $this = this;

        if((KeycloakService.keycloakAuth && KeycloakService.keycloakAuth.authenticated) || (_.hasIn(this.mainservice,"global.notSecure") && this.mainservice.global.notSecure)){
            this.route.queryParams.subscribe(params => {
                this.urlParam = Object.assign({},params);
                if(this.urlParam["queueName"])
                    this.filterObject.queueName = this.urlParam["queueName"];
                if(this.urlParam["dicomDeviceName"])
                    this.filterObject.dicomDeviceName = this.urlParam["dicomDeviceName"];
                this.init();
            });
        }else{
            if (retries){
                setTimeout(()=>{
                    $this.initCheck(retries-1);
                },20);
            }else{
                this.route.queryParams.subscribe(params => {
                    this.urlParam = Object.assign({},params);
                    if(this.urlParam["queueName"])
                        this.filterObject.queueName = this.urlParam["queueName"];
                    this.init();
                });
                this.init();
            }
        }
        this.statusChange();
    }
    statusChange(){
        this.setTableSchema();
        /*        this.allActionsActive = this.allActionsOptions.filter((o)=>{
                    if(this.filterObject.status == "SCHEDULED" || this.filterObject.status == $localize `:@@in_process:IN PROCESS`){
                        return o.value != 'reschedule';
                    }else{
                        if(this.filterObject.status === '*')
                            return o.value != 'cancel' && o.value != 'reschedule';
                        else
                            return o.value != 'cancel';
                    }
                });*/
    }
    allActionChanged(e){
        let text = $localize `:@@matching_task_question:Are you sure, you want to ${Globalvar.getActionText(this.allAction)} all matching tasks?`;
        let filter = {
            dicomDeviceName:(this.filterObject.dicomDeviceName && this.filterObject.status != '*') ? this.filterObject.dicomDeviceName : undefined,
            status:(this.filterObject.status && this.filterObject.status != '*') ? this.filterObject.status : undefined,
            createdTime:this.filterObject.createdTime || undefined,
            updatedTime:this.filterObject.updatedTime || undefined,
            localAET:(this.filterObject.localAET && this.filterObject.localAET != '*') ? this.filterObject.localAET: undefined,
            remoteAET:(this.filterObject.remoteAET && this.filterObject.remoteAET != '*') ?  this.filterObject.remoteAET : undefined
        };
        switch (this.allAction){
            case "cancel":
                this.confirm({
                    content: text
                }).subscribe((ok)=>{
                    if(ok){
                        this.cfpLoadingBar.start();
                        this.service.cancelAll(filter,this.filterObject.queueName).subscribe((res)=>{
                            this.mainservice.showMsg($localize `:@@tasks_queue_canceled:${res.count} tasks in queue canceled successfully!`)
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
                        this.deviceService.selectParametersForMatching((res)=>{
                            if(res){
                                this.cfpLoadingBar.start();
                                if(_.hasIn(res, "schema_model.newDeviceName") && res.schema_model.newDeviceName != ""){
                                    filter["newDeviceName"] = res.schema_model.newDeviceName;
                                }
                                if(_.hasIn(res, "schema_model.scheduledTime") && res.schema_model.scheduledTime != ""){
                                    filter["scheduledTime"] = res.schema_model.scheduledTime;
                                }
                                this.service.rescheduleAll(filter,this.filterObject.queueName).subscribe((res)=>{
                                    this.mainservice.showMsg($localize `:@@tasks_queue_rescheduled:${res.count} tasks in queue rescheduled successfully!`);
                                    this.cfpLoadingBar.complete();
                                }, (err) => {
                                    this.cfpLoadingBar.complete();
                                    this.httpErrorHandler.handleError(err);
                                });
                            }
                        },
                        this.devices);
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
                        this.service.deleteAll(filter,this.filterObject.queueName).subscribe((res)=>{
                            this.mainservice.showMsg($localize `:@@tasks_queue_deleted:${res.count || 0} tasks in queue deleted successfully!`);
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
/*    test(){
        this.deviceService.selectParameters((res)=>{
            console.log("j4carehelper select deviceres",res);
            if(res){

            }
        },
        this.devices.map(device=>{
            return {
                text:device.dicomDeviceName,
                value:device.dicomDeviceName
            }
        }));
    }*/
    init(){
        this.initQuery();
        this.statuses.forEach(status =>{
            this.statusValues[status.value] = {
                count: 0,
                loader: false,
                ...status
            };
        });
        this.setTableSchema();
    }

    setTableSchema(){
        this.tableConfig = {
            table:j4care.calculateWidthOfTable(this.service.getTableColumns(this, this.action,{filterObject:this.filterObject})),
            filter:this.filterObject,
            search:"",
            showAttributes:false,
            calculate:false
        };
    }

    action(mode, match){
        switch(mode){
            case "cancel-selected":
                this.executeAll('cancel');
                break;
            case "reschedule-selected":
                this.executeAll('reschedule');
                break;
            case "delete-selected":
                    this.executeAll('delete');
                break;
            case "delete":
                this.delete(match);
                break;
            case "cancel":
                this.cancel(match);
                break;
            case "reschedule":
                this.reschedule(match);
                break;
        }

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
        if(this.filterObject.queueName){
            if(!this.tableHovered)
                this.search(0);
            Object.keys(this.statusValues).forEach(status=>{
                this.statusValues[status].loader = true;
                this.service.getCount(this.filterObject.queueName,
                    status,
                    undefined,
                    undefined,
                    this.filterObject.dicomDeviceName,
                    this.filterObject.createdTime,
                    this.filterObject.updatedTime,
                    this.filterObject.batchID,
                    this.filterObject.localAET,
                    this.filterObject.remoteAET,
                    '',
                    this.filterObject.StudyInstanceUID
                ).subscribe((count)=>{
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
        }else{
            this.mainservice.showError($localize `:@@no_queue_name:No Queue Name selected!`);
        }
    }
    filterKeyUp(e){
        let code = (e.keyCode ? e.keyCode : e.which);
        if (code === 13){
            this.search(0);
        }
    };
    onSubmit(object){
        if(_.hasIn(object,"id") && _.hasIn(object,"model")){
            this.filterObject = object.model;
            if(object.id === "count"){
                this.getCount();
            }else{
                this.getCounts();
            }
        }
    }
    moreTasks = false;
    search(offset) {
        let $this = this;
        if(this.filterObject.queueName){
            $this.cfpLoadingBar.start();
            this.service.search(this.filterObject.queueName,
                this.filterObject.status, offset,
                this.filterObject.limit,
                this.filterObject.dicomDeviceName,
                this.filterObject.createdTime,
                this.filterObject.updatedTime,
                this.filterObject.batchID,
                this.filterObject.localAET,
                this.filterObject.remoteAET,
                this.filterObject.orderby,
                this.filterObject.StudyInstanceUID
                )
                .subscribe((res) => {
                    if(!environment.production){
                        res = [{"taskID":"1910","dicomDeviceName":"demoj4c","queue":"Export10","type":"EXPORT","status":"SCHEDULED","failures":"11","createdTime":"2022-03-15T01:20:51.476+0100","updatedTime":"2022-03-15T19:43:11.194+0100","scheduledTime":"2022-03-15T19:43:00.643+0100","processingStartTime":"2022-03-15T19:43:06.158+0100","processingEndTime":"2022-03-15T19:43:10.799+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series WADO Metadata","StudyInstanceUID":"2.25.253254299183063559800543717906499910894","NumberOfInstances":"2079","Modality":["CT"]},{"taskID":"1942","dicomDeviceName":"demoj4c","queue":"Export10","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.896+0100","updatedTime":"2022-03-15T19:39:01.099+0100","scheduledTime":"2022-03-15T19:38:00.984+0100","processingStartTime":"2022-03-15T19:39:00.860+0100","processingEndTime":"2022-03-15T19:39:01.098+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series WADO Metadata","StudyInstanceUID":"2.25.229975568262236341440816121223809588447","NumberOfInstances":"2958","Modality":["CT","SR"]},{"taskID":"1934","dicomDeviceName":"demoj4c","queue":"Export10","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.755+0100","updatedTime":"2022-03-15T19:39:01.027+0100","scheduledTime":"2022-03-15T19:38:00.942+0100","processingStartTime":"2022-03-15T19:39:00.771+0100","processingEndTime":"2022-03-15T19:39:01.026+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series WADO Metadata","StudyInstanceUID":"2.25.150446237572158364218250943566632959728","NumberOfInstances":"1007","Modality":["CT","PR","SR"]},{"taskID":"1918","dicomDeviceName":"demoj4c","queue":"Export10","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.585+0100","updatedTime":"2022-03-15T19:39:00.863+0100","scheduledTime":"2022-03-15T19:38:00.873+0100","processingStartTime":"2022-03-15T19:39:00.717+0100","processingEndTime":"2022-03-15T19:39:00.861+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series WADO Metadata","StudyInstanceUID":"2.25.133790264388916295901178041223591125703","NumberOfInstances":"310","Modality":["CT","PR","KO"]},{"taskID":"1922","dicomDeviceName":"demoj4c","queue":"Export10","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.634+0100","updatedTime":"2022-03-15T19:39:00.781+0100","scheduledTime":"2022-03-15T19:38:00.813+0100","processingStartTime":"2022-03-15T19:39:00.688+0100","processingEndTime":"2022-03-15T19:39:00.780+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series WADO Metadata","StudyInstanceUID":"2.25.38075169170603874823282185203405259704","NumberOfInstances":"7","Modality":["PR","DX"]},{"taskID":"1930","dicomDeviceName":"demoj4c","queue":"Export10","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.714+0100","updatedTime":"2022-03-15T19:39:00.735+0100","scheduledTime":"2022-03-15T19:38:00.741+0100","processingStartTime":"2022-03-15T19:39:00.671+0100","processingEndTime":"2022-03-15T19:39:00.734+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series WADO Metadata","StudyInstanceUID":"2.25.73758429535919582411283778014440169399","NumberOfInstances":"3","Modality":["PR","SR","CR"]},{"taskID":"1926","dicomDeviceName":"demoj4c","queue":"Export10","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.675+0100","updatedTime":"2022-03-15T19:39:00.729+0100","scheduledTime":"2022-03-15T19:38:00.722+0100","processingStartTime":"2022-03-15T19:39:00.657+0100","processingEndTime":"2022-03-15T19:39:00.728+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series WADO Metadata","StudyInstanceUID":"2.25.244811086690625888516433847572724695651","NumberOfInstances":"3","Modality":["CR"]},{"taskID":"1946","dicomDeviceName":"demoj4c","queue":"Export10","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.929+0100","updatedTime":"2022-03-15T19:39:00.707+0100","scheduledTime":"2022-03-15T19:38:00.700+0100","processingStartTime":"2022-03-15T19:39:00.638+0100","processingEndTime":"2022-03-15T19:39:00.705+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series WADO Metadata","StudyInstanceUID":"2.25.186478623465738853816986648905599291667","NumberOfInstances":"1","Modality":["CR"]},{"taskID":"1938","dicomDeviceName":"demoj4c","queue":"Export10","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.863+0100","updatedTime":"2022-03-15T19:39:00.706+0100","scheduledTime":"2022-03-15T19:38:00.699+0100","processingStartTime":"2022-03-15T19:39:00.594+0100","processingEndTime":"2022-03-15T19:39:00.703+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series WADO Metadata","StudyInstanceUID":"2.25.153656090606480794239864790187400375890","NumberOfInstances":"1","Modality":["CR"]},{"taskID":"1900","dicomDeviceName":"demoj4c","queue":"Export10","type":"EXPORT","status":"FAILED","failures":"11","createdTime":"2022-03-10T13:15:58.517+0100","updatedTime":"2022-03-10T14:46:56.450+0100","scheduledTime":"2022-03-10T14:45:56.415+0100","processingStartTime":"2022-03-10T14:46:56.191+0100","processingEndTime":"2022-03-10T14:46:56.449+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series WADO Metadata","StudyInstanceUID":"1.3.6.1.4.1.37873.1.98.95913952367855087503341730515226595193","NumberOfInstances":"2468","Modality":["CT"]},{"taskID":"1905","dicomDeviceName":"demoj4c","queue":"Export10","type":"EXPORT","status":"FAILED","failures":"11","createdTime":"2022-03-10T13:18:21.966+0100","updatedTime":"2022-03-10T14:35:56.325+0100","scheduledTime":"2022-03-10T14:34:56.331+0100","processingStartTime":"2022-03-10T14:35:56.193+0100","processingEndTime":"2022-03-10T14:35:56.324+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series WADO Metadata","StudyInstanceUID":"1.2.826.0.1.3680043.2.1352.10.168.123.110.2834470","NumberOfInstances":"103","Modality":["MR"]},{"taskID":"1889","dicomDeviceName":"demoj4c","queue":"Export10","type":"EXPORT","status":"FAILED","failures":"11","createdTime":"2022-03-05T16:48:56.337+0100","updatedTime":"2022-03-05T18:05:56.300+0100","scheduledTime":"2022-03-05T18:04:56.323+0100","processingStartTime":"2022-03-05T18:05:56.213+0100","processingEndTime":"2022-03-05T18:05:56.299+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series WADO Metadata","StudyInstanceUID":"1.2.124.113532.12.10565.9380.20171220.90627.17583712","NumberOfInstances":"0"},{"taskID":"1895","dicomDeviceName":"demoj4c","queue":"Export10","type":"EXPORT","status":"FAILED","failures":"11","createdTime":"2022-03-05T16:48:56.559+0100","updatedTime":"2022-03-05T18:05:56.299+0100","scheduledTime":"2022-03-05T18:04:56.322+0100","processingStartTime":"2022-03-05T18:05:56.170+0100","processingEndTime":"2022-03-05T18:05:56.298+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series WADO Metadata","StudyInstanceUID":"1.2.124.113532.80.22166.36561.20181217.113442.21320","NumberOfInstances":"0"},{"taskID":"1806","dicomDeviceName":"demoj4c","queue":"Export10","type":"EXPORT","status":"FAILED","failures":"11","createdTime":"2022-02-25T16:16:40.377+0100","updatedTime":"2022-03-03T13:07:56.444+0100","scheduledTime":"2022-03-03T13:06:56.437+0100","processingStartTime":"2022-03-03T13:07:56.168+0100","processingEndTime":"2022-03-03T13:07:56.443+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series WADO Metadata","StudyInstanceUID":"2.25.229975568262236341440816121223809588447","NumberOfInstances":"2958","Modality":["CT","SR"]},{"taskID":"1757","dicomDeviceName":"demoj4c","queue":"Export10","type":"EXPORT","status":"FAILED","failures":"11","createdTime":"2022-02-25T15:59:25.587+0100","updatedTime":"2022-02-25T17:16:11.921+0100","scheduledTime":"2022-02-25T17:15:13.689+0100","processingStartTime":"2022-02-25T17:16:11.125+0100","processingEndTime":"2022-02-25T17:16:11.921+0100","errorMessage":"HTTP 404 Not Found","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series WADO Metadata","StudyInstanceUID":"2.25.314099000311973995718493894996536932361","NumberOfInstances":"0"},{"taskID":"1572","dicomDeviceName":"demoj4c","queue":"Export10","type":"EXPORT","status":"WARNING","batchID":"ATS Prefetch Priors on Store[1.2.276.0.37.1.406.201712.240110]","createdTime":"2022-02-08T18:45:29.664+0100","updatedTime":"2022-02-08T18:45:49.821+0100","scheduledTime":"2022-02-08T18:45:29.489+0100","processingStartTime":"2022-02-08T18:45:48.149+0100","processingEndTime":"2022-02-08T18:45:49.821+0100","outcomeMessage":"Query retrieved 1 objects by Exporter ATS Prefetch NG - Series WADO Metadata, failed: 3 - HTTP 404 Not Found","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series WADO Metadata","StudyInstanceUID":"2.25.109066430802510762536912094273354916506","NumberOfInstances":"1","Modality":["KO"]},{"taskID":"1372","dicomDeviceName":"demoj4c","queue":"Export10","type":"EXPORT","status":"WARNING","createdTime":"2022-01-26T11:19:42.244+0100","updatedTime":"2022-01-26T11:20:52.979+0100","scheduledTime":"2022-01-26T11:20:42.243+0100","processingStartTime":"2022-01-26T11:20:52.407+0100","processingEndTime":"2022-01-26T11:20:52.979+0100","outcomeMessage":"Query retrieved 1 objects by Exporter ATS Prefetch NG - Series WADO Metadata, failed: 3 - HTTP 404 Not Found","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series WADO Metadata","StudyInstanceUID":"1.3.12.2.1107.5.8.3.485251.834954.83838049.2020073010062484","NumberOfInstances":"1","Modality":["KO"]},{"taskID":"1349","dicomDeviceName":"demoj4c","queue":"Export10","type":"EXPORT","status":"WARNING","failures":"2","createdTime":"2022-01-26T11:05:57.309+0100","updatedTime":"2022-01-26T11:10:30.402+0100","scheduledTime":"2022-01-26T11:09:30.745+0100","processingStartTime":"2022-01-26T11:10:29.889+0100","processingEndTime":"2022-01-26T11:10:30.402+0100","errorMessage":"HTTP 404 Not Found","outcomeMessage":"Query retrieved 2 objects by Exporter ATS Prefetch NG - Series WADO Metadata, failed: 1 - HTTP 404 Not Found","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series WADO Metadata","StudyInstanceUID":"1.3.12.2.1107.5.8.3.485251.834954.83838049.2020073010062484","NumberOfInstances":"27","Modality":["CT"]},{"taskID":"1318","dicomDeviceName":"demoj4c","queue":"Export10","type":"EXPORT","status":"WARNING","createdTime":"2022-01-21T15:29:10.379+0100","updatedTime":"2022-01-21T15:30:32.111+0100","scheduledTime":"2022-01-21T15:30:10.909+0100","processingStartTime":"2022-01-21T15:30:29.868+0100","processingEndTime":"2022-01-21T15:30:32.110+0100","outcomeMessage":"Query retrieved 1 objects by Exporter ATS Prefetch NG - Series WADO Metadata, failed: 3 - HTTP 404 Not Found","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series WADO Metadata","StudyInstanceUID":"2.25.109066430802510762536912094273354916506","NumberOfInstances":"1","Modality":["KO"]},{"taskID":"1336","dicomDeviceName":"demoj4c","queue":"Export10","type":"EXPORT","status":"WARNING","createdTime":"2022-01-21T15:29:11.011+0100","updatedTime":"2022-01-21T15:30:31.941+0100","scheduledTime":"2022-01-21T15:30:11.010+0100","processingStartTime":"2022-01-21T15:30:29.898+0100","processingEndTime":"2022-01-21T15:30:31.941+0100","outcomeMessage":"Query retrieved 2 objects by Exporter ATS Prefetch NG - Series WADO Metadata, failed: 3 - HTTP 404 Not Found","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series WADO Metadata","StudyInstanceUID":"1.2.4.0.13.1.432252867.1552278.1","NumberOfInstances":"1","Modality":["KO"]},{"taskID":"1336","dicomDeviceName":"demoj4c","queue":"Export10","type":"EXPORT","status":"WARNING","createdTime":"2022-01-21T15:29:11.011+0100","updatedTime":"2022-01-21T15:30:31.941+0100","scheduledTime":"2022-01-21T15:30:11.010+0100","processingStartTime":"2022-01-21T15:30:29.898+0100","processingEndTime":"2022-01-21T15:30:31.941+0100","outcomeMessage":"Query retrieved 2 objects by Exporter ATS Prefetch NG - Series WADO Metadata, failed: 3 - HTTP 404 Not Found","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series WADO Metadata","StudyInstanceUID":"1.2.4.0.13.1.432252867.1552278.1","NumberOfInstances":"1","Modality":["KO"]}];
                    }
                    if (res && res.length > 0){
/*                        $this.matches = res.map((properties, index) => {
                            $this.cfpLoadingBar.complete();
                            return {
                                offset: offset + index,
                                properties: properties,
                                showProperties: false
                            };
                        });*/
                        $this.cfpLoadingBar.complete();

                        this.matches = res;
                        this.moreTasks = res.length > this.filterObject['limit'];
                        if(this.moreTasks)
                            this.matches.splice(this.matches.length-1,1);
                    }else{
                        $this.matches = [];
                        $this.cfpLoadingBar.complete();
                        this.mainservice.showMsg($localize `:@@no_tasks_found:No tasks found!`)
                    }
                }, (err) => {
                    console.log('err', err);
                    $this.matches = [];
                });
        }else{
            this.mainservice.showError($localize `:@@no_queue_name:No Queue Name selected!`);
        }
    }
    getCount(){
        if(this.filterObject.queueName){
            this.cfpLoadingBar.start();
            this.setFilters();
            this.service.getCount(this.filterObject.queueName,
                this.filterObject.status,
                undefined,
                undefined,
                this.filterObject.dicomDeviceName,
                this.filterObject.createdTime,
                this.filterObject.updatedTime,
                this.filterObject.batchID,
                this.filterObject.localAET,
                this.filterObject.remoteAET, '').subscribe((count)=>{
                try{
                    this.counText = $localize `:@@count_param:COUNT ${count.count}:count:`;
                }catch (e){
                    this.counText = $localize `:@@COUNT:COUNT`;
                }
                this.setFilters();
                this.cfpLoadingBar.complete();
            },(err)=>{
                this.cfpLoadingBar.complete();
                this.httpErrorHandler.handleError(err);
            });
        }else{
            this.mainservice.showError($localize `:@@no_queue_name:No Queue Name selected!`);
        }
    }
    confirm(confirmparameters){
        //this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(ConfirmComponent, {
            height: 'auto',
            width: '500px'
        });
        this.dialogRef.componentInstance.parameters = confirmparameters;
        return this.dialogRef.afterClosed();
    };
    cancel(match) {
        let $this = this;
        $this.cfpLoadingBar.start();
        this.service.cancel(this.filterObject.queueName, match.taskID)
            .subscribe(function (res) {
                match.properties.status = 'CANCELED';
                $this.cfpLoadingBar.complete();
            }, (err) => {
                $this.cfpLoadingBar.complete();
                $this.httpErrorHandler.handleError(err);
            });
    };
    reschedule(match) {
        let $this = this;
        this.deviceService.selectParameters((res)=>{
                if(res){
                    $this.cfpLoadingBar.start();
                    let filter = {};
                    if(_.hasIn(res, "schema_model.newDeviceName") && res.schema_model.newDeviceName != ""){
                        filter["newDeviceName"] = res.schema_model.newDeviceName;
                    }
                    if(_.hasIn(res, "schema_model.scheduledTime") && res.schema_model.scheduledTime != ""){
                        filter["scheduledTime"] = res.schema_model.scheduledTime;
                    }
                    this.service.reschedule(this.filterObject.queueName, match.taskID, filter)
                        .subscribe((res) => {
                            $this.search(0);
                            $this.cfpLoadingBar.complete();
                        }, (err) => {
                            $this.cfpLoadingBar.complete();
                            $this.httpErrorHandler.handleError(err);
                        });
                }
            },
            this.devices);
    };
    checkAll(event){
        console.log("in checkall",event.target.checked);
        this.matches.forEach((match)=>{
            match.checked = event.target.checked;
        });
    }
    delete(match) {
        let $this = this;
        this.confirm({
            content: $localize `:@@want_to_delete_question:Are you sure you want to delete?`
        }).subscribe(result => {
            if (result){
                $this.cfpLoadingBar.start();

                this.service.delete(this.filterObject.queueName, match.taskID)
                .subscribe((res) => {
                    $this.search($this.matches[0].offset);
                    $this.cfpLoadingBar.complete();
                },(err)=>{
                    $this.cfpLoadingBar.complete();
                    $this.httpErrorHandler.handleError(err);
                });
            }
        }, (err) => {
            $this.cfpLoadingBar.complete();
            $this.httpErrorHandler.handleError(err);
        });
    };
    executeAll(mode){
        this.confirm({
            content: $localize `:@@action_selected_entries_question:Are you sure you want to ${Globalvar.getActionText(mode)} selected entries?`
        }).subscribe(result => {
            if (result){
                this.cfpLoadingBar.start();
                this.matches.forEach((match)=>{
                    if(match.selected){
                        this.service[mode](this.filterObject.queueName, match.taskID)
                            .subscribe((res) => {
                            },(err)=>{
                                this.httpErrorHandler.handleError(err);
                            });
                    }
                });
                setTimeout(()=>{
                    if(mode === "delete"){
                        this.search(this.matches[0].offset||0);
                    }else{
                        this.search(0);
                    }
                    this.cfpLoadingBar.complete();
                },300);
            }
        });
    }
    getQueueDescriptionFromName(queuename){
        let description;
        _.forEach(this.queues, (m, i) => {
            if (m.name == queuename){
                description = m.description;
            }
        });
        return description;
    };
    hasOlder(objs) {
        return objs && (objs.length === this.filterObject.limit);
    };
    hasNewer(objs) {
        return objs && objs.length && objs[0].offset;
    };
    newerOffset(objs) {
        return Math.max(0, objs[0].offset - this.filterObject.limit);
    };
    olderOffset(objs) {
        return objs[0].offset + this.filterObject.limit;
    };
    next(){
        if(this.moreTasks){
            let filter = Object.assign({},this.filterObject);
            if(filter['limit']){
                this.filterObject['offset'] = filter['offset'] = filter['offset']*1 + this.filterObject['limit']*1;
                filter['limit']++;
            }
            this.search(filter.offset);
        }
    }
    prev(){
        if(this.filterObject["offset"] > 0){
            let filter = Object.assign({},this.filterObject);
            if(filter['limit'])
                this.filterObject['offset'] = filter['offset'] = filter['offset']*1 - this.filterObject['limit']*1;
            this.search(filter.offset);
        }
    }
    initQuery() {
        let $this = this;
        this.cfpLoadingBar.start();
        this.getLocalAEs();
        this.getRemoteAEs();
        this.$http.get(`${j4care.addLastSlash(this.mainservice.baseUrl)}queue`)
            // .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res; }catch (e){ resjson = [];} return resjson;})
            .subscribe((res) => {
                $this.getDevices();
                $this.queues = res;
                if(!this.urlParam && !this.filterObject.queueName)
                    this.filterObject.queueName = res[0].name;
                $this.cfpLoadingBar.complete();
            });
    }
    setFilters(){
        this.filterSchema = j4care.prepareFlatFilterObject(this.service.getFilterSchema(this.queues,this.devices,this.localAETs,this.remoteAETs,this.counText),3);
        if(this.urlParam) {
            this.filterObject["queueName"] = this.filterObject["queueName"] || 'Export';
            this.filterObject["orderby"] = this.filterObject["orderby"] || '-updatedTime';
        }
    }
    getDevices(){
        this.cfpLoadingBar.start();
        this.service.getDevices().subscribe(devices=>{
            this.cfpLoadingBar.complete();
            this.devices = j4care.mapDevicesToDropdown(
                devices.filter(dev => dev.hasArcDevExt),
                (device:Device)=>new SelectDropdown(device.dicomDeviceName,device.dicomDeviceName, device.dicomDescription, device.dicomDescription ? `${device.dicomDescription} ( ${device.dicomDeviceName} )` : device.dicomDeviceName)
            );
            this.setFilters();
            if(this.urlParam && Object.keys(this.urlParam).length > 0)
                this.search(0);
        },(err)=>{
            this.cfpLoadingBar.complete();
            console.error("Could not get devices",err);
        });
    }
    getRemoteAEs(){
        this.aeListService.getAes()
            .subscribe((response)=>{
            this.remoteAETs = response;
        });
    }
    getLocalAEs(){
        this.aeListService.getAets()
            .subscribe((response)=>{
                this.localAETs = response;
            });
    }
    ngOnDestroy(){
        if(this.timer.started){
            this.timer.started = false;
            clearInterval(this.refreshInterval);
        }
        this.destroyed$.next(true);
        this.destroyed$.complete();
    }
}
