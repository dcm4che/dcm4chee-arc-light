import {Component, OnInit, ViewContainerRef} from '@angular/core';
import {SlimLoadingBarService} from 'ng2-slim-loading-bar';
import {User} from '../../models/user';
import {Http} from '@angular/http';
import {ConfirmComponent} from '../../widgets/dialogs/confirm/confirm.component';
import {MdDialogConfig, MdDialog, MdDialogRef} from '@angular/material';
import * as _ from 'lodash';
import {AppService} from '../../app.service';
import {ExportService} from './export.service';
import {ExportDialogComponent} from '../../widgets/dialogs/export/export.component';
import {WindowRefService} from "../../helpers/window-ref.service";
import {HttpErrorHandler} from "../../helpers/http-error-handler";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {j4care} from "../../helpers/j4care.service";
import * as FileSaver from 'file-saver';


@Component({
  selector: 'app-export',
  templateUrl: './export.component.html'
})
export class ExportComponent implements OnInit {
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
        createdTime: undefined
        // createdTimeObject: undefined
    };

    isRole: any = (user)=>{return false;};
    dialogRef: MdDialogRef<any>;
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
    constructor(
        public $http:J4careHttpService,
        public cfpLoadingBar: SlimLoadingBarService,
        public mainservice: AppService,
        public  service: ExportService,
        public viewContainerRef: ViewContainerRef,
        public dialog: MdDialog,
        public config: MdDialogConfig,
        private httpErrorHandler:HttpErrorHandler
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
        this.initExporters(1);
        // this.init();
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
    downloadCsv(){
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
                WindowRefService.nativeWindow.open(`../monitor/export?accept=text/csv&access_token=${token}&${this.mainservice.param(this.service.paramWithoutLimit(this.filters))}`);
            }else{
                WindowRefService.nativeWindow.open(`../monitor/export?accept=text/csv&${this.mainservice.param(this.service.paramWithoutLimit(this.filters))}`);
            }
        });
/*        this.service.downloadCsv(this.filters).subscribe((csv)=>{
            let file = new File([csv._body], `export_${new Date().toDateString()}.csv`, {type: 'text/csv;charset=utf-8'});
            FileSaver.saveAs(file);
        },(err)=>{
            this.httpErrorHandler.handleError(err);
        });*/
    }
    search(offset) {
        let $this = this;
        $this.cfpLoadingBar.start();
        this.service.search(this.filters, offset)
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
            .subscribe((res) => {
                if (res && res.length > 0){
                    $this.matches = res.map((properties, index) => {
                        $this.cfpLoadingBar.complete();
                        if (_.hasIn(properties, 'Modality')){
                            properties.Modality = properties.Modality.join(',');
                        }
                        return {
                            offset: offset + index,
                            properties: properties,
                            showProperties: false
                        };
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
                this.dialogRef.componentInstance.subTitle = "Select an Exporter ID if you don't want to use the default one:";
                this.dialogRef.componentInstance.okButtonLabel = 'RESCHEDULE';
                this.dialogRef.afterClosed().subscribe((ok) => {
                    if (ok) {
                        this.cfpLoadingBar.start();
                        this.service.rescheduleAll(filter,ok.selectedExporter).subscribe((res)=>{
                            this.mainservice.setMessage({
                                'title': 'Info',
                                'text': res.count + ' queues rescheduled successfully!',
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
    getDifferenceTime(starttime, endtime){
        let start = new Date(starttime).getTime();
        let end = new Date(endtime).getTime();
        if (!start || !end || end < start){
            return null;
        }else{
            return this.msToTime(new Date(endtime).getTime() - new Date(starttime).getTime());
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
    msToTime(duration) {
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
                $this.cfpLoadingBar.start();
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
}
