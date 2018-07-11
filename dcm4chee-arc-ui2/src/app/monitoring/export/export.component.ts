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
    exportTasks = [];
    filters = {
        ExporterID: undefined,
        offset: undefined,
        limit: 20,
        status: '*',
        dicomDeviceName: '',
        StudyInstanceUID: undefined,
        updatedTime: undefined,
        // updatedTimeObject: undefined,
        createdTime: undefined,
        batchID: undefined,
        orderby: undefined,
        // createdTimeObject: undefined
    };
    timer = {
        started:false,
        startText:"Start Auto Refresh",
        stopText:"Stop Auto Refresh"
    };
    statusValues = {};
    refreshInterval;
    interval = 10;
    Object = Object;
    status = [
        "TO SCHEDULE",
        "SCHEDULED",
        "IN PROCESS",
        "COMPLETED",
        "WARNING",
        "FAILED",
        "CANCELED"
    ];
    batchGrouped = false;
    isRole: any = (user)=>{return false;};
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
    constructor(
        public $http:J4careHttpService,
        public cfpLoadingBar: LoadingBarService,
        public mainservice: AppService,
        public  service: ExportService,
        public viewContainerRef: ViewContainerRef,
        public dialog: MatDialog,
        public config: MatDialogConfig,
        private httpErrorHandler:HttpErrorHandler,
        private route: ActivatedRoute
    ) {}
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
        this.route.queryParams.subscribe(params => {
            if(params && params['dicomDeviceName']){
                this.filters['dicomDeviceName'] = params['dicomDeviceName'];
                this.search(0);
            }
        });
        this.initExporters(1);
        // this.init();
        this.status.forEach(status =>{
            this.statusValues[status] = {
                count: 0,
                loader: false
            };
        });
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
        }
        this.statusChange();
    }
    // changeTest(e){
    //     console.log("changetest",e);
    //     this.filters.createdTime = e;
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
            width: '500px'
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
    getCounts(){
        let filters = Object.assign({},this.filters);
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
            this.$http.refreshToken().subscribe((response)=>{
                if(!this.mainservice.global.notSecure){
                    if(response && response.length != 0){
                        this.$http.resetAuthenticationInfo(response);
                        token = response['token'];
                    }else{
                        token = this.mainservice.global.authentication.token;
                    }
                }
                if(!this.mainservice.global.notSecure){
                    WindowRefService.nativeWindow.open(`../monitor/export?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&access_token=${token}&${this.mainservice.param(this.service.paramWithoutLimit(this.filters))}`);
                }else{
                    WindowRefService.nativeWindow.open(`../monitor/export?accept=text/csv${(semicolon?';delimiter=semicolon':'')}&${this.mainservice.param(this.service.paramWithoutLimit(this.filters))}`);
                }
            });
        });
