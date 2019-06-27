import {Component, OnDestroy, OnInit, ViewContainerRef} from '@angular/core';
import {User} from '../../models/user';
import {Http} from '@angular/http';
import {ConfirmComponent} from '../../widgets/dialogs/confirm/confirm.component';
import {MatDialogConfig, MatDialog, MatDialogRef} from '@angular/material';
import * as _ from 'lodash';
import {AppService} from '../../app.service';
import {ExportService} from './export.service';
import {ExportDialogComponent} from '../../widgets/dialogs/export/export.component';
import {WindowRefService} from "../../helpers/window-ref.service";
import {HttpErrorHandler} from "../../helpers/http-error-handler";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {j4care} from "../../helpers/j4care.service";
import * as FileSaver from 'file-saver';
import {LoadingBarService} from "@ngx-loading-bar/core";
import {Globalvar} from "../../constants/globalvar";
import {ActivatedRoute} from "@angular/router";
import {CsvUploadComponent} from "../../widgets/dialogs/csv-upload/csv-upload.component";
import {AeListService} from "../../configuration/ae-list/ae-list.service";
import {PermissionService} from "../../helpers/permissions/permission.service";
import {Validators} from "@angular/forms";
import {KeycloakService} from "../../helpers/keycloak-service/keycloak.service";


