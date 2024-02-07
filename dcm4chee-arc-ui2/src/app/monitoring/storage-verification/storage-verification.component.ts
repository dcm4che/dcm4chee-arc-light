import {Component, OnDestroy, OnInit, ViewContainerRef} from '@angular/core';
import {LoadingBarService} from "@ngx-loading-bar/core";
import {AppService} from "../../app.service";
import * as _ from 'lodash-es';
import {StorageVerificationService} from "./storage-verification.service";
import {Globalvar} from "../../constants/globalvar";
import {j4care} from "../../helpers/j4care.service";
import {HttpErrorHandler} from "../../helpers/http-error-handler";
import {AeListService} from "../../configuration/ae-list/ae-list.service";
import {ConfirmComponent} from "../../widgets/dialogs/confirm/confirm.component";
import {J4careHttpService} from "../../helpers/j4care-http.service";
// import { MatLegacyDialog as MatDialog, MatLegacyDialogConfig as MatDialogConfig, MatLegacyDialogRef as MatDialogRef } from "@angular/material/legacy-dialog";
import {MatDialog, MatDialogRef} from "@angular/material/dialog";
import {PermissionService} from "../../helpers/permissions/permission.service";
import {DevicesService} from "../../configuration/devices/devices.service";
import {KeycloakService} from "../../helpers/keycloak-service/keycloak.service";
import {forkJoin} from "rxjs";
import {map} from 'rxjs/operators';
import {environment} from "../../../environments/environment";

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
      startText:$localize `:@@start_auto_refresh:Start Auto Refresh`,
      stopText:$localize `:@@stop_auto_refresh:Stop Auto Refresh`
    };
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
    tableConfigGrouped;
    tableConfigNormal;
    moreTasks;
    openedBlock = "monitor";
    triggerFilterSchema = [];
    triggerFilterSchemaHidden = [];
    triggerFilterObject = {};
    showFilter = false;
    constructor(
      private cfpLoadingBar: LoadingBarService,
      public mainservice: AppService,
      private aeListService:AeListService,
      private httpErrorHandler:HttpErrorHandler,
      private service:StorageVerificationService,
      public dialog: MatDialog,
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
        forkJoin(
            this.aeListService.getAets().pipe(map(aet=> this.permissionService.filterAetDependingOnUiConfig(aet,'internal'))),
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
    setTableSchemas(){
        this.tableConfigGrouped = {
            table:j4care.calculateWidthOfTable(this.service.getTableBatchGroupedColumens((e)=>{
                this.showDetails(e)
            })),
            filter:this.filterObject
        };
        this.tableConfigNormal = {
            table:j4care.calculateWidthOfTable(this.service.getTableSchema(this, this.action, {filterObject: this.filterObject})),
            filter:this.filterObject
        };

    }
    initSchema(){
        this.filterSchema = j4care.prepareFlatFilterObject(this.service.getFilterSchema( this.devices, this.localAET,$localize `:@@count_param:COUNT ${((this.count || this.count == 0)?this.count:'')}:count:`),3);
        this.filterObject["orderby"] = '-updatedTime';
        this.triggerFilterSchema = j4care.prepareFlatFilterObject([
                ...Globalvar.STUDY_FILTER_SCHEMA(this.localAET, [], []).filter((filter, i)=>{
                    return i < 14 && filter.filterKey != "limit";
                }),
                ...[
                    {
                        tag:"input",
                        type:"text",
                        filterKey:"batchID",
                        description:$localize `:@@batch_id:Batch ID`,
                        placeholder:$localize `:@@batch_id:Batch ID`
                    },
                    {
                        tag:"select",
                        options:[
                            {
                                value:"DB_RECORD_EXISTS",
                                text:$localize `:@@DB_RECORD_EXISTS:DB_RECORD_EXISTS`,
                                title:$localize `:@@check_for_existence_of_db_records:Check for existence of DB records`
                            },
                            {
                                value:"OBJECT_EXISTS",
                                text:$localize `:@@OBJECT_EXISTS:OBJECT_EXISTS`,
                                title:$localize `:@@storage-verification.check_if_object_exists_on_storage_system:check if object exists on Storage System`
                            },
                            {
                                value:"OBJECT_SIZE",
                                text:$localize `:@@OBJECT_SIZE:OBJECT_SIZE`,
                                title:$localize `:@@storage-verification.check_size_of_object_on_storage_system:check size of object on Storage System`
                            },
                            {
                                value:"OBJECT_FETCH",
                                text:$localize `:@@OBJECT_FETCH:OBJECT_FETCH`,
                                title:$localize `:@@fetch_object_from_storage_system:Fetch object from Storage System`
                            },
                            {
                                value:"OBJECT_CHECKSUM",
                                text:$localize `:@@OBJECT_CHECKSUM:OBJECT_CHECKSUM`,
                                title:$localize `:@@recalculate_checksum_of_object_on_storage_system:recalculate checksum of object on Storage System`
                            },
                            {
                                value:"S3_MD5SUM",
                                text:$localize `:@@S3_MD5SUM:S3_MD5SUM`,
                                title:$localize `:@@check_MD5_checksum_on_S3:Check MD5 checksum of object on S3 Storage System`
                            }
                        ],
                        showStar:true,
                        filterKey:"storageVerificationPolicy",
                        description:$localize `:@@verification_policy:Verification Policy`,
                        placeholder:$localize `:@@verification_policy:Verification Policy`
                    },
                    {
                        tag:"checkbox",
                        filterKey:"storageVerificationUpdateLocationStatus",
                        text:$localize `:@@storage-verification.update_location:Update location`,
                        description:$localize `:@@update_location_db:Update Location DB`
                    },
                    {
                        tag:"checkbox",
                        filterKey:"storageVerificationFailed",
                        text:$localize `:@@storage-verification.failed_verification:Failed verification`,
                        description:$localize `:@@failed_storage_verification:Failed storage verification`
                    },
                    {
                        tag:"button",
                        text:$localize `:@@TRIGGER:TRIGGER`,
                        description:$localize `:@@TRIGGER:TRIGGER`
                    }
                ]
        ]);
        this.triggerFilterSchemaHidden = j4care.prepareFlatFilterObject([
                ...Globalvar.STUDY_FILTER_SCHEMA(this.localAET, [], [], true),
                ...Globalvar.STUDY_FILTER_SCHEMA(this.localAET, [], []).filter((filter, i)=>{
                    return i > 13 && filter.filterKey != "limit";
                })
            ],3);
        this.setTableSchemas();
    }
    toggleAutoRefresh(){
        this.timer.started = !this.timer.started;
        if(this.timer.started){
            this.getCounts(true);
            this.refreshInterval = setInterval(()=>{
                this.getCounts(true);
            },this.interval*1000);
        }else
            clearInterval(this.refreshInterval);
    }
    onTriggerSubmit(object){
        console.log("this.triggerFilterObject",this.triggerFilterObject);
        this.cfpLoadingBar.start();
        if(this.triggerFilterObject["aet"]){
            let filter = Object.assign({},this.triggerFilterObject);
            let aet = filter["aet"];
            delete filter["aet"];
            this.service.scheduleStorageVerification(filter, aet).subscribe((res)=>{
                this.cfpLoadingBar.complete();
                this.mainservice.showMsg($localize `:@@storage_verification_scheduled:Storage Verification scheduled successfully!`);
                setTimeout(()=>{
                    this.filterObject["batchID"] = filter["batchID"] || this.service.getUniqueID();
                    this.batchGrouped = true;
                    this.openedBlock = "monitor";
                    this.getCounts(true);
                },500);
            },(err)=>{
                this.cfpLoadingBar.complete();
                this.httpErrorHandler.handleError(err);
            });
        }else{
            this.mainservice.showError($localize `:@@aet_required:Aet is required!`);
            this.cfpLoadingBar.complete();
        }
    }
    onSubmit(object){
        if(_.hasIn(object,"id") && _.hasIn(object,"model")){
            if(object.id === "submit"){
                let filter = Object.assign({},this.filterObject);
                // let filterCount = Object.assign({},this.filterObject);
                if(filter['limit'])
                    filter['limit']++;
                this.getTasks(filter);
                this.getCounts(false);
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
        let text = $localize `:@@matching_task_question:Are you sure, you want to ${Globalvar.getActionText(this.allAction)} all matching tasks?`;
        let filter = Object.assign({}, this.filterObject);
        delete filter.limit;
        delete filter.offset;
        this.confirm({
            content: text
        }).subscribe((ok)=>{
            if(ok){
                switch (this.allAction){
                    case "cancel":
                        this.cfpLoadingBar.start();
                        this.service.cancelAll(this.filterObject).subscribe((res)=>{
                            this.cfpLoadingBar.complete();
                            if(_.hasIn(res,"count")){
                                this.mainservice.showMsg($localize `:@@tasks_canceled_param:${res.count}:count: tasks canceled successfully!`);
                            }else{
                                this.mainservice.showMsg($localize `:@@tasks_canceled:Tasks canceled successfully!`);
                            }
                        }, (err) => {
                            this.cfpLoadingBar.complete();
                            this.httpErrorHandler.handleError(err);
                        });

                        break;
                    case "reschedule":
                        this.deviceService.selectParametersForMatching((res)=> {
                            if (res){
                                this.cfpLoadingBar.start();
                                if (_.hasIn(res, "schema_model.newDeviceName") && res.schema_model.newDeviceName != "") {
                                    filter["newDeviceName"] = res.schema_model.newDeviceName;
                                }
                                if(_.hasIn(res, "schema_model.scheduledTime") && res.schema_model.scheduledTime != ""){
                                    filter["scheduledTime"] = res.schema_model.scheduledTime;
                                }
                                this.service.rescheduleAll(filter).subscribe((res) => {
                                    this.cfpLoadingBar.complete();
                                    if(_.hasIn(res,"count")){
                                        this.mainservice.showMsg($localize `:@@tasks_rescheduled_param:${res.count}:count: tasks rescheduled successfully!`);
                                    }else{
                                        this.mainservice.showMsg($localize `:@@tasks_rescheduled:Tasks rescheduled successfully!`);
                                    }
                                }, (err) => {
                                    this.cfpLoadingBar.complete();
                                    this.httpErrorHandler.handleError(err);
                                });
                            }
                        },
                        this.devices);
                        break;
                    case "delete":
                        this.cfpLoadingBar.start();
                        this.service.deleteAll(this.filterObject).subscribe((res)=>{
                            this.cfpLoadingBar.complete();
                            if(_.hasIn(res,"deleted")){
                                this.mainservice.showMsg($localize `:@@tasks_deleted_param:${res.deleted}:tasks: tasks deleted successfully!`);
                            }else{
                                this.mainservice.showMsg($localize `:@@tasks_deleted:Tasks deleted successfully!`);
                            }
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
    getCounts(getTasks?){
        let filters = Object.assign({},this.filterObject);
        if(!this.tableHovered && getTasks){
            let filter = Object.assign({},this.filterObject);
            if(filter['limit']){
                this.filterObject['offset'] = 0;
                filter['limit']++;
            }
            this.getTasks(filter);
        }
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
        //this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(ConfirmComponent, {
            height: 'auto',
            width: '500px'
        });
        this.dialogRef.componentInstance.parameters = confirmparameters;
        return this.dialogRef.afterClosed();
    };
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
                delete filterClone.offset;
                delete filterClone.limit;
                if(!this.mainservice.global.notSecure){
                    // WindowRefService.nativeWindow.open(`../monitor/stgver?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&access_token=${token}&${this.mainservice.param(filterClone)}`);
                    j4care.downloadFile(`${j4care.addLastSlash(this.mainservice.baseUrl)}monitor/stgver?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&access_token=${token}&${this.mainservice.param(filterClone)}`,"storage_verification.csv")
                }else{
                    // WindowRefService.nativeWindow.open(`../monitor/stgver?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&${this.mainservice.param(filterClone)}`);
                    j4care.downloadFile(`${j4care.addLastSlash(this.mainservice.baseUrl)}monitor/stgver?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&${this.mainservice.param(filterClone)}`,"storage_verification.csv")
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
                if(!environment.production){
                    if(this.batchGrouped){
                        res = [{"batchID":"testbatch","tasks":{"scheduled":"708"},"dicomDeviceName":["devj4c"],"LocalAET":["DEVJ4C"],"createdTimeRange":["2022-03-24T19:43:16.534+0100","2022-03-24T19:43:18.785+0100"],"updatedTimeRange":["2022-03-24T19:43:16.534+0100","2022-03-24T19:43:18.785+0100"],"scheduledTimeRange":["2022-03-24T19:43:16.513+0100","2022-03-24T19:43:16.513+0100"]},{"batchID":"testbatch","tasks":{"scheduled":"521","in-process":"5","completed":"182"},"dicomDeviceName":["devj4c"],"LocalAET":["DEVJ4C"],"createdTimeRange":["2022-03-24T19:43:16.534+0100","2022-03-24T19:43:18.785+0100"],"updatedTimeRange":["2022-03-24T19:43:16.534+0100","2022-03-24T19:44:30.503+0100"],"scheduledTimeRange":["2022-03-24T19:43:16.513+0100","2022-03-24T19:43:16.513+0100"],"processingStartTimeRange":["2022-03-24T19:44:16.034+0100","2022-03-24T19:44:30.503+0100"],"processingEndTimeRange":["2022-03-24T19:44:16.195+0100","2022-03-24T19:44:30.348+0100"]},{"batchID":"testbatch","tasks":{"scheduled":"400","in-process":"3","completed":"305"},"dicomDeviceName":["devj4c"],"LocalAET":["DEVJ4C"],"createdTimeRange":["2022-03-24T19:43:16.534+0100","2022-03-24T19:43:18.785+0100"],"updatedTimeRange":["2022-03-24T19:43:16.534+0100","2022-03-24T19:44:51.821+0100"],"scheduledTimeRange":["2022-03-24T19:43:16.513+0100","2022-03-24T19:43:16.513+0100"],"processingStartTimeRange":["2022-03-24T19:44:16.034+0100","2022-03-24T19:44:51.634+0100"],"processingEndTimeRange":["2022-03-24T19:44:16.195+0100","2022-03-24T19:44:51.821+0100"]},{"batchID":"testbatch","tasks":{"scheduled":"151","in-process":"4","completed":"553"},"dicomDeviceName":["devj4c"],"LocalAET":["DEVJ4C"],"createdTimeRange":["2022-03-24T19:43:16.534+0100","2022-03-24T19:43:18.785+0100"],"updatedTimeRange":["2022-03-24T19:43:16.534+0100","2022-03-24T19:45:08.561+0100"],"scheduledTimeRange":["2022-03-24T19:43:16.513+0100","2022-03-24T19:43:16.513+0100"],"processingStartTimeRange":["2022-03-24T19:44:16.034+0100","2022-03-24T19:45:08.561+0100"],"processingEndTimeRange":["2022-03-24T19:44:16.195+0100","2022-03-24T19:45:08.561+0100"]},{"batchID":"testbatch","tasks":{"scheduled":"11","in-process":"5","completed":"692"},"dicomDeviceName":["devj4c"],"LocalAET":["DEVJ4C"],"createdTimeRange":["2022-03-24T19:43:16.534+0100","2022-03-24T19:43:18.785+0100"],"updatedTimeRange":["2022-03-24T19:43:17.517+0100","2022-03-24T19:45:26.909+0100"],"scheduledTimeRange":["2022-03-24T19:43:16.513+0100","2022-03-24T19:43:16.513+0100"],"processingStartTimeRange":["2022-03-24T19:44:16.034+0100","2022-03-24T19:45:26.636+0100"],"processingEndTimeRange":["2022-03-24T19:44:16.195+0100","2022-03-24T19:45:26.582+0100"]},{"batchID":"testbatch","tasks":{"completed":"708"},"dicomDeviceName":["devj4c"],"LocalAET":["DEVJ4C"],"createdTimeRange":["2022-03-24T19:43:16.534+0100","2022-03-24T19:43:18.785+0100"],"updatedTimeRange":["2022-03-24T19:44:16.195+0100","2022-03-24T19:45:28.264+0100"],"scheduledTimeRange":["2022-03-24T19:43:16.513+0100","2022-03-24T19:43:16.513+0100"],"processingStartTimeRange":["2022-03-24T19:44:16.034+0100","2022-03-24T19:45:27.754+0100"],"processingEndTimeRange":["2022-03-24T19:44:16.195+0100","2022-03-24T19:45:28.264+0100"]}];
                    }else{
                        res = [{"taskID":"3445","dicomDeviceName":"devj4c","queue":"StgVerTasks","type":"STGVER","status":"COMPLETED","createdTime":"2022-03-17T00:19:44.155+0100","updatedTime":"2022-03-17T00:20:37.643+0100","scheduledTime":"2022-03-17T00:19:44.153+0100","processingStartTime":"2022-03-17T00:20:37.582+0100","processingEndTime":"2022-03-17T00:20:37.643+0100","outcomeMessage":"Commit Storage of Series[uid=2.25.226771377810260383099403728946437958202] of Study[uid=2.25.187439577464559836490772088259615969176] for OBJECT_CHECKSUM: - completed: 0, failed: 0","LocalAET":"DEVJ4C","StudyInstanceUID":"2.25.187439577464559836490772088259615969176","SeriesInstanceUID":"2.25.226771377810260383099403728946437958202"},{"taskID":"3444","dicomDeviceName":"devj4c","queue":"StgVerTasks","type":"STGVER","status":"COMPLETED","createdTime":"2022-03-17T00:18:44.058+0100","updatedTime":"2022-03-17T00:19:37.676+0100","scheduledTime":"2022-03-17T00:18:44.056+0100","processingStartTime":"2022-03-17T00:19:37.622+0100","processingEndTime":"2022-03-17T00:19:37.676+0100","outcomeMessage":"Commit Storage of Series[uid=2.25.249319711441696008504058463592875912795] of Study[uid=2.25.283304145802740676259414951321571976120] for OBJECT_CHECKSUM: - completed: 1, failed: 0","LocalAET":"DEVJ4C","StudyInstanceUID":"2.25.283304145802740676259414951321571976120","SeriesInstanceUID":"2.25.249319711441696008504058463592875912795","completed":"1"},{"taskID":"3443","dicomDeviceName":"devj4c","queue":"StgVerTasks","type":"STGVER","status":"COMPLETED","createdTime":"2022-03-17T00:18:44.033+0100","updatedTime":"2022-03-17T00:19:37.667+0100","scheduledTime":"2022-03-17T00:18:44.031+0100","processingStartTime":"2022-03-17T00:19:37.581+0100","processingEndTime":"2022-03-17T00:19:37.667+0100","outcomeMessage":"Commit Storage of Series[uid=2.25.2133667457369353508175387852445334811] of Study[uid=2.25.187439577464559836490772088259615969176] for OBJECT_CHECKSUM: - completed: 1, failed: 0","LocalAET":"DEVJ4C","StudyInstanceUID":"2.25.187439577464559836490772088259615969176","SeriesInstanceUID":"2.25.2133667457369353508175387852445334811","completed":"1"},{"taskID":"3442","dicomDeviceName":"devj4c","queue":"StgVerTasks","type":"STGVER","status":"COMPLETED","createdTime":"2022-03-17T00:17:43.978+0100","updatedTime":"2022-03-17T00:18:37.741+0100","scheduledTime":"2022-03-17T00:17:43.977+0100","processingStartTime":"2022-03-17T00:18:37.612+0100","processingEndTime":"2022-03-17T00:18:37.741+0100","outcomeMessage":"Commit Storage of Series[uid=1.3.6.1.4.1.37873.1.20.289556855539230578455393093755395983231] of Study[uid=2.25.283304145802740676259414951321571976120] for OBJECT_CHECKSUM: - completed: 2, failed: 0","LocalAET":"DEVJ4C","StudyInstanceUID":"2.25.283304145802740676259414951321571976120","SeriesInstanceUID":"1.3.6.1.4.1.37873.1.20.289556855539230578455393093755395983231","completed":"2"},{"taskID":"3441","dicomDeviceName":"devj4c","queue":"StgVerTasks","type":"STGVER","status":"COMPLETED","createdTime":"2022-03-17T00:16:43.925+0100","updatedTime":"2022-03-17T00:17:37.672+0100","scheduledTime":"2022-03-17T00:16:43.923+0100","processingStartTime":"2022-03-17T00:17:37.608+0100","processingEndTime":"2022-03-17T00:17:37.672+0100","outcomeMessage":"Commit Storage of Series[uid=1.3.6.1.4.1.37873.1.20.247536570020359359774575642363475035667] of Study[uid=2.25.187439577464559836490772088259615969176] for OBJECT_CHECKSUM: - completed: 1, failed: 0","LocalAET":"DEVJ4C","StudyInstanceUID":"2.25.187439577464559836490772088259615969176","SeriesInstanceUID":"1.3.6.1.4.1.37873.1.20.247536570020359359774575642363475035667","completed":"1"},{"taskID":"3437","dicomDeviceName":"devj4c","queue":"StgVerTasks","type":"STGVER","status":"COMPLETED","createdTime":"2022-03-17T00:07:43.795+0100","updatedTime":"2022-03-17T00:08:38.627+0100","scheduledTime":"2022-03-17T00:07:43.794+0100","processingStartTime":"2022-03-17T00:08:37.701+0100","processingEndTime":"2022-03-17T00:08:38.627+0100","outcomeMessage":"Commit Storage of Series[uid=2.25.273526272782556061765155428536698740654] of Study[uid=2.25.4104151166269820755491054129106833506] for OBJECT_CHECKSUM: - completed: 13, failed: 0","LocalAET":"DEVJ4C","StudyInstanceUID":"2.25.4104151166269820755491054129106833506","SeriesInstanceUID":"2.25.273526272782556061765155428536698740654","completed":"13"},{"taskID":"3440","dicomDeviceName":"devj4c","queue":"StgVerTasks","type":"STGVER","status":"COMPLETED","createdTime":"2022-03-17T00:07:43.845+0100","updatedTime":"2022-03-17T00:08:38.086+0100","scheduledTime":"2022-03-17T00:07:43.844+0100","processingStartTime":"2022-03-17T00:08:37.902+0100","processingEndTime":"2022-03-17T00:08:38.085+0100","outcomeMessage":"Commit Storage of Series[uid=2.25.251585774989980495668716070036214323625] of Study[uid=2.25.4104151166269820755491054129106833506] for OBJECT_CHECKSUM: - completed: 1, failed: 0","LocalAET":"DEVJ4C","StudyInstanceUID":"2.25.4104151166269820755491054129106833506","SeriesInstanceUID":"2.25.251585774989980495668716070036214323625","completed":"1"},{"taskID":"3439","dicomDeviceName":"devj4c","queue":"StgVerTasks","type":"STGVER","status":"COMPLETED","createdTime":"2022-03-17T00:07:43.829+0100","updatedTime":"2022-03-17T00:08:37.976+0100","scheduledTime":"2022-03-17T00:07:43.828+0100","processingStartTime":"2022-03-17T00:08:37.886+0100","processingEndTime":"2022-03-17T00:08:37.976+0100","outcomeMessage":"Commit Storage of Series[uid=2.25.238791581884223303700761357580968487329] of Study[uid=2.25.4104151166269820755491054129106833506] for OBJECT_CHECKSUM: - completed: 1, failed: 0","LocalAET":"DEVJ4C","StudyInstanceUID":"2.25.4104151166269820755491054129106833506","SeriesInstanceUID":"2.25.238791581884223303700761357580968487329","completed":"1"},{"taskID":"3438","dicomDeviceName":"devj4c","queue":"StgVerTasks","type":"STGVER","status":"COMPLETED","createdTime":"2022-03-17T00:07:43.812+0100","updatedTime":"2022-03-17T00:08:37.968+0100","scheduledTime":"2022-03-17T00:07:43.811+0100","processingStartTime":"2022-03-17T00:08:37.809+0100","processingEndTime":"2022-03-17T00:08:37.968+0100","outcomeMessage":"Commit Storage of Series[uid=2.25.209893573848189194515701669039602952980] of Study[uid=2.25.4104151166269820755491054129106833506] for OBJECT_CHECKSUM: - completed: 1, failed: 0","LocalAET":"DEVJ4C","StudyInstanceUID":"2.25.4104151166269820755491054129106833506","SeriesInstanceUID":"2.25.209893573848189194515701669039602952980","completed":"1"},{"taskID":"3436","dicomDeviceName":"devj4c","queue":"StgVerTasks","type":"STGVER","status":"COMPLETED","createdTime":"2022-03-17T00:07:43.778+0100","updatedTime":"2022-03-17T00:08:37.944+0100","scheduledTime":"2022-03-17T00:07:43.777+0100","processingStartTime":"2022-03-17T00:08:37.666+0100","processingEndTime":"2022-03-17T00:08:37.944+0100","outcomeMessage":"Commit Storage of Series[uid=2.25.327325684233608589739237606483587820137] of Study[uid=2.25.4104151166269820755491054129106833506] for OBJECT_CHECKSUM: - completed: 5, failed: 0","LocalAET":"DEVJ4C","StudyInstanceUID":"2.25.4104151166269820755491054129106833506","SeriesInstanceUID":"2.25.327325684233608589739237606483587820137","completed":"5"},{"taskID":"3435","dicomDeviceName":"devj4c","queue":"StgVerTasks","type":"STGVER","status":"COMPLETED","createdTime":"2022-03-17T00:07:43.761+0100","updatedTime":"2022-03-17T00:08:37.870+0100","scheduledTime":"2022-03-17T00:07:43.760+0100","processingStartTime":"2022-03-17T00:08:37.596+0100","processingEndTime":"2022-03-17T00:08:37.869+0100","outcomeMessage":"Commit Storage of Series[uid=2.25.141591256152800607165594020100725939657] of Study[uid=2.25.4104151166269820755491054129106833506] for OBJECT_CHECKSUM: - completed: 1, failed: 0","LocalAET":"DEVJ4C","StudyInstanceUID":"2.25.4104151166269820755491054129106833506","SeriesInstanceUID":"2.25.141591256152800607165594020100725939657","completed":"1"},{"taskID":"3434","dicomDeviceName":"devj4c","queue":"StgVerTasks","type":"STGVER","status":"COMPLETED","createdTime":"2022-03-16T23:49:43.545+0100","updatedTime":"2022-03-16T23:50:37.762+0100","scheduledTime":"2022-03-16T23:49:43.543+0100","processingStartTime":"2022-03-16T23:50:37.703+0100","processingEndTime":"2022-03-16T23:50:37.762+0100","outcomeMessage":"Commit Storage of Series[uid=2.25.67588952459076364445128671815214566778] of Study[uid=2.25.121555613334906896240260346728609314963] for OBJECT_CHECKSUM: - completed: 20, failed: 0","LocalAET":"DEVJ4C","StudyInstanceUID":"2.25.121555613334906896240260346728609314963","SeriesInstanceUID":"2.25.67588952459076364445128671815214566778","completed":"20"},{"taskID":"3433","dicomDeviceName":"devj4c","queue":"StgVerTasks","type":"STGVER","status":"COMPLETED","createdTime":"2022-03-16T23:49:43.502+0100","updatedTime":"2022-03-16T23:50:37.754+0100","scheduledTime":"2022-03-16T23:49:43.501+0100","processingStartTime":"2022-03-16T23:50:37.687+0100","processingEndTime":"2022-03-16T23:50:37.754+0100","outcomeMessage":"Commit Storage of Series[uid=2.25.102902093042500596256409312998451421469] of Study[uid=2.25.121555613334906896240260346728609314963] for OBJECT_CHECKSUM: - completed: 20, failed: 0","LocalAET":"DEVJ4C","StudyInstanceUID":"2.25.121555613334906896240260346728609314963","SeriesInstanceUID":"2.25.102902093042500596256409312998451421469","completed":"20"},{"taskID":"3432","dicomDeviceName":"devj4c","queue":"StgVerTasks","type":"STGVER","status":"COMPLETED","createdTime":"2022-03-16T23:49:43.478+0100","updatedTime":"2022-03-16T23:50:37.729+0100","scheduledTime":"2022-03-16T23:49:43.476+0100","processingStartTime":"2022-03-16T23:50:37.670+0100","processingEndTime":"2022-03-16T23:50:37.728+0100","outcomeMessage":"Commit Storage of Series[uid=2.25.310794295704156880311613765816328504351] of Study[uid=2.25.121555613334906896240260346728609314963] for OBJECT_CHECKSUM: - completed: 15, failed: 0","LocalAET":"DEVJ4C","StudyInstanceUID":"2.25.121555613334906896240260346728609314963","SeriesInstanceUID":"2.25.310794295704156880311613765816328504351","completed":"15"},{"taskID":"3431","dicomDeviceName":"devj4c","queue":"StgVerTasks","type":"STGVER","status":"COMPLETED","createdTime":"2022-03-16T23:49:43.453+0100","updatedTime":"2022-03-16T23:50:37.720+0100","scheduledTime":"2022-03-16T23:49:43.451+0100","processingStartTime":"2022-03-16T23:50:37.654+0100","processingEndTime":"2022-03-16T23:50:37.720+0100","outcomeMessage":"Commit Storage of Series[uid=2.25.25108260755645824206528083729590597481] of Study[uid=2.25.121555613334906896240260346728609314963] for OBJECT_CHECKSUM: - completed: 15, failed: 0","LocalAET":"DEVJ4C","StudyInstanceUID":"2.25.121555613334906896240260346728609314963","SeriesInstanceUID":"2.25.25108260755645824206528083729590597481","completed":"15"},{"taskID":"3430","dicomDeviceName":"devj4c","queue":"StgVerTasks","type":"STGVER","status":"COMPLETED","createdTime":"2022-03-16T23:49:43.428+0100","updatedTime":"2022-03-16T23:50:37.678+0100","scheduledTime":"2022-03-16T23:49:43.426+0100","processingStartTime":"2022-03-16T23:50:37.611+0100","processingEndTime":"2022-03-16T23:50:37.678+0100","outcomeMessage":"Commit Storage of Series[uid=2.25.75312149585436102311774719227652836561] of Study[uid=2.25.121555613334906896240260346728609314963] for OBJECT_CHECKSUM: - completed: 1, failed: 0","LocalAET":"DEVJ4C","StudyInstanceUID":"2.25.121555613334906896240260346728609314963","SeriesInstanceUID":"2.25.75312149585436102311774719227652836561","completed":"1"},{"taskID":"3429","dicomDeviceName":"devj4c","queue":"StgVerTasks","type":"STGVER","status":"COMPLETED","createdTime":"2022-03-16T23:48:43.357+0100","updatedTime":"2022-03-16T23:49:37.685+0100","scheduledTime":"2022-03-16T23:48:43.355+0100","processingStartTime":"2022-03-16T23:49:37.612+0100","processingEndTime":"2022-03-16T23:49:37.684+0100","outcomeMessage":"Commit Storage of Series[uid=2.25.181896536013993857549222326511302072795] of Study[uid=2.25.239412065351115581560076770912136009244] for OBJECT_CHECKSUM: - completed: 4, failed: 0","LocalAET":"DEVJ4C","StudyInstanceUID":"2.25.239412065351115581560076770912136009244","SeriesInstanceUID":"2.25.181896536013993857549222326511302072795","completed":"4"},{"taskID":"3428","dicomDeviceName":"devj4c","queue":"StgVerTasks","type":"STGVER","status":"COMPLETED","createdTime":"2022-03-16T23:46:43.296+0100","updatedTime":"2022-03-16T23:47:37.679+0100","scheduledTime":"2022-03-16T23:46:43.294+0100","processingStartTime":"2022-03-16T23:47:37.610+0100","processingEndTime":"2022-03-16T23:47:37.679+0100","outcomeMessage":"Commit Storage of Series[uid=2.25.69012359711510693145798679449337647363] of Study[uid=2.25.113140863059576012295811363051304563895] for OBJECT_CHECKSUM: - completed: 0, failed: 0","LocalAET":"DEVJ4C","StudyInstanceUID":"2.25.113140863059576012295811363051304563895","SeriesInstanceUID":"2.25.69012359711510693145798679449337647363"},{"taskID":"3427","dicomDeviceName":"devj4c","queue":"StgVerTasks","type":"STGVER","status":"COMPLETED","createdTime":"2022-03-16T23:43:43.231+0100","updatedTime":"2022-03-16T23:44:37.672+0100","scheduledTime":"2022-03-16T23:43:43.229+0100","processingStartTime":"2022-03-16T23:44:37.606+0100","processingEndTime":"2022-03-16T23:44:37.672+0100","outcomeMessage":"Commit Storage of Series[uid=2.25.238065245568528336746839137845588685441] of Study[uid=2.25.113140863059576012295811363051304563895] for OBJECT_CHECKSUM: - completed: 0, failed: 0","LocalAET":"DEVJ4C","StudyInstanceUID":"2.25.113140863059576012295811363051304563895","SeriesInstanceUID":"2.25.238065245568528336746839137845588685441"},{"taskID":"3426","dicomDeviceName":"devj4c","queue":"StgVerTasks","type":"STGVER","status":"COMPLETED","createdTime":"2022-03-16T23:40:43.174+0100","updatedTime":"2022-03-16T23:41:37.679+0100","scheduledTime":"2022-03-16T23:40:43.173+0100","processingStartTime":"2022-03-16T23:41:37.655+0100","processingEndTime":"2022-03-16T23:41:37.679+0100","outcomeMessage":"Commit Storage of Series[uid=2.25.247978201123894924858372352311582041240] of Study[uid=2.25.113140863059576012295811363051304563895] for OBJECT_CHECKSUM: - completed: 0, failed: 0","LocalAET":"DEVJ4C","StudyInstanceUID":"2.25.113140863059576012295811363051304563895","SeriesInstanceUID":"2.25.247978201123894924858372352311582041240"},{"taskID":"3425","dicomDeviceName":"devj4c","queue":"StgVerTasks","type":"STGVER","status":"COMPLETED","createdTime":"2022-03-16T23:40:43.150+0100","updatedTime":"2022-03-16T23:41:37.671+0100","scheduledTime":"2022-03-16T23:40:43.148+0100","processingStartTime":"2022-03-16T23:41:37.613+0100","processingEndTime":"2022-03-16T23:41:37.671+0100","outcomeMessage":"Commit Storage of Series[uid=2.25.89503268592787656317516862088811784009] of Study[uid=2.25.113140863059576012295811363051304563895] for OBJECT_CHECKSUM: - completed: 0, failed: 0","LocalAET":"DEVJ4C","StudyInstanceUID":"2.25.113140863059576012295811363051304563895","SeriesInstanceUID":"2.25.89503268592787656317516862088811784009"}];
                    }
                }
                if (res && res.length > 0){
                    this.storageVerifications =  res;
                    $this.count = undefined;
                    if(this.batchGrouped){
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
                    }
                }else{
                    $this.cfpLoadingBar.complete();
                    $this.storageVerifications = [];
                    this.mainservice.showMsg($localize `:@@no_tasks_found:No tasks found!`)
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
        this.setTableSchemas();
/*        this.allActionsActive = this.allActionsOptions.filter((o)=>{
            if(filters.status == "SCHEDULED" || filters.status == $localize `:@@storage-verification.in_process:IN PROCESS`){
                return o.value != 'reschedule';
            }else{
                if(filters.status === '*' || !filters.status || filters.status === '')
                    return o.value != 'cancel' && o.value != 'reschedule';
                else
                    return o.value != 'cancel';
            }
        });*/
    }
    deleteAllTasks(filter){
        this.service.deleteAll(filter).subscribe((res)=>{
            this.mainservice.showMsg($localize `:@@tasks_deleted_param:${res.deleted}:tasks: tasks deleted successfully!`)
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
        if(mode && match && match.taskID){
            this.confirm({
                content: $localize `:@@action_selected_entries_question:Are you sure you want to ${Globalvar.getActionText(mode)} selected entries?`
            }).subscribe(ok => {
                if (ok){
                    switch (mode) {
                        case 'reschedule':
                            this.deviceService.selectParameters((res)=>{
                                    if(res){
                                        this.cfpLoadingBar.start();
                                        let filter = {}
                                        if(_.hasIn(res, "schema_model.newDeviceName") && res.schema_model.newDeviceName != ""){
                                            filter["newDeviceName"] = res.schema_model.newDeviceName;
                                        }
                                        if(_.hasIn(res, "schema_model.scheduledTime") && res.schema_model.scheduledTime != ""){
                                            filter["scheduledTime"] = res.schema_model.scheduledTime;
                                        }
                                        this.service.reschedule(match.taskID, filter)
                                            .subscribe(
                                                (res) => {
                                                    this.getTasks(this.filterObject['offset'] || 0);
                                                    this.cfpLoadingBar.complete();
                                                    this.mainservice.showMsg($localize `:@@task_rescheduled:Task rescheduled successfully!`)
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
                                        this.getTasks(this.filterObject['offset'] || 0);
                                        this.mainservice.showMsg($localize `:@@task_deleted:Task deleted successfully!`)
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
                                        match.status = 'CANCELED';
                                        this.cfpLoadingBar.complete();
                                        this.mainservice.showMsg($localize `:@@task_canceled:Task canceled successfully!`)
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
