import {User} from '../../models/user';
import {ConfirmComponent} from '../../widgets/dialogs/confirm/confirm.component';
// import { MatLegacyDialogConfig as MatDialogConfig, MatLegacyDialog as MatDialog, MatLegacyDialogRef as MatDialogRef } from '@angular/material/legacy-dialog';
import {MatDialog, MatDialogRef} from "@angular/material/dialog";
import * as _ from 'lodash-es';
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
import {FormsModule, Validators} from '@angular/forms';
import {KeycloakService} from "../../helpers/keycloak-service/keycloak.service";
import {map} from "rxjs/operators";
import {SelectDropdown} from "../../interfaces";
import {environment} from "../../../environments/environment";
import {Component, OnDestroy, OnInit, ViewContainerRef} from "@angular/core";
import {MonitoringTabsComponent} from '../monitoring-tabs.component';
import {FilterGeneratorComponent} from '../../helpers/filter-generator/filter-generator.component';
import {CommonModule, NgClass} from '@angular/common';
import {PermissionDirective} from '../../helpers/permissions/permission.directive';
import {MatProgressSpinner} from '@angular/material/progress-spinner';
import {MatOption, MatSelect} from '@angular/material/select';
import {TableGeneratorComponent} from '../../helpers/table-generator/table-generator.component';


