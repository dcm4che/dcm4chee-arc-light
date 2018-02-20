import {Component, OnDestroy} from '@angular/core';
import {AppService} from '../../app.service';
import {Subscription} from 'rxjs';
import {InfoComponent} from '../dialogs/info/info.component';
import {MatDialogRef, MatDialog, MatDialogConfig} from '@angular/material';
@Component({
  selector: 'app-messaging',
  template: `
    <div class="msg_container" *ngIf="msg && msg.length > 0">
        <li *ngFor="let m of msg" class="{{m.status}} msg_{{m.id}} slideInRight animated"  (click)="closeBox(m)">
            <span class="close" data-dismiss="alert" aria-label="close">&times;</span>  
            <h4>{{m.title}}</h4>
            <p *ngIf="!m.detailError" [innerHtml]="m.text"></p>
            <p *ngIf="m.detailError">
                {{m.text}}<br>
                <a *ngIf="m.detailError" class="more" (click)="$event.preventDefault();alert(m)">more..</a>
            </p>
            <div class="progress"></div>
        </li>
    </div>
  `
})
export class MessagingComponent implements OnDestroy{
    private msgTimeout = 10000;
    public msg: Array<any> = [];
    subscription: Subscription;

    dialogRef: MatDialogRef<any>;
    constructor(
        private mainservice: AppService,
        public dialog: MatDialog,
        public config: MatDialogConfig
    ){
        this.subscription = this.mainservice.messageSet$.subscribe(msg => {
            console.log('msg in subscribe messagecomponent ', msg);
            this.setMsg(msg);
        });
    }

    alert(m){
        // alert(m.detailError);
        // this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(InfoComponent, {
            height: 'auto',
            width: '60%'
        });
        this.dialogRef.componentInstance.info = {
            title: 'Error detail',
            content: m.detailError
        };
        this.dialogRef.afterClosed().subscribe();
    }
    setMsg(msg: any){
        let timeout = msg.timeout || this.msgTimeout;
        let isInArray = false;
        let presentId = '';
        if(!msg.title && msg.status)
            msg.title = msg.status.charAt(0).toUpperCase() + msg.status.slice(1);
        if(!msg.status)
            msg.status = 'info';
        if(!msg.status && !msg.title){
            msg.title = "Info";
            msg.status = 'info';
        }
        if (this.msg && this.msg.length > 0){
            this.msg.forEach((k, i) => {
                if (k.text === msg.text && k.status === msg.status) {
                    presentId = k.id;
                    isInArray = true;
                }
            });
        }
        if (isInArray) { //If the same message is already in the array, then just put the class pulse (To simulate a pulse) and remove it again
            $('.msg_' + presentId).removeClass('slideInRight').addClass('pulse');
            setTimeout(function() {
                $('.msg_' + presentId).removeClass('pulse');
            }, 500);
        } else {
            let id = this.getUniqueRandomId();
            msg.id = id;
            this.msg.push(msg);
            this.msgCounter(id, timeout);
        }
    }

    private removeMsgFromArray(id) {
        this.msg.forEach((m, k) => {
            if (m.id == id) {
                this.msg.splice(k, 1);
            }
        });
    };

    private msgCounter(id, timeout) {
        let cssClass = '.msg_' + id;
        let x = 0;
        let $this = this;
        let interval = setInterval(function() {
            $(cssClass).find('.progress').css('width', (x * 10000 / timeout) + '%');
            if (x === (timeout / 100)) {
                    clearInterval(interval);
                $('.msg_container li.' + 'msg_' + id).fadeOut('400', function() {
                    $this.removeMsgFromArray(id);
                });
            }
            x++;
        }, 100);
    };

    closeBox(m){
        this.removeMsgFromArray(m.id);
    }
    private getUniqueRandomId() {
        if (this.msg && this.msg[0]) { //If there is no message in the array just create some rendom number
            let buffer = 15; //Create a security to prevent infinite loop
            let isAvailable = false; //Check parameter to see if some message has alredy the new id
            let id = 0;

            while (!isAvailable && buffer > 0) {
                id = Math.floor((Math.random() * 100) + 1); //Render int between 1 and 100
                this.msg.forEach((k, i) => {
                    if (k.id === id) {
                        isAvailable = true;
                    }
                });
                buffer--;
            }
            if (buffer === 0 && isAvailable && isAvailable === true) {
                return 999;
            } else {
                return id;
            }
        } else {
            return Math.floor((Math.random() * 100) + 1); //Render int between 1 and 100
        }
    }

    ngOnDestroy() {
        // prevent memory leak when component destroyed
        this.subscription.unsubscribe();
    }
}
