import {Component, ContentChild, ElementRef, EventEmitter, Input, OnInit, Output, ViewChild} from "@angular/core";
import {OptionService} from "./option.service";
import {SelectDropdown} from "../../interfaces";

@Component({
    selector: 'j4care-option',
    template:`
        <div class="option" (click)="select($event)" #options>
            <div *ngIf="htmlLabel" [innerHTML]="htmlLabel"></div>
            <ng-content *ngIf="!htmlLabel">
            </ng-content>
        </div>
    `
})
export class OptionComponent implements OnInit {
    @Input() value;
    @Input() htmlLabel;
    constructor(
        public service:OptionService,
        private element:ElementRef
    ){}
    ngOnInit() {
    }

    select(e){
        console.log("select",e);
        console.log("value",this.value);
        console.log("element",this.element);
        console.log("element",this.element.nativeElement.innerHTML);
        console.log("outerText",this.element.nativeElement.outerText);
        console.log("htmlLabel",this.htmlLabel);
        if(this.htmlLabel){
            this.service.setValue(new SelectDropdown(this.value,'','','',this.htmlLabel));
        }else{
            this.service.setValue(new SelectDropdown(this.value,this.element.nativeElement.outerText));
        }
    }
}