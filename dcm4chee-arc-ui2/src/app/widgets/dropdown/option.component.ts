import {Component, ElementRef, EventEmitter, Input, OnInit} from "@angular/core";
import {OptionService} from "./option.service";
import {SelectDropdown} from "../../interfaces";
import * as _ from 'lodash';

@Component({
    selector: 'j4care-option',
    template:`
        <div class="option" (click)="select($event)" #options [ngClass]="{'active':selected}" title="{{title || ''}}">
            <div *ngIf="htmlLabel" [innerHTML]="htmlLabel"></div>
            <ng-content *ngIf="!htmlLabel">
            </ng-content>
        </div>
    `,
    styles:[`
        .option.active {
            background: #cccccc;
        }
        .option {
            padding: 5px 10px;
        }
        .option:hover {
            cursor: pointer;
            background: rgba(28, 36, 43, 0.1);
        }
    `]
})
export class OptionComponent implements OnInit {
    @Input() value;
    @Input() htmlLabel;
    @Input() title;
    private _selected:boolean = false;
    selectEvent = new EventEmitter();
    constructor(
    ){
        // this.uniqueId = Math.random().toString(36).substring(2, 15);
    }
    ngOnInit() {
/*        if(this.service.currentStateOfTheValue && this.value && _.isEqual(this.service.currentStateOfTheValue.value, this.value)){
            this._selected = true;
        }else{
            this._selected = false;
        }*/
    }

    select(e){
        this.selectEvent.emit(this);
/*        if(this.value){
/!*            if(this.htmlLabel){
                this.service.setValue({
                    id:this.uniqueId,
                    value:new SelectDropdown(this.value,'','','',this.htmlLabel)
                });
            }else{
                this.service.setValue({
                    id:this.uniqueId,
                    value:new SelectDropdown(this.value,this.element.nativeElement.outerText)
                });
            }*!/
        }else{
            this.service.setValue(undefined);
        }*/
    }


    get selected(): boolean {
        return this._selected;
    }

    set selected(value: boolean) {
        this._selected = value;
    }
}