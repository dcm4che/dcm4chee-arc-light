import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {SelectionsDicomObjects} from "../selections-dicom-objects.model";
import {DicomLevel} from "../../../interfaces";
import {TableSchemaElement} from "../../../models/dicom-table-schema-element";
import {SelectionsDicomViewService} from "./selections-dicom-view.service";
import * as _ from "lodash-es";
import {j4care} from "../../../helpers/j4care.service";
import {CommonModule, NgStyle, NgSwitch, UpperCasePipe} from '@angular/common';
import {TrimPipe} from '../../../pipes/trim.pipe';
import {DynamicPipePipe} from '../../../pipes/dynamic-pipe.pipe';

@Component({
    selector: 'selections-dicom-view',
    templateUrl: './selections-dicom-view.component.html',
    styleUrls: ['./selections-dicom-view.component.scss'],
    imports: [
        NgStyle,
        NgSwitch,
        DynamicPipePipe,
        UpperCasePipe,
        CommonModule,
    ],
    standalone: true
})
export class SelectionsDicomViewComponent implements OnInit {

    @Input() dicomLevel:DicomLevel;

    @Input() hideHeader:boolean;

    private _selectionsDicomObjects:SelectionsDicomObjects;
    Object = Object;
    _ = _;
    dicomObject = [];
    config;
    tableSchema:TableSchemaElement[];

    @Output() onRemoveFromSelection = new EventEmitter();

    get selectionsDicomObjects(): SelectionsDicomObjects {
        return this._selectionsDicomObjects;
    }

    @Input()
    set selectionsDicomObjects(value: SelectionsDicomObjects) {
        this._selectionsDicomObjects = value;
    }

    constructor(
        private service:SelectionsDicomViewService,
        private dynamicPipe:DynamicPipePipe
    ) { }

    ngOnInit() {
        this.tableSchema = j4care.calculateWidthOfTable(this.service.getTableSchema(this, this.actions, this.dicomLevel));
    }
    actions(model){
        console.log("model",model);
        this.onRemoveFromSelection.emit(model);
    }
    getDynamicPipeValue(object: any, table: TableSchemaElement, tooltipMode?:boolean) {
        return j4care.getDynamicPipeValue(object, table, this.dynamicPipe, tooltipMode)
    }
}
