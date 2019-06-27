import {Component, OnInit, ViewContainerRef} from '@angular/core';
import {User} from '../../models/user';
import {MatDialogRef, MatDialog, MatDialogConfig} from '@angular/material';
import {Http} from '@angular/http';
import {AppService} from '../../app.service';
import * as _ from 'lodash';
import {ConfirmComponent} from '../../widgets/dialogs/confirm/confirm.component';
import {StorageSystemsService} from './storage-systems.service';
import {WindowRefService} from "../../helpers/window-ref.service";
import {HttpErrorHandler} from "../../helpers/http-error-handler";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {j4care} from "../../helpers/j4care.service";
import {LoadingBarService} from "@ngx-loading-bar/core";
import {environment} from "../../../environments/environment";
import {KeycloakService} from "../../helpers/keycloak-service/keycloak.service";

@Component({
  selector: 'app-storage-systems',
  templateUrl: './storage-systems.component.html'
})
export class StorageSystemsComponent implements OnInit {
    matches = [];
    user: User;
    exporterID;
    exportTasks = [];
    filters = {
        offset: undefined,
        uriScheme: undefined,
        dicomAETitle: undefined,
        usage: undefined,
        usableSpaceBelow: undefined,
        usableSpaceBelowMode:"GB"
    };
    isRole: any;
    dialogRef: MatDialogRef<any>;
    _ = _;
    aets;
    // usableSpaceBelow;
    // usableSpaceBelowMode = "GB";
    Object = Object;
    filterSchema = [];
    constructor(
        public $http:J4careHttpService,
        public cfpLoadingBar: LoadingBarService,
        public mainservice: AppService,
        public  service: StorageSystemsService,
        public viewContainerRef: ViewContainerRef,
        public dialog: MatDialog,
        public config: MatDialogConfig,
        public httpErrorHandler:HttpErrorHandler
    ){}
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
        // this.initExporters(1);
        // this.init();
        this.getAets();
    };
    filterKeyUp(e){
        let code = (e.keyCode ? e.keyCode : e.which);
        if (code === 13){
            this.search(0);
        }
    };
    confirm(confirmparameters){
        this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(ConfirmComponent, {
            height: 'auto',
            width: '500px'
        });
        this.dialogRef.componentInstance.parameters = confirmparameters;
        return this.dialogRef.afterClosed();
    };
    calculateUsableSpaceBelowFilter(filters){
        if(filters.usableSpaceBelow){
            switch(filters.usableSpaceBelowMode) {
                case "TB":
                    filters.usableSpaceBelow = filters.usableSpaceBelow * 1000000000000;
                    break;
                case "GB":
                    filters.usableSpaceBelow = filters.usableSpaceBelow * 1000000000;
                    break;
                case "MB":
                    filters.usableSpaceBelow = filters.usableSpaceBelow * 1000000;
                    break;
            }
        }
        return filters;
    }
    search(offset) {
        let $this = this;
        $this.cfpLoadingBar.start();
        let filters = Object.assign({},this.filters);
        filters = this.calculateUsableSpaceBelowFilter(filters);
        delete filters.usableSpaceBelowMode;
        this.service.search(filters, offset)
            .subscribe((res) => {
/*                if(!environment.production){
                    res = [{"dcmStorageID":"fs1","dcmURI":"file:///storage/fs1/","dcmDigestAlgorithm":"MD5","dcmInstanceAvailability":"ONLINE","dcmProperty":["pathFormat={now,date,yyyy/MM/dd}/{0020000D,hash}/{0020000E,hash}/{00080018,hash}","checkMountFile=NO_MOUNT"],"usableSpace":341985411072,"totalSpace":412715171840},{"dcmStorageID":"nfscache1","dcmURI":"file:///storage/archive1tln-cache","dcmDigestAlgorithm":"MD5","dcmInstanceAvailability":"ONLINE","deleterThreshold":[{"":1000000000000}],"dcmExportStorageID":"s3-data","dcmProperty":["pathFormat={now,date,yyyy/MM/dd}/{0020000D,hash}/{0020000E,hash}/{00080018,hash}","checkMountFile=NO_MOUNT"],"dicomAETitle":["ARCHIVE1TLN"],"dcmStorageClusterID":"nfscachetln","usages":["dcmObjectStorageID"],"usableSpace":2458130055168,"totalSpace":7612746563584},{"dcmStorageID":"nfscache2","dcmURI":"file:///storage/archive2tln-cache","dcmDigestAlgorithm":"MD5","dcmInstanceAvailability":"ONLINE","dcmReadOnly":true,"dcmProperty":["pathFormat={now,date,yyyy/MM/dd}/{0020000D,hash}/{0020000E,hash}/{00080018,hash}","checkMountFile=NO_MOUNT"],"dcmStorageClusterID":"nfscachetln","usableSpace":2458130055168,"totalSpace":7612746563584},{"dcmStorageID":"nfscache3","dcmURI":"file:///storage/archive3tln-cache","dcmDigestAlgorithm":"MD5","dcmInstanceAvailability":"ONLINE","dcmReadOnly":true,"dcmProperty":["pathFormat={now,date,yyyy/MM/dd}/{0020000D,hash}/{0020000E,hash}/{00080018,hash}","checkMountFile=NO_MOUNT"],"dcmStorageClusterID":"nfscachetln","usableSpace":2458130055168,"totalSpace":7612746563584},{"dcmStorageID":"nfscache4","dcmURI":"file:///storage/archive4tln-cache","dcmDigestAlgorithm":"MD5","dcmInstanceAvailability":"ONLINE","dcmReadOnly":true,"dcmProperty":["pathFormat={now,date,yyyy/MM/dd}/{0020000D,hash}/{0020000E,hash}/{00080018,hash}","checkMountFile=NO_MOUNT"],"dcmStorageClusterID":"nfscachetln","usableSpace":2458130055168,"totalSpace":7612746563584},{"dcmStorageID":"s3-data","dcmURI":"jclouds:s3:http://ecscls1tln.vna.pacs.ee:9020","dcmDigestAlgorithm":"MD5","dcmInstanceAvailability":"NEARLINE","dcmRetrieveCacheStorageID":"nfscache1","dcmRetrieveCacheMaxParallel":20,"dcmProperty":["container=archive","jclouds.s3.virtual-host-buckets=false","credential=tzJaK0XSBYRfZcGRCAbktjwnM8WYCisqbPeemJH8","identity=lives3.service","pathFormat={now,date,yyyy/MM/dd}/{0020000D,hash}/{0020000E,hash}/{00080018,hash}","jclouds.strip-expect-header=true","containerExists=true","jclouds.relax-hostname=true","jclouds.trust-all-certs=true"],"dicomAETitle":["ARCHIVE1TLN_S3"],"usages":["dcmObjectStorageID"],"usableSpace":-1,"totalSpace":-1},{"dcmStorageID":"s3-metadata","dcmURI":"jclouds:s3:http://ecscls1tln.vna.pacs.ee:9020","dcmInstanceAvailability":"ONLINE","dcmReadOnly":true,"dcmProperty":["container=metadata","jclouds.s3.virtual-host-buckets=false","credential=tzJaK0XSBYRfZcGRCAbktjwnM8WYCisqbPeemJH8","identity=lives3.service","pathFormat={now,date,yyyy/MM/dd}/{0020000D}/{0020000E}/{now,date,yyyyMMddHHmmss}.zip","jclouds.strip-expect-header=true","containerExists=true","jclouds.relax-hostname=true","jclouds.trust-all-certs=true"],"usages":["dcmSeriesMetadataStorageID"],"usableSpace":-1,"totalSpace":-1}];
                }*/
                if (res && res.length > 0){
                    $this.matches = res.map((properties, index) => {
/*                        if(_.hasIn(properties,'dicomAETitle')){
                            properties.dicomAETitle = properties.dicomAETitle.join(' | ');
                        }*/
                        if(
                            properties.dcmNoDeletionConstraint ||
                            (
                                j4care.isSetInObject(properties, 'deleterThreshold') &&
                                (
                                    j4care.isSetInObject(properties, 'dcmExportStorageID') || j4care.isSetInObject(properties, 'dcmExternalRetrieveAET')
                                )
                            )
                        ){
                            properties.noDeleter = true;
                        }
                        if(_.hasIn(properties, 'deleterThreshold') && _.hasIn(properties, 'usableSpace')){
                            let deleterThreshold;
                            properties.deleterThreshold.map((deleter, i) => {
                                deleterThreshold = _.values(deleter)[0];
                            });
                            if(deleterThreshold && (deleterThreshold > properties.usableSpace)){
                                properties.warning = true;
                            }
                        }
                        if (_.hasIn(properties, 'deleterThreshold')){
                            properties.deleterThresholdProcent = properties.deleterThreshold.map((deleter, i) => {
                                return (Math.round(((parseInt(<string>_.values(deleter)[0]) * 100) / properties.totalSpace) *100)/100).toFixed(2);
                            });
                            properties.deleterThreshold = properties.deleterThreshold.map((deleter, i) => {
                                if (_.keys(deleter)[0] != ''){
                                    return _.keys(deleter)[0] + ':' + $this.convertBtoGBorMB(_.values(deleter)[0]);
                                }else{
                                    return $this.convertBtoGBorMB(_.values(deleter)[0]);
                                }
                            });
                        }
                        if(_.hasIn(properties, 'usableSpace') && _.hasIn(properties, 'totalSpace')){
                            properties.usedSpace = (Math.round((((properties.totalSpace-properties.usableSpace)*100)/properties.totalSpace) * 100)/100).toFixed(2);
/*                            if(properties.usedSpace){
                                properties.usedSpace += ' %';
                            }*/
                        }
                        if (_.hasIn(properties, 'usableSpace')){
                            properties.usableSpace = $this.convertBtoGBorMB(properties.usableSpace);
                        }
                        if (_.hasIn(properties, 'totalSpace')){
                            properties.totalSpace = $this.convertBtoGBorMB(properties.totalSpace);
                        }
                        _.forEach(properties, (l, k) => {
                            if (_.isObject(l)){
                                properties[k] = l.join(' | ');
                            }
                        });

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
                    $this.mainservice.setMessage({
                        'title': 'Info',
                        'text': 'No storages found!',
                        'status': 'info'
                    });
                }
            }, (err) => {
                $this.cfpLoadingBar.complete();
                $this.matches = [];
                $this.httpErrorHandler.handleError(err);
            });
    };
    convertBtoGBorMB(value){
        return j4care.convertBtoHumanReadable(value);
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
    msToTime(duration) {

        if (duration > 999){

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
        }
    }
    flushBefore() {
        let select: any = [
            {
                title: 'PENDING',
                value: 'PENDING',
                label: 'PENDING'
            },
            {
                title: 'COMPLETED',
                value: 'COMPLETED',
                label: 'COMPLETED'
            },
            {
                title: 'WARNING',
                value: 'WARNING',
                label: 'WARNING'
            },
            {
                title: 'FAILED',
                value: 'FAILED',
                label: 'FAILED'
            }
        ];

        let parameters: any = {
            content: 'Select before date and status to delete all storage commitments',
            select: select,
            date: {
                placeholder: 'Updated before',
                format: 'yy-mm-dd'
            },
            result: {
                select: 'PENDING',
                date: undefined
            },
            saveButton: 'DELETE',
            saveButtonClass: 'btn-danger'
        };
        let $this = this;
        // let beforeDate = datePipeEn.transform(this.before,'yyyy-mm-dd');
        // console.log("beforeDate",beforeDate);
        this.confirm(parameters).subscribe(result => {
            if (result){
                // console.log("parametersdate",datePipeEn.transform(parameters.result.date,'yyyy-mm-dd'));
                $this.cfpLoadingBar.start();
                if (parameters.result.date === undefined){
                    $this.mainservice.setMessage({
                        'title': 'Error',
                        'text': '\'Updated before\'-date was not set',
                        'status': 'error'
                    });
                }else{

                    this.service.flush(parameters.result.select, parameters.result.date)
                        // .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res; }catch (e){ resjson = [];} return resjson;})
                        .subscribe((res) => {
                            console.log('resflush', res);
                            $this.mainservice.setMessage({
                                'title': 'Info',
                                'text': res.deleted + ' queues deleted successfully!',
                                'status': 'info'
                            });
                            $this.search(0);
                            $this.cfpLoadingBar.complete();
                        }, (err) => {
                            $this.httpErrorHandler.handleError(err);
                        });
                }
            }
        });
    };
    delete(match){
        let $this = this;
        let parameters: any = {
            content: 'Are you sure you want to delete this task?',
            result: 'Ok',
            saveButton: 'DELETE',
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
    getAets(){
        this.$http.get('../aets')
            .map(res => j4care.redirectOnAuthResponse(res))
            .subscribe((response) => {
                this.aets = j4care.extendAetObjectWithAlias(response);
                this.filterSchema = this.service.getFiltersSchema(this.aets);
            }, (err) => {
                console.log('error getting aets', err);
            });
    }

    /*    init() {
     let $this = this;
     $this.cfpLoadingBar.start();
     this.$http.get("../monitor/export")
     .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res; }catch (e){ resjson = [];} return resjson;})
     .subscribe((res) => {
     $this.exportTasks = res;
     // $this.queueName = res[0].name;
     $this.cfpLoadingBar.complete();
     })
     }*/
/*    initExporters(retries) {
        let $this = this;
        this.$http.get("../storage")
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res; }catch (e){ resjson = [];} return resjson;})
            .subscribe(
                (res) => {
                    console.log("res",res);
                    console.log("exporters",$this.exporters);
                    $this.exporters = res;
                    console.log("exporters2",$this.exporters);
                    if(res && res[0] && res[0].id){
                        $this.exporterID = res[0].id;
                    }
                    // $this.mainservice.setGlobal({exporterID:$this.exporterID});
                },
                (res) => {
                    if (retries)
                        this.initExporters(retries-1);
                });
    }*/
}
