import {Component, OnInit, ViewContainerRef} from '@angular/core';
import {Http} from '@angular/http';
import {QueuesService} from './queues.service';
import {AppService} from '../../app.service';
import {User} from '../../models/user';
import {ConfirmComponent} from '../../widgets/dialogs/confirm/confirm.component';
import {SlimLoadingBarService} from 'ng2-slim-loading-bar';
import {MdDialogRef, MdDialog, MdDialogConfig} from '@angular/material';
import {DatePipe} from '@angular/common';
import * as _ from 'lodash';
import {WindowRefService} from "../../helpers/window-ref.service";
import {HttpErrorHandler} from "../../helpers/http-error-handler";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {errorHandler} from "@angular/platform-browser/src/browser";

@Component({
  selector: 'app-queues',
  templateUrl: './queues.component.html'
})
export class QueuesComponent implements OnInit{
    matches = [];
    limit = 20;
    queues = [];
    queueName = null;
    dicomDeviceName = null;
    status = '*';
    before;
    isRole: any = (user)=>{return false;};
    user: User;
    dialogRef: MdDialogRef<any>;
    _ = _;

    constructor(
        public $http:J4careHttpService,
        public service: QueuesService,
        public mainservice: AppService,
        public cfpLoadingBar: SlimLoadingBarService,
        public viewContainerRef: ViewContainerRef,
        public dialog: MdDialog,
        public config: MdDialogConfig,
        private httpErrorHandler:HttpErrorHandler
    ) {};
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
        this.initQuery();
        this.before = new Date();
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
    }
    filterKeyUp(e){
        let code = (e.keyCode ? e.keyCode : e.which);
        if (code === 13){
            this.search(0);
        }
    };
    search(offset) {
        let $this = this;
        $this.cfpLoadingBar.start();
        this.service.search(this.queueName, this.status, offset, this.limit, this.dicomDeviceName)
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
            .subscribe((res) => {
                if (res && res.length > 0){
                    $this.matches = res.map((properties, index) => {
                        $this.cfpLoadingBar.complete();
                        return {
                            offset: offset + index,
                            properties: properties,
                            showProperties: false
                        };
                    });
                }else{
                    $this.matches = [];
                    $this.cfpLoadingBar.complete();
                    $this.mainservice.setMessage({
                        'title': 'Info',
                        'text': 'No tasks found!',
                        'status': 'info'
                    });
                }
            }, (err) => {
                console.log('err', err);
                $this.matches = [];
            });
    };
    scrollToDialog(){
        let counter = 0;
        let i = setInterval(function(){
            if (($('.md-overlay-pane').length > 0)) {
                clearInterval(i);
                $('html, body').animate({
                    scrollTop: ($('.md-overlay-pane').offset().top)
                }, 200);
            }
            if (counter > 200){
                clearInterval(i);
            }else{
                counter++;
            }
        }, 50);
    }
    confirm(confirmparameters){
        this.scrollToDialog();
        this.config.viewContainerRef = this.viewContainerRef;
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
        this.service.cancel(this.queueName, match.properties.id)
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
        $this.cfpLoadingBar.start();
        this.service.reschedule(this.queueName, match.properties.id)
            .subscribe((res) => {
                $this.search(0);
                $this.cfpLoadingBar.complete();
            }, (err) => {
                $this.cfpLoadingBar.complete();
                $this.httpErrorHandler.handleError(err);
            });
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
            content: 'Are you sure you want to delete?'
        }).subscribe(result => {
            if (result){
                $this.cfpLoadingBar.start();

                this.service.delete(this.queueName, match.properties.id)
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
            content: `Are you sure you want to ${mode} selected entries?`
        }).subscribe(result => {
            if (result){
                this.cfpLoadingBar.start();
                this.matches.forEach((match)=>{
                    if(match.checked){
                        this.service[mode](this.queueName, match.properties.id)
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
    flushBefore() {
        let $this = this;
        let datePipeEn = new DatePipe('us-US');
        let beforeDate = datePipeEn.transform(this.before, 'yyyy-mm-dd');
        console.log('beforeDate', beforeDate);
        console.log('this.status', this.status);
        let parameters = {
            content: 'Flush with this configuration:<br>- Before: ' + beforeDate + '<br>- In queue:"' + this.getQueueDescriptionFromName(this.queueName) + '"<br>- With status:' + this.status + (this.dicomDeviceName ? '<br>- Device:' + this.dicomDeviceName:''),
            result: 'ok',
            noForm: true,
            saveButton: 'Flush',
            saveButtonClass: 'btn-danger'
        };
        this.confirm(parameters).subscribe(result => {
            console.log('result', result);
            if (result){
                $this.cfpLoadingBar.start();
                this.service.flush(this.queueName, this.status, this.before, this.dicomDeviceName)
                    .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
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
                        $this.cfpLoadingBar.complete();
                        $this.httpErrorHandler.handleError(err);
                    });
            }
        });
    };
    hasOlder(objs) {
        return objs && (objs.length === this.limit);
    };
    hasNewer(objs) {
        return objs && objs.length && objs[0].offset;
    };
    newerOffset(objs) {
        return Math.max(0, objs[0].offset - this.limit);
    };
    olderOffset(objs) {
        return objs[0].offset + this.limit;
    };
    initQuery() {
        let $this = this;
        $this.cfpLoadingBar.start();
        this.$http.get('../queue')
            .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
            .subscribe((res) => {
            $this.queues = res;
            $this.queueName = res[0].name;
            $this.cfpLoadingBar.complete();
        });
    }
}
