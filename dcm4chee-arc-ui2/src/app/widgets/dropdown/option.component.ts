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
    constructor(
        private parent: ElementRef
    ){
    }
    ngOnInit() {
    }

    select(e){
        this.selectEvent.emit(this);
    }


    get selected(): boolean {
        return this._selected;
    }

    set selected(value: boolean) {
        this._selected = value;
    }
}