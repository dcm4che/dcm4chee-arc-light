import {Component, OnInit, ViewContainerRef} from '@angular/core';
import {User} from '../../models/user';
import {ConfirmComponent} from '../../widgets/dialogs/confirm/confirm.component';
// import { MatLegacyDialogConfig as MatDialogConfig, MatLegacyDialog as MatDialog, MatLegacyDialogRef as MatDialogRef } from '@angular/material/legacy-dialog';
import {MatDialog, MatDialogRef} from "@angular/material/dialog";
import * as _ from 'lodash-es';
import {AppService} from '../../app.service';
import {StorageCommitmentService} from './storage-commitment.service';
import {WindowRefService} from "../../helpers/window-ref.service";
import {HttpErrorHandler} from "../../helpers/http-error-handler";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {LoadingBarService} from "@ngx-loading-bar/core";
import {j4care} from "../../helpers/j4care.service";
import {KeycloakService} from "../../helpers/keycloak-service/keycloak.service";

@Component({
  selector: 'app-storage-commitment',
  templateUrl: './storage-commitment.component.html'
})
export class StorageCommitmentComponent implements OnInit {
    matches = [];
    // user: User;
    exporters;
    exporterID;
    exportTasks = [];
    Object = Object;
    filters = {
        ExporterID: undefined,
        offset: undefined,
        limit: 20,
        status: '*',
        StudyUID: undefined,
        updatedBefore: undefined,
        dicomDeviceName: undefined
    };
    dialogRef: MatDialogRef<any>;
    _ = _;
    filterSchema = [];
    constructor(
        public cfpLoadingBar: LoadingBarService,
        public mainservice: AppService,
        public  service: StorageCommitmentService,
        public viewContainerRef: ViewContainerRef,
        public dialog: MatDialog,
        public httpErrorHandler:HttpErrorHandler
    ) {}
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
        this.initExporters(2);
    };
    filterKeyUp(e){
        let code = (e.keyCode ? e.keyCode : e.which);
        if (code === 13){
            this.search(0);
        }
    };
    confirm(confirmparameters){
        //this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(ConfirmComponent, {
            height: 'auto',
            width: '500px'
        });
        this.dialogRef.componentInstance.parameters = confirmparameters;
        return this.dialogRef.afterClosed();
    };
    search(offset) {
        let $this = this;
        $this.cfpLoadingBar.start();
        this.service.search(this.filters, offset)

            .subscribe((res) => {
                if (res && res.length > 0){
                    if(this.filters.limit < res.length){
                        res.pop();
                    }
                    $this.matches = res.map((properties, index) => {
                        if (_.hasIn(properties, 'Modality')){
                            properties.Modality = properties.Modality.join(',');
                        }
                        return {
                            offset: offset + index,
                            properties: properties,
                            showProperties: false
                        };
                    });
                    $this.cfpLoadingBar.complete();
                }else{
                    $this.cfpLoadingBar.complete();
                    $this.matches = [];
                    this.mainservice.showMsg($localize `:@@no_tasks_found:No tasks found!`)
                }
            }, (err) => {
                $this.cfpLoadingBar.complete();
                $this.matches = [];
                $this.httpErrorHandler.handleError(err);
            });
    };
    getDifferenceTime(starttime, endtime){
        let start = new Date(starttime).getTime();
        let end = new Date(endtime).getTime();
        if (!start || !end || end < start){
            return null;
        }else{
            return this.msToTime(new Date(endtime).getTime() - new Date(starttime).getTime());
        }
    };
    msToTime(duration) {
        if (duration > 999){
            let milliseconds: any = parseInt((((duration % 1000))).toString())
                , seconds: any = parseInt(((duration / 1000) % 60).toString())
                , minutes: any = parseInt(((duration / (1000 * 60)) % 60).toString())
                , hours: any = parseInt(((duration / (1000 * 60 * 60))).toString());
            if (hours === 0){
                if (minutes === 0){
                    return seconds.toString() + '.' + milliseconds.toString() + $localize `:@@storage-commitment._sec: sec`;
                }else{
                    seconds = (seconds < 10) ? '0' + seconds : seconds;
                    return minutes.toString() + ':' + seconds.toString() + '.' + milliseconds.toString() + $localize `:@@storage-commitment._min: min`;
                }
            }else{

                hours = (hours < 10) ? '0' + hours : hours;
                minutes = (minutes < 10) ? '0' + minutes : minutes;
                seconds = (seconds < 10) ? '0' + seconds : seconds;

                return hours.toString() + ':' + minutes.toString() + ':' + seconds.toString() + '.' + milliseconds.toString() + $localize `:@@storage-commitment._h: h`;
            }
        }else{
            return duration.toString() + $localize `:@@storage-commitment._ms: ms`;
        }
    }
    flushBefore() {
        let select: any = [
            {
                title: $localize `:@@PENDING:PENDING`,
                value: 'PENDING',
                label: $localize `:@@PENDING:PENDING`
            },
            {
                title: $localize `:@@COMPLETED:COMPLETED`,
                value: 'COMPLETED',
                label: $localize `:@@COMPLETED:COMPLETED`
            },
            {
                title: $localize `:@@WARNING:WARNING`,
                value: 'WARNING',
                label: $localize `:@@WARNING:WARNING`
            },
            {
                title: $localize `:@@FAILED:FAILED`,
                value: 'FAILED',
                label: $localize `:@@FAILED:FAILED`
            }
        ];

        let parameters: any = {
            content: $localize `:@@select_before_date_and_status_to_delete_all_storage_commitments:Select before date and status to delete all storage commitments`,
            select: select,
            date: {
                placeholder: $localize `:@@updated_before:Updated before`,
                format: 'yyyy-MM-dd'
            },
            result: {
                select: 'PENDING',
                date: undefined
            },
            saveButton: $localize `:@@DELETE:DELETE`,
            saveButtonClass: 'btn-danger'
        };
        let $this = this;
        this.confirm(parameters).subscribe(result => {
            if (result){
                $this.cfpLoadingBar.start();
                if (parameters.result.date === undefined){
                    $this.mainservice.showError($localize `:@@updated_before_not_set:"Updated before"-date was not set`);
                }else{
                    let resultDate = j4care.extractDateTimeFromString(parameters.result.date).firstDateTime;
                    if( resultDate && resultDate.dateObject){
                        let formattedDate = j4care.formatDate(resultDate.dateObject,'yyyy-MM-dd')
                        this.service.flush(parameters.result.select, formattedDate)
                            .subscribe((res) => {
                                console.log('resflush', res);
                                $this.mainservice.showMsg($localize `:@@queues_deleted:${res.deleted}:deleted: queues deleted successfully!`);
                                $this.search(0);
                                $this.cfpLoadingBar.complete();
                            }, (err) => {
                                $this.httpErrorHandler.handleError(err);
                            });
                    }
                }
            }
        });
    };
    delete(match){
        let $this = this;
        let parameters: any = {
            content: $localize `:@@delete_task_question:Are you sure you want to delete this task?`,
            result: 'Ok',
            saveButton: $localize `:@@DELETE:DELETE`,
            saveButtonClass: 'btn-danger'
        };
        this.confirm(parameters).subscribe(result => {
            if (result){
                $this.cfpLoadingBar.start();
                this.service.delete(match.properties.transactionUID)
                    .subscribe(
                        (res) => {
                            // match.properties.status = 'CANCELED';
                            $this.cfpLoadingBar.complete();
                            $this.search(0);
                            this.mainservice.showMsg($localize `:@@task_deleted:Task deleted successfully!`)
                        },
                        (err) => {
                            $this.cfpLoadingBar.complete();
                            console.log('cancleerr', err);
                            $this.httpErrorHandler.handleError(err);
                        });
            }
        });
    }
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

    initExporters(retries) {
        this.service.getExporters()
            .subscribe(
                (res) => {
                    this.exporters = res;
                    this.filterSchema = this.service.getFiltersSchema(this.exporters);
                    if (res && res[0] && res[0].id){
                        this.exporterID = res[0].id;
                    }
                },
                (res) => {
                    if (retries)
                        this.initExporters(retries - 1);
                });
    }
}
