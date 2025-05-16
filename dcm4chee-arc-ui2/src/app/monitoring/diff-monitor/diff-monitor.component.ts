import {Component, OnInit, ViewContainerRef} from '@angular/core';
import {DiffMonitorService} from "./diff-monitor.service";
import {AppService} from "../../app.service";
import {ActivatedRoute} from "@angular/router";
import * as _ from 'lodash-es';
import {LoadingBarService} from "@ngx-loading-bar/core";
import {AeListService} from "../../configuration/ae-list/ae-list.service";
import {j4care} from "../../helpers/j4care.service";
import {HttpErrorHandler} from "../../helpers/http-error-handler";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {ConfirmComponent} from "../../widgets/dialogs/confirm/confirm.component";
// import { MatLegacyDialogConfig as MatDialogConfig, MatLegacyDialog as MatDialog, MatLegacyDialogRef as MatDialogRef } from "@angular/material/legacy-dialog";
import {MatDialog, MatDialogRef} from "@angular/material/dialog";
import {Globalvar} from "../../constants/globalvar";
import {PermissionService} from "../../helpers/permissions/permission.service";
import {DevicesService} from "../../configuration/devices/devices.service";
import {KeycloakService} from "../../helpers/keycloak-service/keycloak.service";
import {forkJoin} from "rxjs";
import {map} from "rxjs/operators";
import {CsvUploadComponent} from "../../widgets/dialogs/csv-upload/csv-upload.component";
import {Validators} from "@angular/forms";
import {environment} from "../../../environments/environment";

@Component({
    selector: 'diff-monitor',
    templateUrl: './diff-monitor.component.html',
    styleUrls: ['./diff-monitor.component.scss'],
    standalone: false
})
export class DiffMonitorComponent implements OnInit {