/*        this.service.downloadCsv(this.filters).subscribe((csv)=>{
            let file = new File([csv._body], `export_${new Date().toDateString()}.csv`, {type: 'text/csv;charset=utf-8'});
            FileSaver.saveAs(file);
        },(err)=>{
            this.httpErrorHandler.handleError(err);
        });*/
    }
    showTaskDetail(task){
        this.filters.batchID = task.properties.batchID;
        this.batchGrouped = false;
        this.search(0);
    }
    search(offset) {
        let $this = this;
        $this.cfpLoadingBar.start();
        this.service.search(this.filters, offset,this.batchGrouped)
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
            .subscribe((res) => {
/*                    res = [{"batchID":"test12","tasks":{
                        "completed":60,
                        "warning":24,
                        "failed":12,
                        "in-process":5,
                        "scheduled":123,
                        "canceled":26
                    },"dicomDeviceName":["dcm4chee-arc", "dcm4chee-arc2"],"LocalAET":["DCM4CHEE"],"RemoteAET":["DCM4CHEE"],"DestinationAET":["DCM4CHEE"],"createdTimeRange":["2018-04-10 18:02:06.936","2018-04-10 18:02:07.049"],"updatedTimeRange":["2018-04-10 18:02:08.300311","2018-04-10 18:02:08.553547"],"scheduledTimeRange":["2018-04-10 18:02:06.935","2018-04-10 18:02:07.049"],"processingStartTimeRange":["2018-04-10 18:02:06.989","2018-04-10 18:02:07.079"],"processingEndTimeRange":["2018-04-10 18:02:08.31","2018-04-10 18:02:08.559"]},{"batchID":"test2","tasks":{"completed":"12","failed":3,"warning":34},"dicomDeviceName":["dcm4chee-arc"],"LocalAET":["DCM4CHEE"],"RemoteAET":["DCM4CHEE"],"DestinationAET":["DCM4CHEE"],"createdTimeRange":["2018-04-10 18:02:25.71","2018-04-10 18:02:26.206"],"updatedTimeRange":["2018-04-10 18:02:25.932859","2018-04-10 18:02:27.335741"],"scheduledTimeRange":["2018-04-10 18:02:25.709","2018-04-10 18:02:26.204"],"processingStartTimeRange":["2018-04-10 18:02:25.739","2018-04-10 18:02:26.622"],"processingEndTimeRange":["2018-04-10 18:02:25.943","2018-04-10 18:02:27.344"]}];
               */ if (res && res.length > 0){
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
                    $this.mainservice.setMessage({
                        'title': 'Info',
                        'text': 'No tasks found!',
                        'status': 'info'
                    });
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
        this.service.getCount(this.filters).subscribe((count)=>{
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
        this.allActionsActive = this.allActionsOptions.filter((o)=>{
            if(this.filters.status == "SCHEDULED" || this.filters.status == "IN PROCESS"){
                return o.value != 'reschedule';
            }else{
                if(!this.filters.status || this.filters.status === '*' || this.filters.status === '')
                    return o.value != 'cancel' && o.value != 'reschedule';
                else
                    return o.value != 'cancel';
            }
        });
    }
    allActionChanged(e){
        let text = `Are you sure, you want to ${this.allAction} all matching tasks?`;
/*        let filter = {
            dicomDeviceName:this.filters.dicomDeviceName?this.filters.dicomDeviceName:undefined,
            status:this.filters.status?this.filters.status:undefined
        };*/
        let filter = _.cloneDeep(this.filters);
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
                            this.mainservice.setMessage({
                                'title': 'Info',
                                'text': res.count + ' queues deleted successfully!',
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
                let dicomPrefixes = [];
                let noDicomExporters = [];
                _.forEach(this.exporters, (m, i) => {
                    if (m.id.indexOf(':') > -1) {
                        dicomPrefixes.push(m);
                    } else {
                        noDicomExporters.push(m);
                    }
                });
                this.config.viewContainerRef = this.viewContainerRef;
                this.dialogRef = this.dialog.open(ExportDialogComponent, {
                    height: 'auto',
                    width: '500px'
                });
                this.dialogRef.componentInstance.noDicomExporters = noDicomExporters;
                this.dialogRef.componentInstance.title = `Are you sure, you want to reschedule all matching tasks?`;
                this.dialogRef.componentInstance.warning = null;
                this.dialogRef.componentInstance.mode = "reschedule";
                this.dialogRef.componentInstance.subTitle = "Change the exporter for all rescheduled tasks. To reschedule with the original exporters associated with the tasks, leave blank:";
                this.dialogRef.componentInstance.okButtonLabel = 'RESCHEDULE';
                this.dialogRef.afterClosed().subscribe((ok) => {
                    if (ok) {
                        this.cfpLoadingBar.start();
                        this.service.rescheduleAll(filter,ok.selectedExporter).subscribe((res)=>{
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
    executeAll(mode){
        this.confirm({
            content: `Are you sure you want to ${mode} selected entries?`
        }).subscribe(result => {
            if (result){
                if(mode === "reschedule"){
                    let $this = this;
                    let id;
                    let noDicomExporters = [];
                    let dicomPrefixes = [];
                    _.forEach(this.exporters, (m, i) => {
                        if (m.id.indexOf(':') > -1){
                            dicomPrefixes.push(m);
                        }else{
                            noDicomExporters.push(m);
                        }
                    });
                    // if (match.properties.ExporterID){
/*                        if (match.properties.ExporterID.indexOf(':') > -1){
                            let parameters = _.split(match.properties.ExporterID, ':');
                            result = {
                                exportType: 'dicom',
                                selectedAet: parameters[1],
                                selectedExporter: undefined,
                                dicomPrefix: parameters[0] + ':'
                            };
                        }else{
                            result = {
                                exportType: 'nonedicom',
                                selectedAet: undefined,
                                selectedExporter: match.properties.ExporterID,
                                dicomPrefix: undefined
                            };
                        }*/
                    // }
                    this.dialogRef = this.dialog.open(ExportDialogComponent, {
                        height: 'auto',
                        width: '500px'
                    });
                    this.dialogRef.componentInstance.noDicomExporters = noDicomExporters;
                    this.dialogRef.componentInstance.dicomPrefixes = dicomPrefixes;
                    this.dialogRef.componentInstance.title = 'Task reschedule';
                    this.dialogRef.componentInstance.warning = null;
                    // this.dialogRef.componentInstance.result = result;
                    this.dialogRef.componentInstance.okButtonLabel = 'RESCHEDULE';
                    this.dialogRef.componentInstance.externalInternalAetMode = "internal";
                    this.dialogRef.componentInstance.mode = "single";
                    this.dialogRef.afterClosed().subscribe(result => {
                        if (result){
                            $this.cfpLoadingBar.start();
                            if (result.exportType === 'dicom'){
                                // id = result.dicomPrefix + result.selectedAet;
                                id = 'dicom:' + result.selectedAet;
                            }else{
                                id = result.selectedExporter;
                            }
                            this.matches.forEach((match)=>{
                                if(match.checked){
                                    // $this.cfpLoadingBar.start();
                                    $this.service.reschedule(match.properties.pk, id)
                                        .subscribe(
                                            (res) => {
                                                // $this.mainservice.setMessage({
                                                //     'title': 'Info',
                                                //     'text': 'Task rescheduled successfully!',
                                                //     'status': 'info'
                                                // });
                                                console.log("Execute result",res);
                                            },
                                            (err) => {
                                                $this.httpErrorHandler.handleError(err);
                                            });
                                }
                            });
                            setTimeout(()=>{
                                this.search(this.matches[0].offset || 0);
                                this.cfpLoadingBar.complete();
                            },300);
                        }
                    });
                }else{
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
            }
        });
    }
    msToTime(duration,mode?) {
        if(mode)
            if(mode === "sec")
                return ((duration*6 / 6000).toFixed(4)).toString() + ' s';
        else
            return ((duration / 60000).toFixed(4)).toString() + ' min';
  /*      if (duration > 999){

            let milliseconds: any = parseInt((((duration % 1000))).toString())
                , seconds: any = parseInt(((duration / 1000) % 60).toString())
                , minutes: any = parseInt(((duration / (1000 * 60)) % 60).toString())
                , hours: any = parseInt(((duration / (1000 * 60 * 60))).toString());
            if (hours === 0){
                if (minutes === 0){
                    return seconds.toString() + '.' + milliseconds.toString() + ' sec';
                }else{
                    seconds = (seconds < 10) ? '0' + seconds : seconds;
                    return minutes.toString() + ':' + seconds.toString() + '.' + milliseconds.toString() + ' min';
                }
            }else{

                hours = (hours < 10) ? '0' + hours : hours;
                minutes = (minutes < 10) ? '0' + minutes : minutes;
                seconds = (seconds < 10) ? '0' + seconds : seconds;

                return hours.toString() + ':' + minutes.toString() + ':' + seconds.toString() + '.' + milliseconds.toString() + ' h';
            }
        }else{
            return duration.toString() + ' ms';
        }*/
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
    reschedule(match) {
        let $this = this;
        let id;
        let noDicomExporters = [];
        let dicomPrefixes = [];
        let result;
        _.forEach(this.exporters, (m, i) => {
            if (m.id.indexOf(':') > -1){
                dicomPrefixes.push(m);
            }else{
                noDicomExporters.push(m);
            }
        });
        if (match.properties.ExporterID){
            if (match.properties.ExporterID.indexOf(':') > -1){
                let parameters = _.split(match.properties.ExporterID, ':');
                result = {
                    exportType: 'dicom',
                    selectedAet: parameters[1],
                    selectedExporter: undefined,
                    dicomPrefix: parameters[0] + ':'
                };
            }else{
                result = {
                    exportType: 'nonedicom',
                    selectedAet: undefined,
                    selectedExporter: match.properties.ExporterID,
                    dicomPrefix: undefined
                };
            }
        }
        this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(ExportDialogComponent, {
            height: 'auto',
            width: '500px'
        });
        this.dialogRef.componentInstance.noDicomExporters = noDicomExporters;
        this.dialogRef.componentInstance.dicomPrefixes = dicomPrefixes;
        this.dialogRef.componentInstance.title = 'Task reschedule';
        this.dialogRef.componentInstance.warning = null;
        this.dialogRef.componentInstance.mode = "reschedule";
        this.dialogRef.componentInstance.result = result;
        this.dialogRef.componentInstance.okButtonLabel = 'RESCHEDULE';
        this.dialogRef.afterClosed().subscribe(result => {
            if (result){
                $this.cfpLoadingBar.start();
                if (result.exportType === 'dicom'){
                    id = result.dicomPrefix + result.selectedAet;
                }else{
                    id = result.selectedExporter;
                }
                this.service.reschedule(match.properties.pk, id)
                    .subscribe(
                        (res) => {
                            $this.search(0);
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

    hasOlder(objs) {
        return objs && (objs.length === this.filters.limit);
    };
    hasNewer(objs) {
        return objs && objs.length && objs[0].offset;
    };
    newerOffset(objs) {
        return Math.max(0, objs[0].offset - this.filters.limit);
    };
    olderOffset(objs) {
        return objs[0].offset + this.filters.limit;
    };

/*    init() {
        let $this = this;
        $this.cfpLoadingBar.start();
        this.$http.get("../monitor/export")
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
            .subscribe((res) => {
                $this.exportTasks = res;
                // $this.queueName = res[0].name;
                $this.cfpLoadingBar.complete();
            })
    }*/
    initExporters(retries) {
        let $this = this;
        this.$http.get('../export')
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
            .subscribe(
                (res) => {
                    console.log('res', res);
                    console.log('exporters', $this.exporters);
                    $this.exporters = res;
                    console.log('exporters2', $this.exporters);
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
        },(err)=>{
            this.cfpLoadingBar.complete();
            console.error("Could not get devices",err);
        });
    }
    ngOnDestroy(){
        if(this.timer.started){
            this.timer.started = false;
            clearInterval(this.refreshInterval);
        }
    }
}
