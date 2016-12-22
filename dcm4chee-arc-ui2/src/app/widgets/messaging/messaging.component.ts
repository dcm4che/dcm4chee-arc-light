import {Component, OnDestroy} from '@angular/core';
import {AppService} from "../../app.service";
import {Subscribable} from "rxjs/Observable";
import {Subscription} from "rxjs";
@Component({
  selector: 'app-messaging',
  template: `
    <div class="msg_container" *ngIf="msg && msg.length > 0">
        <li *ngFor="let m of msg" class="{{m.status}} msg_{{m.id}} slideInRight animated"  (click)="closeBox(m)">
            <span class="close" data-dismiss="alert" aria-label="close">&times;</span>  
            <h4>{{m.title}}</h4>
            <p [innerHtml]="m.text"></p>
            <div class="progress"></div>
        </li>
    </div>
  `
})
export class MessagingComponent implements OnDestroy{
    private msgTimeout = 10000;
    public msg:Array<any> = [];
    subscription:Subscription;

    constructor(private mainservice:AppService){
        this.subscription = this.mainservice.messageSet$.subscribe(msg => {
            console.log("msg in subscribe messagecomponent ",msg);
            this.setMsg(msg);
        });
    }
    setMsg(msg:any){
        let timeout = msg.timeout || this.msgTimeout;
        let isInArray = false;
        let presentId = "";
        if(this.msg && this.msg.length > 0){
            this.msg.forEach((k, i) => {
                if (k.text === msg.text && k.status === msg.status) {
                    presentId = k.id;
                    isInArray = true;
                }
            });
        }
        if (isInArray) { //If the same message is already in the array, then just put the class pulse (To simulate a pulse) and remove it again
            $(".msg_" + presentId).removeClass("slideInRight").addClass('pulse');
            setTimeout(function() {
                $(".msg_" + presentId).removeClass("pulse");
            }, 500);
        } else {
            var id = this.getUniqueRandomId();
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
        let cssClass = ".msg_" + id;
        let x = 0;
        let $this = this;
        let interval = setInterval(function() {
            $(cssClass).find(".progress").css("width", (x * 10000 / timeout) + "%");
            if (x === (timeout / 100)) {
                    clearInterval(interval);
                $(".msg_container li." + "msg_" + id).fadeOut("400", function() {
                    $this.removeMsgFromArray(id);
                });
            }
            x++;
        }, 100);
    };

    closeBox(m){
        // this.msg.finde(m);
        console.log("m.id",m.id);
        console.log("m.text",m.text);
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
            if (buffer === 0 && isAvailable === true) {
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