    filterObject:any = {};
    filterSchema = [];
    urlParam;
    aes;
    aets;
    devices;
    batchGrouped = false;
    tasks = [];
    config;
    tableConfigNormal;
    tableConfigGrouped;
    moreTasks;
    dialogRef: MatDialogRef<any>;
    count;
    statusValues = {};
    refreshInterval;
    interval = 10;
    timer = {
        started:false,
        startText:$localize `:@@start_auto_refresh:Start Auto Refresh`,
        stopText:$localize `:@@stop_auto_refresh:Stop Auto Refresh`
    };
    Object = Object;
    filterTreeHeight = 3;
    tableHovered = false;
    allAction;
    allActionsActive = [];
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
    constructor(
        private service:DiffMonitorService,
        private mainservice:AppService,
        private route: ActivatedRoute,
        private cfpLoadingBar: LoadingBarService,
        private aeListService:AeListService,
        private httpErrorHandler:HttpErrorHandler,
        public viewContainerRef: ViewContainerRef,
        public dialog: MatDialog,
        private permissionService:PermissionService,
        private deviceService:DevicesService,
        private _keycloakService: KeycloakService
    ){}

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
        forkJoin(
            this.aeListService.getAes().pipe(map(aet=> this.permissionService.filterAetDependingOnUiConfig(aet,'external'))),
            this.aeListService.getAets().pipe(map(aet=> this.permissionService.filterAetDependingOnUiConfig(aet,'internal'))),
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
            this.setTableSchema();
        });
        this.onFormChange(this.filterObject);
    }

    onFormChange(filters){
        this.setTableSchema();
/*        this.allActionsActive = this.allActionsOptions.filter((o)=>{
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
    initSchema(){
        this.filterSchema = j4care.prepareFlatFilterObject(this.service.getFormSchema(this.aes, this.aets,$localize `:@@count_param:COUNT ${((this.count || this.count == 0)?this.count:'')}:count:`,this.devices),3);
        if (this.urlParam)
            this.filterObject["orderby"] = '-updatedTime';
    }
    setTableSchema(){
        this.tableConfigNormal = {
            table:j4care.calculateWidthOfTable(this.service.getTableColumns(this, this.action, {filterObject: this.filterObject})),
            filter:this.filterObject
        };
        this.tableConfigGrouped = {
            table:j4care.calculateWidthOfTable(this.service.getTableBatchGroupedColumns((e)=>{
                this.showDetails(e)
            })),
            filter:this.filterObject
        };


    }
    allActionChanged(e){
        let text = $localize `:@@matching_task_question:Are you sure, you want to ${Globalvar.getActionText(this.allAction)} all matching tasks?`;
        let filter = Object.assign({}, this.filterObject);
        delete filter["limit"];
        delete filter["offset"];
        this.confirm({
            content: text
        }).subscribe((ok)=>{
            if(ok){
                this.cfpLoadingBar.start();
                switch (this.allAction){
                    case "cancel":
                        this.service.cancelAll(this.filterObject).subscribe((res)=>{
                            this.mainservice.showMsg($localize `:@@tasks_deleted_param:${res.count}:tasks: tasks deleted successfully!`);
                            this.cfpLoadingBar.complete();
                        }, (err) => {
                            this.cfpLoadingBar.complete();
                            this.httpErrorHandler.handleError(err);
                        });

                        break;
                    case "reschedule":
                        this.cfpLoadingBar.complete();
                        this.deviceService.selectParametersForMatching((res)=>{
                                if(res){
                                    this.cfpLoadingBar.start();
                                    let filter = Object.assign({},this.filterObject);
                                    if(_.hasIn(res, "schema_model.newDeviceName") && res.schema_model.newDeviceName != ""){
                                        filter["newDeviceName"] = res.schema_model.newDeviceName;
                                    }
                                    if(_.hasIn(res, "schema_model.scheduledTime") && res.schema_model.scheduledTime != ""){
                                        filter["scheduledTime"] = res.schema_model.scheduledTime;
                                    }
                                    delete filter["limit"];
                                    delete filter["offset"];
                                    this.service.rescheduleAll(filter).subscribe((res)=>{
                                        this.mainservice.showMsg($localize `:@@tasks_rescheduled_param:${res.count}:count: tasks rescheduled successfully!`);
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
                        this.deleteAllTasks(this.filterObject);
                        break;
                }
                this.cfpLoadingBar.complete();
            }
            this.allAction = "";
            this.allAction = undefined;
        });
    }
    onSubmit(e){
        console.log("e",e);
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

            if(!environment.production){
                if(this.batchGrouped){
                    tasks = [{"batchID":"test2","tasks":{"warning":"1"},"dicomDeviceName":["dcm4chee-arc"],"LocalAET":["DCM4CHEE"],"PrimaryAET":["DCM4CHEE"],"SecondaryAET":["DEVJ4C"],"comparefield":["patient"],"checkMissing":["false"],"checkDifferent":["true"],"matches":31,"different":9,"createdTimeRange":["2022-03-25T10:17:29.723-0600","2022-03-25T10:17:29.723-0600"],"updatedTimeRange":["2022-03-25T10:17:30.995-0600","2022-03-25T10:17:30.995-0600"],"scheduledTimeRange":["2022-03-25T10:17:29.721-0600","2022-03-25T10:17:29.721-0600"],"processingStartTimeRange":["2022-03-25T10:17:29.790-0600","2022-03-25T10:17:29.790-0600"],"processingEndTimeRange":["2022-03-25T10:17:30.994-0600","2022-03-25T10:17:30.994-0600"]},{"batchID":"2221712246","tasks":{"warning":"1"},"dicomDeviceName":["dcm4chee-arc"],"LocalAET":["DCM4CHEE"],"PrimaryAET":["DCM4CHEE"],"SecondaryAET":["DEVJ4C"],"comparefield":["patient"],"checkMissing":["false"],"checkDifferent":["true"],"matches":31,"different":9,"createdTimeRange":["2022-03-17T12:02:47.661-0600","2022-03-17T12:02:47.661-0600"],"updatedTimeRange":["2022-03-17T12:02:49.070-0600","2022-03-17T12:02:49.070-0600"],"scheduledTimeRange":["2022-03-17T12:02:47.661-0600","2022-03-17T12:02:47.661-0600"],"processingStartTimeRange":["2022-03-17T12:02:47.691-0600","2022-03-17T12:02:47.691-0600"],"processingEndTimeRange":["2022-03-17T12:02:49.069-0600","2022-03-17T12:02:49.069-0600"]}];
                }else{
                    tasks = [{"taskID":"63","dicomDeviceName":"dcm4chee-arc","queue":"DiffTasks","type":"DIFF","status":"WARNING","createdTime":"2022-03-17T12:02:47.661-0600","updatedTime":"2022-03-17T12:02:49.070-0600","scheduledTime":"2022-03-17T12:02:47.661-0600","processingStartTime":"2022-03-17T12:02:47.691-0600","processingEndTime":"2022-03-17T12:02:49.069-0600","outcomeMessage":"31 studies compared, different: 9","RequesterUserID":"admin","RequesterHostName":"192.168.0.87","RequestURI":"http://shefki-lifebook:8080/dcm4chee-arc/aets/DCM4CHEE/dimse/DCM4CHEE/diff/DEVJ4C/studies","LocalAET":"DCM4CHEE","PrimaryAET":"DCM4CHEE","SecondaryAET":"DEVJ4C","QueryString":"queue=true&batchID=2221712246&comparefield=patient&priority=0","checkDifferent":true,"comparefield":"patient","matches":31,"different":9},
                    {"taskID":"64","dicomDeviceName":"dcm4chee-arc","queue":"DiffTasks","type":"DIFF","status":"WARNING","batchID":"2221712243","createdTime":"2022-03-17T12:06:47.661-0600","updatedTime":"2022-03-17T12:07:49.070-0600","scheduledTime":"2022-03-17T12:08:47.661-0600","processingStartTime":"2022-03-17T12:08:47.691-0600","processingEndTime":"2022-03-17T12:08:49.069-0600","outcomeMessage":"31 studies compared, different: 9","RequesterUserID":"admin","RequesterHostName":"192.168.0.87","RequestURI":"http://shefki-lifebook:8080/dcm4chee-arc/aets/DCM4CHEE/dimse/DCM4CHEE/diff/DEVJ4C/studies","LocalAET":"DCM4CHEE","PrimaryAET":"DCM4CHEE","SecondaryAET":"DEVJ4C","QueryString":"queue=true&batchID=2221712246&comparefield=patient&priority=0","checkDifferent":true,"comparefield":"patient","matches":31,"different":8}];
                }
            }

            if(tasks && tasks.length && tasks.length > 0){
                if(this.batchGrouped){
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
                    this.tasks = tasks;
                }
                this.moreTasks = tasks.length > this.filterObject['limit'];
                if(this.moreTasks)
                    this.tasks.splice(this.tasks.length-1,1);
                // this.tasks = tasks;
            }else{
                this.mainservice.showMsg($localize `:@@diff-monitor.no_diff:No diff tasks found!`);
            }
            this.cfpLoadingBar.complete();
        },err=>{
            this.cfpLoadingBar.complete();
            this.httpErrorHandler.handleError(err);
        })
    }
    deleteAllTasks(filter){
        this.service.deleteAll(filter).subscribe((res)=>{
            this.mainservice.showMsg($localize `:@@tasks_deleted_param:${res.deleted}:tasks: tasks deleted successfully!`);
            this.cfpLoadingBar.complete();
            let filters = Object.assign({},this.filterObject);
            this.getDiffTasks(filters);
        }, (err) => {
            this.cfpLoadingBar.complete();
            this.httpErrorHandler.handleError(err);
        });
    }
    action(mode, match){
        console.log("in action",mode,"match",match);
        if(mode && match && match.taskID){
            this.confirm({
                content: $localize `:@@action_task_question:Are you sure you want to ${Globalvar.getActionText(mode)} this task?`
            }).subscribe(ok => {
                if (ok){
                    switch (mode) {
                        case 'reschedule':
                            this.deviceService.selectParameters((res)=>{
                                    if(res){
                                        this.cfpLoadingBar.start();
                                        let filter = {};
                                        if(_.hasIn(res, "schema_model.newDeviceName") && res.schema_model.newDeviceName != ""){
                                            filter["newDeviceName"] = res.schema_model.newDeviceName;
                                        }
                                        if(_.hasIn(res, "schema_model.scheduledTime") && res.schema_model.scheduledTime != ""){
                                            filter["scheduledTime"] = res.schema_model.scheduledTime;
                                        }
                                        this.service.reschedule(match.taskID, filter)
                                            .subscribe(
                                                (res) => {
                                                    this.getDiffTasks(this.filterObject['offset'] || 0);
                                                    this.cfpLoadingBar.complete();
                                                    this.mainservice.showMsg($localize `:@@task_rescheduled:Task rescheduled successfully!`);
                                                },
                                                (err) => {
                                                    this.cfpLoadingBar.complete();
                                                    this.httpErrorHandler.handleError(err);
                                                });
                                    }
                                },
                                this.devices);
                        break;
                        case 'delete':
                            this.cfpLoadingBar.start();
                            this.service.delete(match.taskID)
                                .subscribe(
                                    (res) => {
                                        // match.properties.status = 'CANCELED';
                                        this.cfpLoadingBar.complete();
                                        this.getDiffTasks(this.filterObject['offset'] || 0);
                                        this.mainservice.showMsg($localize `:@@diff-monitoring.task_deleted:Task deleted successfully!`);
                                    },
                                    (err) => {
                                        this.cfpLoadingBar.complete();
                                        this.httpErrorHandler.handleError(err);
                                    });
                        break;
                        case 'cancel':
                            this.cfpLoadingBar.start();
                            this.service.cancel(match.taskID)
                                .subscribe(
                                    (res) => {
                                        match.status = $localize `:@@CANCELED:CANCELED`;
                                        this.cfpLoadingBar.complete();
                                        this.mainservice.showMsg($localize `:@@task_canceled:Task canceled successfully!`);
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
        //this.dialogConfig.viewContainerRef = this.viewContainerRef;
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
            content:$localize `:@@use_semicolon_delimiter:Do you want to use semicolon as delimiter?`,
            cancelButton:$localize `:@@no:No`,
            saveButton:$localize `:@@Yes:Yes`,
            result:$localize `:@@yes:yes`
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
                delete filterClone['offset'];
                delete filterClone['limit'];
                if(!this.mainservice.global.notSecure){
                    // WindowRefService.nativeWindow.open(`../monitor/diff?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&access_token=${token}&${this.mainservice.param(filterClone)}`);
                    j4care.downloadFile(`${j4care.addLastSlash(this.mainservice.baseUrl)}monitor/diff?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&access_token=${token}&${this.mainservice.param(filterClone)}`,"diff.csv")
                }else{
                    // WindowRefService.nativeWindow.open(`../monitor/diff?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&${this.mainservice.param(filterClone)}`);
                    j4care.downloadFile(`${j4care.addLastSlash(this.mainservice.baseUrl)}monitor/diff?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&${this.mainservice.param(filterClone)}`,"diff.csv")
                }
            });
        })
    }
    uploadCsv(){
        this.dialogRef = this.dialog.open(CsvUploadComponent, {
            height: 'auto',
            width: '500px'
        });
        this.dialogRef.componentInstance.aes = this.aes;
        this.dialogRef.componentInstance.params = {
            LocalAET:this.filterObject['LocalAET']||'',
            PrimaryAET:this.filterObject['PrimaryAET']||'',
            SecondaryAET:this.filterObject['SecondaryAET']||'',
            batchID:this.filterObject['batchID']||'',
            formSchema:[
                {
                    tag:"input",
                    type:"checkbox",
                    filterKey:"semicolon",
                    description:$localize `:@@use_semicolon_as_delimiter:Use semicolon as delimiter`
                },{
                    tag:"select",
                    options:this.aets,
                    showStar:true,
                    filterKey:"LocalAET",
                    description:$localize `:@@local_aet:Local AET`,
                    placeholder:$localize `:@@local_aet:Local AET`,
                    validation:Validators.required
                },{
                    tag:"select",
                    options:this.aes,
                    showStar:true,
                    filterKey:"PrimaryAET",
                    description:$localize `:@@primary_aet:Primary AET`,
                    placeholder:$localize `:@@primary_aet:Primary AET`,
                    validation:Validators.required
                },{
                    tag:"select",
                    options:this.aes,
                    showStar:true,
                    filterKey:"SecondaryAET",
                    description:$localize `:@@secondary_aet:Secondary AET`,
                    placeholder:$localize `:@@secondary_aet:Secondary AET`,
                    validation:Validators.required
                },{
                    tag:"input",
                    type:"number",
                    filterKey:"field",
                    description:$localize `:@@field:Field`,
                    placeholder:$localize `:@@field:Field`,
                    validation:[Validators.minLength(1),Validators.min(1)],
                    defaultValue:1
                },{
                    tag:"input",
                    type:"checkbox",
                    filterKey:"missing",
                    description:$localize `:@@check_missing:Check Missing`
                },{
                    tag:"input",
                    type:"checkbox",
                    filterKey:"different",
                    description:$localize `:@@check_different:Check Different`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"comparefield",
                    description:$localize `:@@compare_field:Compare field`,
                    placeholder:$localize `:@@compare_field:Compare field`
                },{
                    tag:"input",
                    type:"checkbox",
                    filterKey:"ForceQueryByStudyUID",
                    description:$localize `:@@force_query_by_study_uid:Force query by Study UID`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"SplitStudyDateRange",
                    description:$localize `:@@split_study_date_range:Split Study Date Range`,
                    placeholder:$localize `:@@split_study_date_range_duration_format:Split Study Date Range as per duration format`
                },{
                    tag:"input",
                    type:"number",
                    filterKey:"priority",
                    description:$localize `:@@priority:Priority`,
                    placeholder:$localize `:@@priority:Priority`
                },{
                    tag:"input",
                    type:"text",
                    filterKey:"batchID",
                    description:$localize `:@@batch_id:Batch ID`,
                    placeholder:$localize `:@@batch_id:Batch ID`
                }
            ],
            prepareUrl:(filter)=>{
                let clonedFilters = {};
                if(filter['missing']) clonedFilters['missing'] = filter['missing'];
                if(filter['different']) clonedFilters['different'] = filter['different'];
                if(filter['compareField']) clonedFilters['compareField'] = filter['compareField'];
                if(filter['ForceQueryByStudyUID']) clonedFilters['ForceQueryByStudyUID'] = filter['ForceQueryByStudyUID'];
                if(filter['SplitStudyDateRange']) clonedFilters['SplitStudyDateRange'] = filter['SplitStudyDateRange'];
                if(filter['priority']) clonedFilters['priority'] = filter['priority'];
                if(filter['batchID']) clonedFilters['batchID'] = filter['batchID'];

                return `${j4care.addLastSlash(this.mainservice.baseUrl)}aets/${filter.LocalAET}/dimse/${filter.PrimaryAET}/diff/${filter.SecondaryAET}/studies/csv:${filter.field}${j4care.getUrlParams(clonedFilters)}`;
            }
        };
        this.dialogRef.afterClosed().subscribe((ok)=>{
            if(ok){
                console.log("ok",ok);
                //TODO
            }
        });
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
    keyUp(e){
        console.log("e",e);
        if(e.which === 13){
            this.getCounts();
        }
    }
    ngOnDestroy(){
        if(this.timer.started){
            this.timer.started = false;
            clearInterval(this.refreshInterval);
        }
    }
}