@Component({
    selector: 'app-export',
    templateUrl: './export.component.html',
    imports: [
        MonitoringTabsComponent,
        FilterGeneratorComponent,
        NgClass,
        FormsModule,
        MatProgressSpinner,
        PermissionDirective,
        MatSelect,
        MatOption,
        TableGeneratorComponent,
        CommonModule
    ],
    standalone: true
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
        startText:$localize `:@@start_auto_refresh:Start Auto Refresh`,
        stopText:$localize `:@@stop_auto_refresh:Stop Auto Refresh`
    };
    statusValues = {};
    refreshInterval;
    externalRetrieveEntries;
    interval = 10;
    Object = Object;
    batchGrouped = false;
    dialogRef: MatDialogRef<any>;
    _ = _;
    devices;
    count;
    allAction;
    filterLoadFinished = false;
    allActionsOptions = [
        {
            value:"cancel",
            label:$localize `:@@cancel_all_matching_tasks:Cancel all matching tasks`
        },{
            value:"reschedule",
            label:$localize `:@@reschedule_all_matching_tasks:Reschedule all matching tasks`
        },{
            value:"delete",
            label:$localize `:@@edelete_all_matching_tasks:Delete all matching tasks`
        }
    ];
    allActionsActive = [];
    tableHovered = false;
    filterSchema;
    filterObject:any = {
        limit:20,
        offset:0
    };
    urlParam;
    constructor(
        public $http:J4careHttpService,
        public cfpLoadingBar: LoadingBarService,
        public mainservice: AppService,
        public  service: ExportService,
        public viewContainerRef: ViewContainerRef,
        public dialog: MatDialog,
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
    tableConfigNormal:any = {
        search:""
    };
    tableConfigGrouped:any = {
        search:""
    };
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
        this.service.statusValues().forEach(val =>{
            this.statusValues[val.value] = {
                count: 0,
                loader: false,
                text:val.text
            };
        });
        this.statusChange();


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
            case "task-detail":
                this.showTaskDetail(match);
                break;
            case "delete-batched":
                this.deleteBatchedTask(match);
                break;
        }

    }
    initSchema(){
        this.setFilterSchema();
        if(this.urlParam){
            this.filterObject = this.urlParam;
            this.filterObject["limit"] = 20;
            this.filterObject["offset"] = 0;
            this.filterObject["orderby"] = '-updatedTime';
        }
        console.log("this.filterObject",this.filterObject);
        this.setTableSchemas();
    }
    setTableSchemas(){
        this.tableConfigNormal = {
            table:j4care.calculateWidthOfTable(this.service.getTableSchema(this, this.action, {grouped:false, getDifferenceTime:this.getDifferenceTime, filterObject:this.filterObject})),
            table_grouped:j4care.calculateWidthOfTable(this.service.getTableSchema(this, this.action, {grouped:true, getDifferenceTime:this.getDifferenceTime, filterObject:this.filterObject})),
            filter:this.filterObject,
            search:"",
            showAttributes:false,
            calculate:false
        };
        this.tableConfigGrouped = {
            table:j4care.calculateWidthOfTable(this.service.getTableSchema(this, this.action, {grouped:true, getDifferenceTime:this.getDifferenceTime, filterObject:this.filterObject})),
            filter:this.filterObject,
            search:"",
            showAttributes:false,
            calculate:false
        };
    }
    setFilterSchema(){
        this.filterSchema = this.service.getFilterSchema(this.exporters, this.devices,$localize `:@@count_param:COUNT ${((this.count || this.count == 0)?this.count:'')}:count:`);
    }
    onFormChange(e){
        console.log("e",e);
        this.statusChange();
        this.setTableSchemas();
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
    confirm(confirmparameters,width?:string){
        //this.config.viewContainerRef = this.viewContainerRef;
        width = width || '465px';
        this.dialogRef = this.dialog.open(ConfirmComponent,{
            height: 'auto',
            width: width
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
    getCounts(offset?){
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
                if(!this.mainservice.global.notSecure)
                    j4care.downloadFile(`${j4care.addLastSlash(this.mainservice.baseUrl)}monitor/export?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&access_token=${token}&${this.mainservice.param(filterClone)}`,"export.csv")
                else
                    j4care.downloadFile(`${j4care.addLastSlash(this.mainservice.baseUrl)}monitor/export?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&${this.mainservice.param(filterClone)}`,"export.csv")
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
                    description:$localize `:@@use_semicolon_as_delimiter:Use semicolon as delimiter`
                },
                {
                    tag:"range-picker-time",
                    type:"text",
                    filterKey:"scheduledTime",
                    description:$localize `:@@schedule_at_desc:Schedule at (if not set, schedule immediately)`
                },
                //scheduledTime
                {
                    tag:"select",
                    options:this.aets,
                    showStar:true,
                    filterKey:"LocalAET",
                    description:$localize `:@@local_aet:Local AET`,
                    placeholder:$localize `:@@local_aet:Local AET`,
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
                    description:$localize `:@@exporter_id:Exporter ID`,
                    placeholder:$localize `:@@exporter_id:Exporter ID`,
                    validation:Validators.required
                },
                {
                    tag:"input",
                    type:"number",
                    filterKey:"studyUIDField",
                    description:$localize `:@@study_uid_field:Study UID Field`,
                    placeholder:$localize `:@@study_uid_field:Study UID Field`,
                    validation:[Validators.minLength(1),Validators.min(1)],
                    defaultValue:1
                },
                {
                    tag:"input",
                    type:"number",
                    filterKey:"seriesUIDField",
                    description:$localize `:@@series_uid_field:Series UID Field`,
                    placeholder:$localize `:@@series_uid_field:Series UID Field`,
                    validation:[Validators.minLength(1),Validators.min(1)],
                    defaultValue:null
                },
                {
                    tag:"input",
                    type:"text",
                    filterKey:"batchID",
                    description:$localize `:@@batch_id:Batch ID`,
                    placeholder:$localize `:@@batch_id:Batch ID`
                }
            ],
            prepareUrl:(filter)=>{
                let clonedFilters = {};
                if (filter['batchID'])
                    clonedFilters['batchID'] = filter['batchID'];
                if (filter['scheduledTime'])
                    clonedFilters['scheduledTime'] = filter['scheduledTime'];
                return filter['seriesUIDField']
                    ? `${j4care.addLastSlash(this.mainservice.baseUrl)}aets/${filter.LocalAET}/rs/studies/csv:${filter.studyUIDField}/series/csv:${filter.seriesUIDField}/export/${filter.exporterID}${j4care.getUrlParams(clonedFilters)}`
                    : `${j4care.addLastSlash(this.mainservice.baseUrl)}aets/${filter.LocalAET}/rs/studies/csv:${filter.studyUIDField}/export/${filter.exporterID}${j4care.getUrlParams(clonedFilters)}`;
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
        this.filterObject.batchID = task.batchID;
        this.batchGrouped = false;
        this.search(0);
    }
    search(offset) {
        let $this = this;
        $this.cfpLoadingBar.start();
        this.service.search(this.filterObject, offset,this.batchGrouped)

            .subscribe((res) => {
                if(!environment.production){
                    if(this.batchGrouped){
                        res = [
                            {
                                "batchID": "ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]",
                                "tasks": {
                                    "failed": "32"
                                },
                                "dicomDeviceName": [
                                    "demoj4c"
                                ],
                                "ExporterID": [
                                    "ATS Prefetch NG - QIDO Study Instances",
                                    "ATS Prefetch NG - QIDO Study Series",
                                    "ATS Prefetch NG - Series Thumbnails",
                                    "ATS Prefetch NG - Series WADO Metadata"
                                ],
                                "createdTimeRange": [
                                    "2022-03-15T01:20:51.548+0100",
                                    "2022-03-15T01:20:51.929+0100"
                                ],
                                "updatedTimeRange": [
                                    "2022-03-15T19:39:00.706+0100",
                                    "2022-03-15T19:41:30.097+0100"
                                ],
                                "scheduledTimeRange": [
                                    "2022-03-15T19:38:00.692+0100",
                                    "2022-03-15T19:41:00.721+0100"
                                ],
                                "processingStartTimeRange": [
                                    "2022-03-15T19:39:00.584+0100",
                                    "2022-03-15T19:41:05.308+0100"
                                ],
                                "processingEndTimeRange": [
                                    "2022-03-15T19:39:00.703+0100",
                                    "2022-03-15T19:41:29.900+0100"
                                ]
                            },
                            {
                                "batchID": "ATS Prefetch Priors on Store[1.2.276.0.37.1.406.201712.240110]",
                                "tasks": {
                                    "warning": "2"
                                },
                                "dicomDeviceName": [
                                    "demoj4c"
                                ],
                                "ExporterID": [
                                    "ATS Prefetch NG - Series Thumbnails",
                                    "ATS Prefetch NG - Series WADO Metadata"
                                ],
                                "createdTimeRange": [
                                    "2022-02-08T18:45:29.656+0100",
                                    "2022-02-08T18:45:29.664+0100"
                                ],
                                "updatedTimeRange": [
                                    "2022-02-08T18:45:49.821+0100",
                                    "2022-02-08T18:45:50.111+0100"
                                ],
                                "scheduledTimeRange": [
                                    "2022-02-08T18:45:29.489+0100",
                                    "2022-02-08T18:45:29.489+0100"
                                ],
                                "processingStartTimeRange": [
                                    "2022-02-08T18:45:48.149+0100",
                                    "2022-02-08T18:45:48.585+0100"
                                ],
                                "processingEndTimeRange": [
                                    "2022-02-08T18:45:49.821+0100",
                                    "2022-02-08T18:45:50.110+0100"
                                ]
                            },
                            {
                                "batchID": "recreate-tasks-5.24.iuids",
                                "tasks": {
                                    "warning": "9"
                                },
                                "dicomDeviceName": [
                                    "demoj4c"
                                ],
                                "ExporterID": [
                                    "Export Objects to STORESCP"
                                ],
                                "createdTimeRange": [
                                    "2021-09-24T16:17:16.929+0200",
                                    "2021-09-24T16:17:17.092+0200"
                                ],
                                "updatedTimeRange": [
                                    "2021-09-24T16:27:46.787+0200",
                                    "2021-09-24T16:27:46.884+0200"
                                ],
                                "scheduledTimeRange": [
                                    "2021-09-24T16:27:46.633+0200",
                                    "2021-09-24T16:27:46.633+0200"
                                ],
                                "processingStartTimeRange": [
                                    "2021-09-24T16:27:46.765+0200",
                                    "2021-09-24T16:27:46.864+0200"
                                ],
                                "processingEndTimeRange": [
                                    "2021-09-24T16:27:46.787+0200",
                                    "2021-09-24T16:27:46.884+0200"
                                ]
                            },
                            {
                                "batchID": "ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]",
                                "tasks": {
                                    "failed": "32"
                                },
                                "dicomDeviceName": [
                                    "demoj4c"
                                ],
                                "ExporterID": [
                                    "ATS Prefetch NG - QIDO Study Instances",
                                    "ATS Prefetch NG - QIDO Study Series",
                                    "ATS Prefetch NG - Series Thumbnails",
                                    "ATS Prefetch NG - Series WADO Metadata"
                                ],
                                "createdTimeRange": [
                                    "2022-03-15T01:20:51.548+0100",
                                    "2022-03-15T01:20:51.929+0100"
                                ],
                                "updatedTimeRange": [
                                    "2022-03-15T19:39:00.706+0100",
                                    "2022-03-15T19:41:30.097+0100"
                                ],
                                "scheduledTimeRange": [
                                    "2022-03-15T19:38:00.692+0100",
                                    "2022-03-15T19:41:00.721+0100"
                                ],
                                "processingStartTimeRange": [
                                    "2022-03-15T19:39:00.584+0100",
                                    "2022-03-15T19:41:05.308+0100"
                                ],
                                "processingEndTimeRange": [
                                    "2022-03-15T19:39:00.703+0100",
                                    "2022-03-15T19:41:29.900+0100"
                                ]
                            },
                            {
                                "batchID": "ATS Prefetch Priors on Store[1.2.276.0.37.1.406.201712.240110]",
                                "tasks": {
                                    "warning": "2"
                                },
                                "dicomDeviceName": [
                                    "demoj4c"
                                ],
                                "ExporterID": [
                                    "ATS Prefetch NG - Series Thumbnails",
                                    "ATS Prefetch NG - Series WADO Metadata"
                                ],
                                "createdTimeRange": [
                                    "2022-02-08T18:45:29.656+0100",
                                    "2022-02-08T18:45:29.664+0100"
                                ],
                                "updatedTimeRange": [
                                    "2022-02-08T18:45:49.821+0100",
                                    "2022-02-08T18:45:50.111+0100"
                                ],
                                "scheduledTimeRange": [
                                    "2022-02-08T18:45:29.489+0100",
                                    "2022-02-08T18:45:29.489+0100"
                                ],
                                "processingStartTimeRange": [
                                    "2022-02-08T18:45:48.149+0100",
                                    "2022-02-08T18:45:48.585+0100"
                                ],
                                "processingEndTimeRange": [
                                    "2022-02-08T18:45:49.821+0100",
                                    "2022-02-08T18:45:50.110+0100"
                                ]
                            },
                            {
                                "batchID": "recreate-tasks-5.24.iuids",
                                "tasks": {
                                    "warning": "9"
                                },
                                "dicomDeviceName": [
                                    "demoj4c"
                                ],
                                "ExporterID": [
                                    "Export Objects to STORESCP"
                                ],
                                "createdTimeRange": [
                                    "2021-09-24T16:17:16.929+0200",
                                    "2021-09-24T16:17:17.092+0200"
                                ],
                                "updatedTimeRange": [
                                    "2021-09-24T16:27:46.787+0200",
                                    "2021-09-24T16:27:46.884+0200"
                                ],
                                "scheduledTimeRange": [
                                    "2021-09-24T16:27:46.633+0200",
                                    "2021-09-24T16:27:46.633+0200"
                                ],
                                "processingStartTimeRange": [
                                    "2021-09-24T16:27:46.765+0200",
                                    "2021-09-24T16:27:46.864+0200"
                                ],
                                "processingEndTimeRange": [
                                    "2021-09-24T16:27:46.787+0200",
                                    "2021-09-24T16:27:46.884+0200"
                                ]
                            },
                            {
                                "batchID": "ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]",
                                "tasks": {
                                    "failed": "32"
                                },
                                "dicomDeviceName": [
                                    "demoj4c"
                                ],
                                "ExporterID": [
                                    "ATS Prefetch NG - QIDO Study Instances",
                                    "ATS Prefetch NG - QIDO Study Series",
                                    "ATS Prefetch NG - Series Thumbnails",
                                    "ATS Prefetch NG - Series WADO Metadata"
                                ],
                                "createdTimeRange": [
                                    "2022-03-15T01:20:51.548+0100",
                                    "2022-03-15T01:20:51.929+0100"
                                ],
                                "updatedTimeRange": [
                                    "2022-03-15T19:39:00.706+0100",
                                    "2022-03-15T19:41:30.097+0100"
                                ],
                                "scheduledTimeRange": [
                                    "2022-03-15T19:38:00.692+0100",
                                    "2022-03-15T19:41:00.721+0100"
                                ],
                                "processingStartTimeRange": [
                                    "2022-03-15T19:39:00.584+0100",
                                    "2022-03-15T19:41:05.308+0100"
                                ],
                                "processingEndTimeRange": [
                                    "2022-03-15T19:39:00.703+0100",
                                    "2022-03-15T19:41:29.900+0100"
                                ]
                            },
                            {
                                "batchID": "ATS Prefetch Priors on Store[1.2.276.0.37.1.406.201712.240110]",
                                "tasks": {
                                    "warning": "2"
                                },
                                "dicomDeviceName": [
                                    "demoj4c"
                                ],
                                "ExporterID": [
                                    "ATS Prefetch NG - Series Thumbnails",
                                    "ATS Prefetch NG - Series WADO Metadata"
                                ],
                                "createdTimeRange": [
                                    "2022-02-08T18:45:29.656+0100",
                                    "2022-02-08T18:45:29.664+0100"
                                ],
                                "updatedTimeRange": [
                                    "2022-02-08T18:45:49.821+0100",
                                    "2022-02-08T18:45:50.111+0100"
                                ],
                                "scheduledTimeRange": [
                                    "2022-02-08T18:45:29.489+0100",
                                    "2022-02-08T18:45:29.489+0100"
                                ],
                                "processingStartTimeRange": [
                                    "2022-02-08T18:45:48.149+0100",
                                    "2022-02-08T18:45:48.585+0100"
                                ],
                                "processingEndTimeRange": [
                                    "2022-02-08T18:45:49.821+0100",
                                    "2022-02-08T18:45:50.110+0100"
                                ]
                            },
                            {
                                "batchID": "recreate-tasks-5.24.iuids",
                                "tasks": {
                                    "warning": "9"
                                },
                                "dicomDeviceName": [
                                    "demoj4c"
                                ],
                                "ExporterID": [
                                    "Export Objects to STORESCP"
                                ],
                                "createdTimeRange": [
                                    "2021-09-24T16:17:16.929+0200",
                                    "2021-09-24T16:17:17.092+0200"
                                ],
                                "updatedTimeRange": [
                                    "2021-09-24T16:27:46.787+0200",
                                    "2021-09-24T16:27:46.884+0200"
                                ],
                                "scheduledTimeRange": [
                                    "2021-09-24T16:27:46.633+0200",
                                    "2021-09-24T16:27:46.633+0200"
                                ],
                                "processingStartTimeRange": [
                                    "2021-09-24T16:27:46.765+0200",
                                    "2021-09-24T16:27:46.864+0200"
                                ],
                                "processingEndTimeRange": [
                                    "2021-09-24T16:27:46.787+0200",
                                    "2021-09-24T16:27:46.884+0200"
                                ]
                            },
                            {
                                "batchID": "ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]",
                                "tasks": {
                                    "failed": "32"
                                },
                                "dicomDeviceName": [
                                    "demoj4c"
                                ],
                                "ExporterID": [
                                    "ATS Prefetch NG - QIDO Study Instances",
                                    "ATS Prefetch NG - QIDO Study Series",
                                    "ATS Prefetch NG - Series Thumbnails",
                                    "ATS Prefetch NG - Series WADO Metadata"
                                ],
                                "createdTimeRange": [
                                    "2022-03-15T01:20:51.548+0100",
                                    "2022-03-15T01:20:51.929+0100"
                                ],
                                "updatedTimeRange": [
                                    "2022-03-15T19:39:00.706+0100",
                                    "2022-03-15T19:41:30.097+0100"
                                ],
                                "scheduledTimeRange": [
                                    "2022-03-15T19:38:00.692+0100",
                                    "2022-03-15T19:41:00.721+0100"
                                ],
                                "processingStartTimeRange": [
                                    "2022-03-15T19:39:00.584+0100",
                                    "2022-03-15T19:41:05.308+0100"
                                ],
                                "processingEndTimeRange": [
                                    "2022-03-15T19:39:00.703+0100",
                                    "2022-03-15T19:41:29.900+0100"
                                ]
                            },
                            {
                                "batchID": "ATS Prefetch Priors on Store[1.2.276.0.37.1.406.201712.240110]",
                                "tasks": {
                                    "warning": "2"
                                },
                                "dicomDeviceName": [
                                    "demoj4c"
                                ],
                                "ExporterID": [
                                    "ATS Prefetch NG - Series Thumbnails",
                                    "ATS Prefetch NG - Series WADO Metadata"
                                ],
                                "createdTimeRange": [
                                    "2022-02-08T18:45:29.656+0100",
                                    "2022-02-08T18:45:29.664+0100"
                                ],
                                "updatedTimeRange": [
                                    "2022-02-08T18:45:49.821+0100",
                                    "2022-02-08T18:45:50.111+0100"
                                ],
                                "scheduledTimeRange": [
                                    "2022-02-08T18:45:29.489+0100",
                                    "2022-02-08T18:45:29.489+0100"
                                ],
                                "processingStartTimeRange": [
                                    "2022-02-08T18:45:48.149+0100",
                                    "2022-02-08T18:45:48.585+0100"
                                ],
                                "processingEndTimeRange": [
                                    "2022-02-08T18:45:49.821+0100",
                                    "2022-02-08T18:45:50.110+0100"
                                ]
                            },
                            {
                                "batchID": "recreate-tasks-5.24.iuids",
                                "tasks": {
                                    "warning": "9"
                                },
                                "dicomDeviceName": [
                                    "demoj4c"
                                ],
                                "ExporterID": [
                                    "Export Objects to STORESCP"
                                ],
                                "createdTimeRange": [
                                    "2021-09-24T16:17:16.929+0200",
                                    "2021-09-24T16:17:17.092+0200"
                                ],
                                "updatedTimeRange": [
                                    "2021-09-24T16:27:46.787+0200",
                                    "2021-09-24T16:27:46.884+0200"
                                ],
                                "scheduledTimeRange": [
                                    "2021-09-24T16:27:46.633+0200",
                                    "2021-09-24T16:27:46.633+0200"
                                ],
                                "processingStartTimeRange": [
                                    "2021-09-24T16:27:46.765+0200",
                                    "2021-09-24T16:27:46.864+0200"
                                ],
                                "processingEndTimeRange": [
                                    "2021-09-24T16:27:46.787+0200",
                                    "2021-09-24T16:27:46.884+0200"
                                ]
                            },
                            {
                                "batchID": "ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]",
                                "tasks": {
                                    "failed": "32"
                                },
                                "dicomDeviceName": [
                                    "demoj4c"
                                ],
                                "ExporterID": [
                                    "ATS Prefetch NG - QIDO Study Instances",
                                    "ATS Prefetch NG - QIDO Study Series",
                                    "ATS Prefetch NG - Series Thumbnails",
                                    "ATS Prefetch NG - Series WADO Metadata"
                                ],
                                "createdTimeRange": [
                                    "2022-03-15T01:20:51.548+0100",
                                    "2022-03-15T01:20:51.929+0100"
                                ],
                                "updatedTimeRange": [
                                    "2022-03-15T19:39:00.706+0100",
                                    "2022-03-15T19:41:30.097+0100"
                                ],
                                "scheduledTimeRange": [
                                    "2022-03-15T19:38:00.692+0100",
                                    "2022-03-15T19:41:00.721+0100"
                                ],
                                "processingStartTimeRange": [
                                    "2022-03-15T19:39:00.584+0100",
                                    "2022-03-15T19:41:05.308+0100"
                                ],
                                "processingEndTimeRange": [
                                    "2022-03-15T19:39:00.703+0100",
                                    "2022-03-15T19:41:29.900+0100"
                                ]
                            },
                            {
                                "batchID": "ATS Prefetch Priors on Store[1.2.276.0.37.1.406.201712.240110]",
                                "tasks": {
                                    "warning": "2"
                                },
                                "dicomDeviceName": [
                                    "demoj4c"
                                ],
                                "ExporterID": [
                                    "ATS Prefetch NG - Series Thumbnails",
                                    "ATS Prefetch NG - Series WADO Metadata"
                                ],
                                "createdTimeRange": [
                                    "2022-02-08T18:45:29.656+0100",
                                    "2022-02-08T18:45:29.664+0100"
                                ],
                                "updatedTimeRange": [
                                    "2022-02-08T18:45:49.821+0100",
                                    "2022-02-08T18:45:50.111+0100"
                                ],
                                "scheduledTimeRange": [
                                    "2022-02-08T18:45:29.489+0100",
                                    "2022-02-08T18:45:29.489+0100"
                                ],
                                "processingStartTimeRange": [
                                    "2022-02-08T18:45:48.149+0100",
                                    "2022-02-08T18:45:48.585+0100"
                                ],
                                "processingEndTimeRange": [
                                    "2022-02-08T18:45:49.821+0100",
                                    "2022-02-08T18:45:50.110+0100"
                                ]
                            },
                            {
                                "batchID": "recreate-tasks-5.24.iuids",
                                "tasks": {
                                    "warning": "9"
                                },
                                "dicomDeviceName": [
                                    "demoj4c"
                                ],
                                "ExporterID": [
                                    "Export Objects to STORESCP"
                                ],
                                "createdTimeRange": [
                                    "2021-09-24T16:17:16.929+0200",
                                    "2021-09-24T16:17:17.092+0200"
                                ],
                                "updatedTimeRange": [
                                    "2021-09-24T16:27:46.787+0200",
                                    "2021-09-24T16:27:46.884+0200"
                                ],
                                "scheduledTimeRange": [
                                    "2021-09-24T16:27:46.633+0200",
                                    "2021-09-24T16:27:46.633+0200"
                                ],
                                "processingStartTimeRange": [
                                    "2021-09-24T16:27:46.765+0200",
                                    "2021-09-24T16:27:46.864+0200"
                                ],
                                "processingEndTimeRange": [
                                    "2021-09-24T16:27:46.787+0200",
                                    "2021-09-24T16:27:46.884+0200"
                                ]
                            },
                            {
                                "batchID": "ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]",
                                "tasks": {
                                    "failed": "32"
                                },
                                "dicomDeviceName": [
                                    "demoj4c"
                                ],
                                "ExporterID": [
                                    "ATS Prefetch NG - QIDO Study Instances",
                                    "ATS Prefetch NG - QIDO Study Series",
                                    "ATS Prefetch NG - Series Thumbnails",
                                    "ATS Prefetch NG - Series WADO Metadata"
                                ],
                                "createdTimeRange": [
                                    "2022-03-15T01:20:51.548+0100",
                                    "2022-03-15T01:20:51.929+0100"
                                ],
                                "updatedTimeRange": [
                                    "2022-03-15T19:39:00.706+0100",
                                    "2022-03-15T19:41:30.097+0100"
                                ],
                                "scheduledTimeRange": [
                                    "2022-03-15T19:38:00.692+0100",
                                    "2022-03-15T19:41:00.721+0100"
                                ],
                                "processingStartTimeRange": [
                                    "2022-03-15T19:39:00.584+0100",
                                    "2022-03-15T19:41:05.308+0100"
                                ],
                                "processingEndTimeRange": [
                                    "2022-03-15T19:39:00.703+0100",
                                    "2022-03-15T19:41:29.900+0100"
                                ]
                            },
                            {
                                "batchID": "ATS Prefetch Priors on Store[1.2.276.0.37.1.406.201712.240110]",
                                "tasks": {
                                    "warning": "2"
                                },
                                "dicomDeviceName": [
                                    "demoj4c"
                                ],
                                "ExporterID": [
                                    "ATS Prefetch NG - Series Thumbnails",
                                    "ATS Prefetch NG - Series WADO Metadata"
                                ],
                                "createdTimeRange": [
                                    "2022-02-08T18:45:29.656+0100",
                                    "2022-02-08T18:45:29.664+0100"
                                ],
                                "updatedTimeRange": [
                                    "2022-02-08T18:45:49.821+0100",
                                    "2022-02-08T18:45:50.111+0100"
                                ],
                                "scheduledTimeRange": [
                                    "2022-02-08T18:45:29.489+0100",
                                    "2022-02-08T18:45:29.489+0100"
                                ],
                                "processingStartTimeRange": [
                                    "2022-02-08T18:45:48.149+0100",
                                    "2022-02-08T18:45:48.585+0100"
                                ],
                                "processingEndTimeRange": [
                                    "2022-02-08T18:45:49.821+0100",
                                    "2022-02-08T18:45:50.110+0100"
                                ]
                            },
                            {
                                "batchID": "recreate-tasks-5.24.iuids",
                                "tasks": {
                                    "warning": "9"
                                },
                                "dicomDeviceName": [
                                    "demoj4c"
                                ],
                                "ExporterID": [
                                    "Export Objects to STORESCP"
                                ],
                                "createdTimeRange": [
                                    "2021-09-24T16:17:16.929+0200",
                                    "2021-09-24T16:17:17.092+0200"
                                ],
                                "updatedTimeRange": [
                                    "2021-09-24T16:27:46.787+0200",
                                    "2021-09-24T16:27:46.884+0200"
                                ],
                                "scheduledTimeRange": [
                                    "2021-09-24T16:27:46.633+0200",
                                    "2021-09-24T16:27:46.633+0200"
                                ],
                                "processingStartTimeRange": [
                                    "2021-09-24T16:27:46.765+0200",
                                    "2021-09-24T16:27:46.864+0200"
                                ],
                                "processingEndTimeRange": [
                                    "2021-09-24T16:27:46.787+0200",
                                    "2021-09-24T16:27:46.884+0200"
                                ]
                            },
                            {
                                "batchID": "ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]",
                                "tasks": {
                                    "failed": "32"
                                },
                                "dicomDeviceName": [
                                    "demoj4c"
                                ],
                                "ExporterID": [
                                    "ATS Prefetch NG - QIDO Study Instances",
                                    "ATS Prefetch NG - QIDO Study Series",
                                    "ATS Prefetch NG - Series Thumbnails",
                                    "ATS Prefetch NG - Series WADO Metadata"
                                ],
                                "createdTimeRange": [
                                    "2022-03-15T01:20:51.548+0100",
                                    "2022-03-15T01:20:51.929+0100"
                                ],
                                "updatedTimeRange": [
                                    "2022-03-15T19:39:00.706+0100",
                                    "2022-03-15T19:41:30.097+0100"
                                ],
                                "scheduledTimeRange": [
                                    "2022-03-15T19:38:00.692+0100",
                                    "2022-03-15T19:41:00.721+0100"
                                ],
                                "processingStartTimeRange": [
                                    "2022-03-15T19:39:00.584+0100",
                                    "2022-03-15T19:41:05.308+0100"
                                ],
                                "processingEndTimeRange": [
                                    "2022-03-15T19:39:00.703+0100",
                                    "2022-03-15T19:41:29.900+0100"
                                ]
                            },
                            {
                                "batchID": "ATS Prefetch Priors on Store[1.2.276.0.37.1.406.201712.240110]",
                                "tasks": {
                                    "warning": "2"
                                },
                                "dicomDeviceName": [
                                    "demoj4c"
                                ],
                                "ExporterID": [
                                    "ATS Prefetch NG - Series Thumbnails",
                                    "ATS Prefetch NG - Series WADO Metadata"
                                ],
                                "createdTimeRange": [
                                    "2022-02-08T18:45:29.656+0100",
                                    "2022-02-08T18:45:29.664+0100"
                                ],
                                "updatedTimeRange": [
                                    "2022-02-08T18:45:49.821+0100",
                                    "2022-02-08T18:45:50.111+0100"
                                ],
                                "scheduledTimeRange": [
                                    "2022-02-08T18:45:29.489+0100",
                                    "2022-02-08T18:45:29.489+0100"
                                ],
                                "processingStartTimeRange": [
                                    "2022-02-08T18:45:48.149+0100",
                                    "2022-02-08T18:45:48.585+0100"
                                ],
                                "processingEndTimeRange": [
                                    "2022-02-08T18:45:49.821+0100",
                                    "2022-02-08T18:45:50.110+0100"
                                ]
                            },
                            {
                                "batchID": "recreate-tasks-5.24.iuids",
                                "tasks": {
                                    "warning": "9"
                                },
                                "dicomDeviceName": [
                                    "demoj4c"
                                ],
                                "ExporterID": [
                                    "Export Objects to STORESCP"
                                ],
                                "createdTimeRange": [
                                    "2021-09-24T16:17:16.929+0200",
                                    "2021-09-24T16:17:17.092+0200"
                                ],
                                "updatedTimeRange": [
                                    "2021-09-24T16:27:46.787+0200",
                                    "2021-09-24T16:27:46.884+0200"
                                ],
                                "scheduledTimeRange": [
                                    "2021-09-24T16:27:46.633+0200",
                                    "2021-09-24T16:27:46.633+0200"
                                ],
                                "processingStartTimeRange": [
                                    "2021-09-24T16:27:46.765+0200",
                                    "2021-09-24T16:27:46.864+0200"
                                ],
                                "processingEndTimeRange": [
                                    "2021-09-24T16:27:46.787+0200",
                                    "2021-09-24T16:27:46.884+0200"
                                ]
                            }
                        ];
                    }else{
                        res = [{"taskID":"1913","dicomDeviceName":"demoj4c","queue":"Export3","type":"EXPORT","status":"FAILED","failures":"11","createdTime":"2022-03-15T01:20:51.518+0100","updatedTime":"2022-03-15T19:44:09.218+0100","scheduledTime":"2022-03-15T19:44:00.947+0100","processingStartTime":"2022-03-15T19:44:04.725+0100","processingEndTime":"2022-03-15T19:44:09.026+0100","errorMessage":"Java heap space","LocalAET":"DEMOJ4C","ExporterID":"XDS-PnR","StudyInstanceUID":"2.25.253254299183063559800543717906499910894","NumberOfInstances":"2079","Modality":["CT"]},{"taskID":"1911","dicomDeviceName":"demoj4c","queue":"Export2","type":"EXPORT","status":"FAILED","failures":"11","createdTime":"2022-03-15T01:20:51.492+0100","updatedTime":"2022-03-15T19:43:12.413+0100","scheduledTime":"2022-03-15T19:43:00.654+0100","processingStartTime":"2022-03-15T19:43:07.180+0100","processingEndTime":"2022-03-15T19:43:12.201+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series Thumbnails","StudyInstanceUID":"2.25.253254299183063559800543717906499910894","NumberOfInstances":"2079","Modality":["CT"]},{"taskID":"1910","dicomDeviceName":"demoj4c","queue":"Export10","type":"EXPORT","status":"FAILED","failures":"11","createdTime":"2022-03-15T01:20:51.476+0100","updatedTime":"2022-03-15T19:43:11.194+0100","scheduledTime":"2022-03-15T19:43:00.643+0100","processingStartTime":"2022-03-15T19:43:06.158+0100","processingEndTime":"2022-03-15T19:43:10.799+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series WADO Metadata","StudyInstanceUID":"2.25.253254299183063559800543717906499910894","NumberOfInstances":"2079","Modality":["CT"]},{"taskID":"1912","dicomDeviceName":"demoj4c","queue":"Export2","type":"EXPORT","status":"FAILED","failures":"11","createdTime":"2022-03-15T01:20:51.504+0100","updatedTime":"2022-03-15T19:43:11.194+0100","scheduledTime":"2022-03-15T19:43:00.644+0100","processingStartTime":"2022-03-15T19:43:05.960+0100","processingEndTime":"2022-03-15T19:43:10.799+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - QIDO Study Series","StudyInstanceUID":"2.25.253254299183063559800543717906499910894","NumberOfInstances":"2079","Modality":["CT"]},{"taskID":"1914","dicomDeviceName":"demoj4c","queue":"Export2","type":"EXPORT","status":"FAILED","failures":"11","createdTime":"2022-03-15T01:20:51.530+0100","updatedTime":"2022-03-15T19:43:10.206+0100","scheduledTime":"2022-03-15T19:43:00.643+0100","processingStartTime":"2022-03-15T19:43:04.982+0100","processingEndTime":"2022-03-15T19:43:09.786+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - QIDO Study Instances","StudyInstanceUID":"2.25.253254299183063559800543717906499910894","NumberOfInstances":"2079","Modality":["CT"]},{"taskID":"1917","dicomDeviceName":"demoj4c","queue":"Export2","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.572+0100","updatedTime":"2022-03-15T19:41:30.097+0100","scheduledTime":"2022-03-15T19:41:00.721+0100","processingStartTime":"2022-03-15T19:41:05.308+0100","processingEndTime":"2022-03-15T19:41:29.900+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series Thumbnails","StudyInstanceUID":"2.25.133790264388916295901178041223591125703","NumberOfInstances":"310","Modality":["CT","PR","KO"]},{"taskID":"1941","dicomDeviceName":"demoj4c","queue":"Export2","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.888+0100","updatedTime":"2022-03-15T19:39:01.415+0100","scheduledTime":"2022-03-15T19:38:01.384+0100","processingStartTime":"2022-03-15T19:39:01.226+0100","processingEndTime":"2022-03-15T19:39:01.414+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series Thumbnails","StudyInstanceUID":"2.25.229975568262236341440816121223809588447","NumberOfInstances":"2958","Modality":["CT","SR"]},{"taskID":"1933","dicomDeviceName":"demoj4c","queue":"Export2","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.746+0100","updatedTime":"2022-03-15T19:39:01.357+0100","scheduledTime":"2022-03-15T19:38:01.344+0100","processingStartTime":"2022-03-15T19:39:01.217+0100","processingEndTime":"2022-03-15T19:39:01.356+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series Thumbnails","StudyInstanceUID":"2.25.150446237572158364218250943566632959728","NumberOfInstances":"1007","Modality":["CT","PR","SR"]},{"taskID":"1921","dicomDeviceName":"demoj4c","queue":"Export2","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.622+0100","updatedTime":"2022-03-15T19:39:01.283+0100","scheduledTime":"2022-03-15T19:38:01.259+0100","processingStartTime":"2022-03-15T19:39:01.209+0100","processingEndTime":"2022-03-15T19:39:01.281+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series Thumbnails","StudyInstanceUID":"2.25.38075169170603874823282185203405259704","NumberOfInstances":"7","Modality":["PR","DX"]},{"taskID":"1915","dicomDeviceName":"demoj4c","queue":"Export2","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.548+0100","updatedTime":"2022-03-15T19:39:01.221+0100","scheduledTime":"2022-03-15T19:38:01.104+0100","processingStartTime":"2022-03-15T19:39:01.192+0100","processingEndTime":"2022-03-15T19:39:01.220+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - QIDO Study Instances","StudyInstanceUID":"2.25.133790264388916295901178041223591125703","NumberOfInstances":"310","Modality":["CT","PR","KO"]},{"taskID":"1925","dicomDeviceName":"demoj4c","queue":"Export2","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.667+0100","updatedTime":"2022-03-15T19:39:01.221+0100","scheduledTime":"2022-03-15T19:38:01.097+0100","processingStartTime":"2022-03-15T19:39:01.176+0100","processingEndTime":"2022-03-15T19:39:01.220+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series Thumbnails","StudyInstanceUID":"2.25.244811086690625888516433847572724695651","NumberOfInstances":"3","Modality":["CR"]},{"taskID":"1916","dicomDeviceName":"demoj4c","queue":"Export2","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.559+0100","updatedTime":"2022-03-15T19:39:01.189+0100","scheduledTime":"2022-03-15T19:38:01.062+0100","processingStartTime":"2022-03-15T19:39:01.167+0100","processingEndTime":"2022-03-15T19:39:01.188+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - QIDO Study Series","StudyInstanceUID":"2.25.133790264388916295901178041223591125703","NumberOfInstances":"310","Modality":["CT","PR","KO"]},{"taskID":"1919","dicomDeviceName":"demoj4c","queue":"Export2","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.597+0100","updatedTime":"2022-03-15T19:39:01.180+0100","scheduledTime":"2022-03-15T19:38:01.015+0100","processingStartTime":"2022-03-15T19:39:01.159+0100","processingEndTime":"2022-03-15T19:39:01.179+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - QIDO Study Instances","StudyInstanceUID":"2.25.38075169170603874823282185203405259704","NumberOfInstances":"7","Modality":["PR","DX"]},{"taskID":"1920","dicomDeviceName":"demoj4c","queue":"Export2","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.610+0100","updatedTime":"2022-03-15T19:39:01.170+0100","scheduledTime":"2022-03-15T19:38:00.987+0100","processingStartTime":"2022-03-15T19:39:01.142+0100","processingEndTime":"2022-03-15T19:39:01.169+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - QIDO Study Series","StudyInstanceUID":"2.25.38075169170603874823282185203405259704","NumberOfInstances":"7","Modality":["PR","DX"]},{"taskID":"1929","dicomDeviceName":"demoj4c","queue":"Export2","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.701+0100","updatedTime":"2022-03-15T19:39:01.170+0100","scheduledTime":"2022-03-15T19:38:00.975+0100","processingStartTime":"2022-03-15T19:39:01.125+0100","processingEndTime":"2022-03-15T19:39:01.169+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series Thumbnails","StudyInstanceUID":"2.25.73758429535919582411283778014440169399","NumberOfInstances":"3","Modality":["PR","SR","CR"]},{"taskID":"1923","dicomDeviceName":"demoj4c","queue":"Export2","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.647+0100","updatedTime":"2022-03-15T19:39:01.137+0100","scheduledTime":"2022-03-15T19:38:00.911+0100","processingStartTime":"2022-03-15T19:39:01.114+0100","processingEndTime":"2022-03-15T19:39:01.136+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - QIDO Study Instances","StudyInstanceUID":"2.25.244811086690625888516433847572724695651","NumberOfInstances":"3","Modality":["CR"]},{"taskID":"1924","dicomDeviceName":"demoj4c","queue":"Export2","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.659+0100","updatedTime":"2022-03-15T19:39:01.125+0100","scheduledTime":"2022-03-15T19:38:00.877+0100","processingStartTime":"2022-03-15T19:39:01.088+0100","processingEndTime":"2022-03-15T19:39:01.124+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - QIDO Study Series","StudyInstanceUID":"2.25.244811086690625888516433847572724695651","NumberOfInstances":"3","Modality":["CR"]},{"taskID":"1942","dicomDeviceName":"demoj4c","queue":"Export10","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.896+0100","updatedTime":"2022-03-15T19:39:01.099+0100","scheduledTime":"2022-03-15T19:38:00.984+0100","processingStartTime":"2022-03-15T19:39:00.860+0100","processingEndTime":"2022-03-15T19:39:01.098+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - Series WADO Metadata","StudyInstanceUID":"2.25.229975568262236341440816121223809588447","NumberOfInstances":"2958","Modality":["CT","SR"]},{"taskID":"1927","dicomDeviceName":"demoj4c","queue":"Export2","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.684+0100","updatedTime":"2022-03-15T19:39:01.099+0100","scheduledTime":"2022-03-15T19:38:00.830+0100","processingStartTime":"2022-03-15T19:39:01.063+0100","processingEndTime":"2022-03-15T19:39:01.098+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - QIDO Study Instances","StudyInstanceUID":"2.25.73758429535919582411283778014440169399","NumberOfInstances":"3","Modality":["PR","SR","CR"]},{"taskID":"1928","dicomDeviceName":"demoj4c","queue":"Export2","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.692+0100","updatedTime":"2022-03-15T19:39:01.074+0100","scheduledTime":"2022-03-15T19:38:00.820+0100","processingStartTime":"2022-03-15T19:39:01.029+0100","processingEndTime":"2022-03-15T19:39:01.073+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - QIDO Study Series","StudyInstanceUID":"2.25.73758429535919582411283778014440169399","NumberOfInstances":"3","Modality":["PR","SR","CR"]},{"taskID":"1928","dicomDeviceName":"demoj4c","queue":"Export2","type":"EXPORT","status":"FAILED","batchID":"ATS Prefetch Priors on Store[2.25.253254299183063559800543717906499910894]","failures":"11","createdTime":"2022-03-15T01:20:51.692+0100","updatedTime":"2022-03-15T19:39:01.074+0100","scheduledTime":"2022-03-15T19:38:00.820+0100","processingStartTime":"2022-03-15T19:39:01.029+0100","processingEndTime":"2022-03-15T19:39:01.073+0100","errorMessage":"RESTEASY004655: Unable to invoke request: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target","LocalAET":"DEMOJ4C","ExporterID":"ATS Prefetch NG - QIDO Study Series","StudyInstanceUID":"2.25.73758429535919582411283778014440169399","NumberOfInstances":"3","Modality":["PR","SR","CR"]}];
                    }
                }
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
/*                            return {
                                offset: offset + index,
                                properties: properties,
                                propertiesAttr: propertiesAttr,
                                showProperties: false
                            };*/
                            return properties;
                        }else{
                            $this.cfpLoadingBar.complete();
                            if (_.hasIn(properties, 'Modality')){
                                properties.Modality = properties.Modality.join(',');
                            }
   /*                         return {
                                offset: offset + index,
                                properties: properties,
                                propertiesAttr: properties,
                                showProperties: false
                            };*/
                            return properties;
                        }
                    });
                    this.moreTasks = res.length > this.filterObject['limit'];
                    if(this.moreTasks)
                        this.matches.splice(this.matches.length-1,1);
                }else{
                    $this.cfpLoadingBar.complete();
                    $this.matches = [];
                    this.mainservice.showMsg($localize `:@@no_tasks_found:No tasks found!`)
                }
            }, (err) => {
                $this.cfpLoadingBar.complete();
                $this.matches = [];
                console.log('err', err);
            });
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
    moreTasks = false;
    batchChange(e){
        this.matches = [];
    }
    getCount(){
        this.cfpLoadingBar.start();
        this.service.getCount(this.filterObject).subscribe((count)=>{
            try{
                this.count = count.count;
                this.setFilterSchema();
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
            if(this.filterObject.status == "SCHEDULED" || this.filterObject.status == $localize `:@@export.in_process:IN PROCESS`){
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
        let text =  $localize `:@@matching_task_question:Are you sure, you want to ${Globalvar.getActionText(this.allAction)} all matching tasks?`;
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
                            this.cfpLoadingBar.complete();
                            if(_.hasIn(res,"count")){
                                this.mainservice.showMsg($localize `:@@tasks_canceled_param:${res.count || 0}:count: tasks canceled successfully!`);
                            }else{
                                this.mainservice.showMsg($localize `:@@tasks_canceled:Tasks canceled successfully!`);
                            }
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
                    content: $localize `:@@export.tasks_reschedule:Tasks reschedule`,
                    doNotSave:true,
                    form_schema: [
                        [
                            [
                                {
                                    tag:"label_large",
                                    text:text || $localize `:@@export.change_exporter_text:Change the exporter for all rescheduled tasks. To reschedule with the original exporters associated with the tasks, leave blank:`
                                }
                            ],
                            [
                                {
                                    tag:"label",
                                    text:$localize `:@@exporter_id:Exporter ID`,
                                },
                                {
                                    tag:"select",
                                    options:this.exporters.map(exporter=>{
                                        return {
                                            text:exporter.description || exporter.id,
                                            value:exporter.id
                                        }
                                    }),
                                    showStar:true,
                                    filterKey:"selectedExporter",
                                    description:$localize `:@@exporter_id:Exporter ID`,
                                    placeholder:$localize `:@@exporter_id:Exporter ID`
                                }
                            ],
                            [
                                {
                                    tag:"label_large",
                                    text:$localize `:@@export.select_device_if_you_want_to_reschedule:Select device if you want to reschedule to an other device:`
                                }
                            ],
                            [
                                {
                                    tag:"label",
                                    text:$localize `:@@device:Device`
                                },
                                {
                                    tag:"multi-select",
                                    options:this.devices.map(device=>{
                                        return {
                                            text:device.dicomDeviceName,
                                            value:device.dicomDeviceName
                                        }
                                    }),
                                    showStar:true,
                                    filterKey:"newDeviceName",
                                    description:$localize `:@@device:Device`,
                                    placeholder:$localize `:@@device:Device`
                                }
                            ],
                            [
                                {
                                    tag:"label",
                                    text:$localize `:@@schedule_at_desc:Schedule at (if not set, schedule immediately)`
                                },
                                {
                                    tag:"single-date-time-picker",
                                    type:"text",
                                    filterKey:"scheduledTime",
                                    description:$localize `:@@schedule_at_desc:Schedule at (if not set, schedule immediately)`
                                }
                            ]
                        ]
                    ],
                    result: {
                        schema_model: {}
                    },
                    saveButton: $localize `:@@SUBMIT:SUBMIT`
                },
                    '520px'
                ).subscribe((ok)=>{
                    if (ok) {
                        this.cfpLoadingBar.start();
                        if(_.hasIn(ok, "schema_model.newDeviceName") && ok.schema_model.newDeviceName != ""){
                            filter["newDeviceName"] = ok.schema_model.newDeviceName;
                        }
                        if(_.hasIn(ok, "schema_model.scheduledTime") && ok.schema_model.scheduledTime != ""){
                            filter["scheduledTime"] = ok.schema_model.scheduledTime;
                        }
                        this.service.rescheduleAll(filter,ok.schema_model.selectedExporter).subscribe((res)=>{
                            this.cfpLoadingBar.complete();
                            if(_.hasIn(res,"count")){
                                this.mainservice.showMsg($localize `:@@tasks_rescheduled_param:${res.count || 0}:count: tasks rescheduled successfully!`);
                            }else{
                                this.mainservice.showMsg($localize `:@@tasks_rescheduled:Tasks rescheduled successfully!`);
                            }
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
                            this.cfpLoadingBar.complete();
                            this.mainservice.showMsgDeleteTasks(res);
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
            content: title || $localize `:@@export.task_reschedule:Task reschedule`,
            doNotSave:true,
            form_schema: this.service.getDialogSchema(this.exporters, this.devices, text),
            result: {
                schema_model: schema_model || {}
            },
            saveButton: $localize `:@@SUBMIT:SUBMIT`
        },
            '520px').subscribe((ok)=>{
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
                    if(_.hasIn(ok, "schema_model.scheduledTime") && ok.schema_model.scheduledTime != ""){
                        filter["scheduledTime"] = ok.schema_model.scheduledTime;
                    }
                    if(_.hasIn(ok, "schema_model.selectedExporter")){
                        id = ok.schema_model.selectedExporter;
                    }
                    this.matches.forEach((match, i)=>{
                        if(match.selected){
                            this.service.reschedule(match.taskID, id || match.ExporterID, filter)
                                .subscribe(
                                    (res) => {
                                        this.mainservice.showMsg($localize `:@@task_rescheduled_param:Task ${match.taskID}:taskid:
 rescheduled successfully!`);
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
        } else {
            this.confirm({
                content: $localize `:@@action_selected_entries_question:Are you sure you want to ${Globalvar.getActionText(mode)} selected entries?`
            }).subscribe(result => {
                if (result){
                    this.cfpLoadingBar.start();
                    this.matches.forEach((match)=>{
                        if(match.selected){
                            this.service[mode](match.taskID)
                                .subscribe((res) => {
                                    console.log("Execute result",res);
                                    if(mode === "cancel")
                                        this.mainservice.showMsg($localize `:@@task_canceled_param:Task ${match.taskID}:taskid: canceled successfully!`);
                                    else
                                        this.mainservice.showMsg($localize `:@@task_deleted_param:Task ${match.taskID}:taskid: deleted successfully!`);
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
            content: $localize `:@@batch_delete_question:Are you sure you want to delete all tasks of this batch?`
        }).subscribe(ok=>{
            if(ok){
                if(batchedTask.batchID){
                    let filter = Object.assign({},this.filterObject);
                    filter["batchID"] = batchedTask.batchID;
                    delete filter["limit"];
                    delete filter["offset"];
                    this.service.deleteAll(filter).subscribe((res)=>{
                        this.mainservice.showMsg($localize `:@@tasks_deleted_param:${res.count}:tasks: tasks deleted successfully!`);
                        this.cfpLoadingBar.complete();
                        this.search(0);
                    }, (err) => {
                        this.cfpLoadingBar.complete();
                        this.httpErrorHandler.handleError(err);
                    });
                }else{
                    this.mainservice.showError($localize `:@@batch_id_not_found:Batch ID not found!`);
                }
            }
        });
    }
    delete(match){
        let $this = this;
        let parameters: any = {
            content: $localize `:@@delete_task_question:Are you sure you want to delete this task?`,
            result: {
                select: this.exporters
            },
            saveButton: $localize `:@@DELETE:DELETE`
        };
        this.confirm(parameters).subscribe(result => {
            if (result){
                $this.cfpLoadingBar.start();
                this.service.delete(match.taskID)
                    .subscribe(
                        () => {
                            // match.status = 'CANCELED';
                            $this.cfpLoadingBar.complete();
                            $this.search(0);
                            this.mainservice.showMsg($localize `:@@task_deleted:Task deleted successfully!`)
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
            content: $localize `:@@want_to_cancel_this_task:Are you sure you want to cancel this task?`,
            result: {
                select: this.exporters
            },
            saveButton: $localize `:@@YES:YES`
        };
        this.confirm(parameters).subscribe(result => {
            if (result){
                $this.cfpLoadingBar.start();
                this.service.cancel(match.taskID)
                    .subscribe(
                        () => {
                            match.status = 'CANCELED';
                            $this.cfpLoadingBar.complete();
                            this.mainservice.showMsg($localize `:@@task_canceled:Task canceled successfully!`)
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
                if(_.hasIn(ok, "schema_model.scheduledTime") && ok.schema_model.scheduledTime != ""){
                    filter["scheduledTime"] = ok.schema_model.scheduledTime;
                }
                if(_.hasIn(ok, "schema_model.selectedExporter")){
                    id = ok.schema_model.selectedExporter;
                }
                this.service.reschedule(match.taskID, id || match.ExporterID, filter)
                    .subscribe(
                        (res) => {
                            this.cfpLoadingBar.complete();
                            if(_.hasIn(res,"count")){
                                this.mainservice.showMsg($localize `:@@tasks_rescheduled_param:${res.count || 0}:count: tasks rescheduled successfully!`);
                            }else{
                                this.mainservice.showMsg($localize `:@@task_rescheduled:Task rescheduled successfully!`);

                            }
                        },
                        (err) => {
                            this.cfpLoadingBar.complete();
                            this.httpErrorHandler.handleError(err);
                        });

            }
        },
        {
            selectedExporter: match.ExporterID
        },
        undefined,
        $localize `:@@export.change_the_exporter_id_only_if_you_want:Change the Exporter Id only if you want to reschedule to another exporter!`
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
        this.$http.get(`${j4care.addLastSlash(this.mainservice.baseUrl)}export`)

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
            .pipe(map(aet=> this.permissionService.filterAetDependingOnUiConfig(aet,'internal')))
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
