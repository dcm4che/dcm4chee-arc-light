import {Component, ElementRef, EventEmitter, Inject, Input, OnInit} from "@angular/core";
import {OptionService} from "./option.service";
import {SelectDropdown} from "../../interfaces";
import * as _ from 'lodash';
import {DropdownComponent} from "./dropdown.component";

@Component({
    selector: 'j4care-option',
    template:`
        <div [hidden]="!showElement" class="option" (click)="select($event)" #options [ngClass]="{'active':selected}" title="{{title || ''}}">
            <div *ngIf="htmlLabel" [innerHTML]="htmlLabel"></div>
            <input type="checkbox" *ngIf="value && value != '' && multiSelectMode" [(ngModel)]="selected">
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
    multiSelectMode:boolean = false;
    selectEvent = new EventEmitter();
    showElement:boolean = true;
    // @Inject(DropdownComponent) private parent: DropdownComponent;
    constructor(
        private parent: ElementRef
    ){
        // this.uniqueId = Math.random().toString(36).substring(2, 15);
    }
    ngOnInit() {
        console.log("parent",this.parent);
/*        if(this.service.currentStateOfTheValue && this.value && _.isEqual(this.service.currentStateOfTheValue.value, this.value)){
            this._selected = true;
        }else{
            this._selected = false;
        }*/
    }

    select(e){
        console.log("parent2",this.parent);
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