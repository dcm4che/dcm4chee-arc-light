import {Component, Input, OnInit} from '@angular/core';
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
    private _selectionsDicomObjects:SelectionsDicomObjects;
    Object = Object;
    _ = _;
    dicomLevels = [
        "patient",
        "study",
        "series",
        "instance"
    ];
    dicomObject = {};

    tableSchema:TableSchemaElement[];
    get v(): SelectionsDicomObjects {
        return this._selectionsDicomObjects;
    }

    @Input()
    set selectionsDicomObjects(value: SelectionsDicomObjects) {
        this.dicomLevels.forEach((dicomLevel:DicomLevel)=>{
            if(value[dicomLevel] && Object.keys(value[dicomLevel]).length > 0){
                this.dicomObject[dicomLevel] = Object.keys(value[dicomLevel]).map(key=>{
                    return value[dicomLevel][key].object.attrs;
                });
            }
        });
        this._selectionsDicomObjects = value;
    }

    constructor(
        private service:SelectionsDicomViewService
    ) { }

    ngOnInit() {
        this.tableSchema = j4care.calculateWidthOfTable(this.service.getTableSchema(this.dicomLevel));
    }

}