@Component({
  selector: 'app-export',
  templateUrl: './export.component.html'
})
export class ExportComponent implements OnInit, OnDestroy {
    matches = [];
    user: User;
    exporters;
    exporterID;
    showMenu;
    aets;
    exportTasks = [];
    timer = {
        started:false,
        startText:"Start Auto Refresh",
        stopText:"Stop Auto Refresh"
    };
    statusValues = {};
    refreshInterval;
    interval = 10;
    Object = Object;
    batchGrouped = false;
    dialogRef: MatDialogRef<any>;
    _ = _;
    devices;
    count;
    allAction;
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
    allActionsActive = [];
    tableHovered = false;
    filterSchema;
    filterObject:any = {};
    urlParam;
    constructor(
        public $http:J4careHttpService,
        public cfpLoadingBar: LoadingBarService,
        public mainservice: AppService,
        public  service: ExportService,
        public viewContainerRef: ViewContainerRef,
        public dialog: MatDialog,
        public config: MatDialogConfig,
        private httpErrorHandler:HttpErrorHandler,
        private route: ActivatedRoute,
        public aeListService:AeListService,
        private permissionService:PermissionService,
        private _keycloakService: KeycloakService
    ) {}
    ngOnInit(){
        this.initCheck(10);
    }
    initCheck(retries){
        let $this = this;
        if((KeycloakService.keycloakAuth && KeycloakService.keycloakAuth.authenticated) || (_.hasIn(this.mainservice,"global.notSecure") && this.mainservice.global.notSecure)){
            this.route.queryParams.subscribe(params => {
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
        this.route.queryParams.subscribe(params => {
            if(params && params['dicomDeviceName']){
                this.filterObject['dicomDeviceName'] = params['dicomDeviceName'];
                this.search(0);
            }
        });
        this.initExporters(1);
        this.getAets();
        // this.init();
        this.service.statusValues().forEach(status =>{
            this.statusValues[status] = {
                count: 0,
                loader: false
            };
        });
        this.statusChange();
    }
    initSchema(){
        this.filterSchema = this.service.getFilterSchema(this.exporters, this.devices,`COUNT ${((this.count || this.count == 0)?this.count:'')}`);
        if(this.urlParam){
            this.filterObject = this.urlParam;
            this.filterObject["limit"] = 20;
        }
    }
    onFormChange(e){
        console.log("e",e);
        this.statusChange();
    }
    // changeTest(e){
    //     console.log("changetest",e);
    //     this.filterObject.createdTime = e;
    // }

    filterKeyUp(e){
        let code = (e.keyCode ? e.keyCode : e.which);
        if (code === 13){
            this.search(0);
        }
    };
    confirm(confirmparameters){
        this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(ConfirmComponent,{
            height: 'auto',
            width: '465px'
        });
        this.dialogRef.componentInstance.parameters = confirmparameters;
        return this.dialogRef.afterClosed();
    };
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
    getCounts(){
        let filters = Object.assign({},this.filterObject);
        if(!this.tableHovered)
            this.search(0);
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
                if(!this.mainservice.global.notSecure){
                    // WindowRefService.nativeWindow.open(`../monitor/export?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&access_token=${token}&${this.mainservice.param(this.service.paramWithoutLimit(this.filterObject))}`);
                    j4care.downloadFile(`../monitor/export?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&access_token=${token}&${this.mainservice.param(this.service.paramWithoutLimit(this.filterObject))}`, "export.csv")
                }else{
                    // WindowRefService.nativeWindow.open(`../monitor/export?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&${this.mainservice.param(this.service.paramWithoutLimit(this.filterObject))}`);
                    j4care.downloadFile(`../monitor/export?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&${this.mainservice.param(this.service.paramWithoutLimit(this.filterObject))}`,"export.csv")
                }
            });
        });
    }
    uploadCsv(){
        this.dialogRef = this.dialog.open(CsvUploadComponent, {
            height: 'auto',
            width: '500px'
        });
        this.dialogRef.componentInstance.params = {
            exporterID:this.exporterID || '',
            batchID:this.filterObject['batchID'] || '',
            formSchema:[
                {
                    tag:"input",
                    type:"checkbox",
                    filterKey:"semicolon",
                    description:"Use semicolon as delimiter"
                },
                {
                    tag:"select",
                    options:this.aets,
                    showStar:true,
                    filterKey:"LocalAET",
                    description:"Local AET",
                    placeholder:"Local AET",
                    validation:Validators.required
                },
                {
                    tag:"select",
                    options:this.exporters.map(exporter=>{
                        return {
                            value:exporter.id,
                            text:exporter.id
                        }
                    }),
                    showStar:true,
                    filterKey:"exporterID",
                    description:"Exporter ID",
                    placeholder:"Exporter ID",
                    validation:Validators.required
                },{
                    tag:"input",
                    type:"number",
                    filterKey:"field",
                    description:"Field",
                    placeholder:"Field",
                    validation:Validators.minLength(1),
                    defaultValue:1
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
                if(filter['batchID']) clonedFilters['batchID'] = filter['batchID'];
                return `../aets/${filter.LocalAET}/export/${filter.exporterID}/studies/csv:${filter.field}${j4care.getUrlParams(clonedFilters)}`;
            }
        };
        this.dialogRef.afterClosed().subscribe((ok)=>{
            if(ok){
                console.log("ok",ok);
                //TODO
            }
        });
    }
    showTaskDetail(task){
        this.filterObject.batchID = task.properties.batchID;
        this.batchGrouped = false;
        this.search(0);
    }
    search(offset) {
        let $this = this;
        $this.cfpLoadingBar.start();
        this.service.search(this.filterObject, offset,this.batchGrouped)
            .map(res => j4care.redirectOnAuthResponse(res))
            .subscribe((res) => {
                if (res && res.length > 0){
                    $this.matches = res.map((properties, index) => {
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
                            $this.cfpLoadingBar.complete();
                            return {
                                offset: offset + index,
                                properties: properties,
                                propertiesAttr: propertiesAttr,
                                showProperties: false
                            };
                        }else{
                            $this.cfpLoadingBar.complete();
                            if (_.hasIn(properties, 'Modality')){
                                properties.Modality = properties.Modality.join(',');
                            }
                            return {
                                offset: offset + index,
                                properties: properties,
                                propertiesAttr: properties,
                                showProperties: false
                            };
                        }
                    });
                }else{
                    $this.cfpLoadingBar.complete();
                    $this.matches = [];
                    this.mainservice.showMsg('No tasks found!')
                }
            }, (err) => {
                $this.cfpLoadingBar.complete();
                $this.matches = [];
                console.log('err', err);
            });
    };
    bachChange(e){
        this.matches = [];
    }
    getCount(){
        this.cfpLoadingBar.start();
        this.service.getCount(this.filterObject).subscribe((count)=>{
            try{
                this.count = count.count;
            }catch (e){
                this.count = "";
            }
            this.cfpLoadingBar.complete();
        },(err)=>{
            this.cfpLoadingBar.complete();
            this.httpErrorHandler.handleError(err);
        });
    }
    statusChange(){
/*        this.allActionsActive = this.allActionsOptions.filter((o)=>{
            if(this.filterObject.status == "SCHEDULED" || this.filterObject.status == "IN PROCESS"){
                return o.value != 'reschedule';
            }else{
                if(!this.filterObject.status || this.filterObject.status === '*' || this.filterObject.status === '')
                    return o.value != 'cancel' && o.value != 'reschedule';
                else
                    return o.value != 'cancel';
            }
        });*/
    }
    allActionChanged(e){
        let text = `Are you sure, you want to ${this.allAction} all matching tasks?`;
        let filter = _.cloneDeep(this.filterObject);
        if(filter.status === '*')
            delete filter.status;
        if(filter.dicomDeviceName === '*')
            delete filter.dicomDeviceName;
        delete filter.limit;
        delete filter.offset;
        switch (this.allAction) {
            case "cancel":
                this.confirm({
                    content: text
                }).subscribe((ok) => {
                    if (ok) {
                        this.cfpLoadingBar.start();
                        this.service.cancelAll(filter).subscribe((res) => {
                            this.mainservice.showMsg(res.count + ' queues deleted successfully!')
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
                this.rescheduleDialog((ok)=>{
                    if (ok) {
                        this.cfpLoadingBar.start();
                        if(_.hasIn(ok, "schema_model.newDeviceName") && ok.schema_model.newDeviceName != ""){
                            filter["newDeviceName"] = ok.schema_model.newDeviceName;
                        }
                        this.service.rescheduleAll(filter,ok.schema_model.selectedExporter).subscribe((res)=>{
                            this.mainservice.showMsg(res.count + ' tasks rescheduled successfully!');
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
                        this.service.deleteAll(filter).subscribe((res)=>{
                            this.mainservice.showMsg(res.deleted + ' tasks deleted successfully!');
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
            default:
                this.allAction = "";
                this.allAction = undefined;
        }
    }
    getDifferenceTime(starttime, endtime,mode?){
        let start = new Date(starttime).getTime();
        let end = new Date(endtime).getTime();
        if (!start || !end || end < start){
            return null;
        }else{
            return this.msToTime(new Date(endtime).getTime() - new Date(starttime).getTime(),mode);
        }
    };
    checkAll(event){
        this.matches.forEach((match)=>{
            match.checked = event.target.checked;
        });
    }
    rescheduleDialog(callBack:Function,  schema_model?:any, title?:string, text?:string){
        this.confirm({
            content: title || 'Task reschedule',
            doNotSave:true,
            form_schema: this.service.getDialogSchema(this.exporters, this.devices, text),
            result: {
                schema_model: schema_model || {}
            },
            saveButton: 'SUBMIT'
        }).subscribe((ok)=>{
                callBack.call(this, ok);
        });
    }
    executeAll(mode){
        if(mode === "reschedule"){
            this.rescheduleDialog((ok)=>{
                if (ok) {
                    this.cfpLoadingBar.start();
                    let filter  = {};
                    let id;
                    if(_.hasIn(ok, "schema_model.newDeviceName") && ok.schema_model.newDeviceName != ""){
                        filter["newDeviceName"] = ok.schema_model.newDeviceName;
                    }
                    if(_.hasIn(ok, "schema_model.selectedExporter")){
                        id = ok.schema_model.selectedExporter;
                    }
                    this.matches.forEach((match, i)=>{
                        if(match.checked){
                            this.service.reschedule(match.properties.pk, id || match.properties.ExporterID, filter)
                                .subscribe(
                                    (res) => {
                                        this.mainservice.showMsg(`Task ${match.properties.pk} rescheduled successfully!`);
                                        if(this.matches.length === i+1){
                                            this.cfpLoadingBar.complete();
                                        }
                                    },
                                    (err) => {
                                        this.httpErrorHandler.handleError(err);
                                        if(this.matches.length === i+1){
                                            this.cfpLoadingBar.complete();
                                        }
                                    });
                        }
                        if(this.matches.length === i+1){
                            this.cfpLoadingBar.complete();
                        }
                    });
                }
                this.allAction = "";
                this.allAction = undefined;
            });
            ////
        }else{
            this.confirm({
                content: `Are you sure you want to ${mode} selected entries?`
            }).subscribe(result => {
                if (result){
                    this.cfpLoadingBar.start();
                    this.matches.forEach((match)=>{
                        if(match.checked){
                            this.service[mode](match.properties.pk)
                                .subscribe((res) => {
                                    console.log("Execute result",res);
                                },(err)=>{
                                    this.httpErrorHandler.handleError(err);
                                });
                        }
                    });
                    setTimeout(()=>{
                        this.search(this.matches[0].offset || 0);
                        this.cfpLoadingBar.complete();
                    },300);

                }
            });
        }
    }
    msToTime(duration,mode?) {
        if(mode)
            if(mode === "sec")
                return ((duration*6 / 6000).toFixed(4)).toString() + ' s';
        else
            return ((duration / 60000).toFixed(4)).toString() + ' min';
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
                        this.mainservice.showMsg(res.deleted + ' tasks deleted successfully!');
                        this.cfpLoadingBar.complete();
                        this.search(0);
                    }, (err) => {
                        this.cfpLoadingBar.complete();
                        this.httpErrorHandler.handleError(err);
                    });
                }else{
                    this.mainservice.showError('Batch ID not found!');
                }
            }
        });
    }
    delete(match){
        let $this = this;
        let parameters: any = {
            content: 'Are you sure you want to delete this task?',
            result: {
                select: this.exporters[0].id
            },
            saveButton: 'DELETE'
        };
        this.confirm(parameters).subscribe(result => {
            if (result){
                $this.cfpLoadingBar.start();
                this.service.delete(match.properties.pk)
                    .subscribe(
                        (res) => {
                            // match.properties.status = 'CANCELED';
                            $this.cfpLoadingBar.complete();
                            $this.search(0);
                            this.mainservice.showMsg('Task deleted successfully!')
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
            content: 'Are you sure you want to cancel this task?',
            result: {
                select: this.exporters[0].id
            },
            saveButton: 'YES'
        };
        this.confirm(parameters).subscribe(result => {
            if (result){
                $this.cfpLoadingBar.start();
                this.service.cancel(match.properties.pk)
                    .subscribe(
                        (res) => {
                            match.properties.status = 'CANCELED';
                            $this.cfpLoadingBar.complete();
                            this.mainservice.showMsg('Task canceled successfully!')
                        },
                        (err) => {
                            $this.cfpLoadingBar.complete();
                            console.log('cancleerr', err);
                            $this.httpErrorHandler.handleError(err);
                        });
            }
        });
    };
    reschedule(match) {
        this.rescheduleDialog((ok)=>{
            if (ok) {
                this.cfpLoadingBar.start();
                let filter  = {};
                let id;
                if(_.hasIn(ok, "schema_model.newDeviceName") && ok.schema_model.newDeviceName != ""){
                    filter["newDeviceName"] = ok.schema_model.newDeviceName;
                }
                if(_.hasIn(ok, "schema_model.selectedExporter")){
                    id = ok.schema_model.selectedExporter;
                }
                this.service.reschedule(match.properties.pk, id || match.properties.ExporterID, filter)
                    .subscribe(
                        (res) => {
                            this.mainservice.showMsg(`Task ${match.properties.pk} rescheduled successfully!`);
                                this.cfpLoadingBar.complete();
                        },
                        (err) => {
                            this.httpErrorHandler.handleError(err);
                                this.cfpLoadingBar.complete();
                        });

            }
        },
        {
            selectedExporter: match.properties.ExporterID
        },
        undefined,
        "Change the Exporter Id only if you wan't to reschedule to another exporter!"
        );
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

    initExporters(retries) {
        let $this = this;
        this.$http.get('../export')
            .map(res => j4care.redirectOnAuthResponse(res))
            .subscribe(
                (res) => {
                    $this.exporters = res;
                    if (res && res[0] && res[0].id){
                        $this.exporterID = res[0].id;
                    }
                    $this.getDevices();
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
            this.initSchema();
        },(err)=>{
            this.cfpLoadingBar.complete();
            console.error("Could not get devices",err);
        });
    }
    getAets(){
        this.aeListService.getAets()
            .map(aet=> this.permissionService.filterAetDependingOnUiConfig(aet,'internal'))
            .retry(3)
            .subscribe(aets=>{
                this.aets = aets.map(ae=>{
                    return {
                        value:ae.dicomAETitle,
                        text:ae.dicomAETitle
                    }
                })
            },(err)=>{
                console.error("Could not get aets",err);
            });
    }
    ngOnDestroy(){
        if(this.timer.started){
            this.timer.started = false;
            clearInterval(this.refreshInterval);
        }
    }
}
