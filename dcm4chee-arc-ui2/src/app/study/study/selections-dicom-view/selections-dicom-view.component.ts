import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {SelectionsDicomObjects} from "../selections-dicom-objects.model";
import {DicomLevel} from "../../../interfaces";
import {TableSchemaElement} from "../../../models/dicom-table-schema-element";
import {SelectionsDicomViewService} from "./selections-dicom-view.service";
import * as _ from "lodash";
import {j4care} from "../../../helpers/j4care.service";

@Component({
  selector: 'selections-dicom-view',
  templateUrl: './selections-dicom-view.component.html',
  styleUrls: ['./selections-dicom-view.component.scss']
})
export class SelectionsDicomViewComponent implements OnInit {

    @Input() dicomLevel:DicomLevel;

    @Input() hideHeader:boolean;

    private _selectionsDicomObjects:SelectionsDicomObjects;
    Object = Object;
    _ = _;
    dicomObject = [];

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
        private service:SelectionsDicomViewService
    ) { }

    ngOnInit() {
        this.tableSchema = j4care.calculateWidthOfTable(this.service.getTableSchema(this, this.actions, this.dicomLevel));
    }
    actions(model){
        console.log("model",model);
        this.onRemoveFromSelection.emit(model);
    }
}
