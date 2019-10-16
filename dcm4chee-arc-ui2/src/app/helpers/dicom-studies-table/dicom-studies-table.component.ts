import {Component, EventEmitter, HostListener, Input, OnInit, Output} from '@angular/core';
import {DicomTableSchema, TableSchemaConfig} from "./dicom-studies-table.interfaces";
import {PatientDicom} from "../../models/patient-dicom";
import * as _ from "lodash";
import {j4care} from "../j4care.service";
import {StudyWebService} from "../../study/study/study-web-service.model";
import {DicomLevel, PaginationDirection} from "../../interfaces";

@Component({
  selector: 'dicom-studies-table',
  templateUrl: './dicom-studies-table.component.html',
  styleUrls: ['./dicom-studies-table.component.scss']
})
export class DicomStudiesTableComponent implements OnInit {
    _ = _;
    private _tableSchema:DicomTableSchema;
    private _config:TableSchemaConfig;
    @Input() patients:PatientDicom[];
    @Input() title:string;
    @Input() studyWebService:StudyWebService;
    @Output() onPaginationClick = new EventEmitter();
    @Input() searchList;

    hover_mode = 'patient';
    active_td = '';
    showStudyMenu = false;
    constructor() { }
    ngOnInit() {
        this._config.offset = this._config.offset || 0;
    }

    get tableSchema(): DicomTableSchema {
        return this._tableSchema;
    }

    @Input('tableSchema')
    set tableSchema(value: DicomTableSchema) {
        value.patient = j4care.calculateWidthOfTable(value.patient);
        value.studies = j4care.calculateWidthOfTable(value.studies);
        value.series = j4care.calculateWidthOfTable(value.series);
        value.instance = j4care.calculateWidthOfTable(value.instance);
        value.mwl = j4care.calculateWidthOfTable(value.mwl);
        value.diff = j4care.calculateWidthOfTable(value.diff);
        this._tableSchema = value;
    }




    get config(): TableSchemaConfig {
        return this._config;
    }

    @Input('config')
    set config(config: TableSchemaConfig) {
        config.offset = config.offset || 0;
        this._config = config;
    }

    paginationClick(object, level:DicomLevel, direction:PaginationDirection){
        this.onPaginationClick.emit({
            object:object,
            level:level,
            direction:direction
        });
    }

/*    @HostListener('document:keydown', ['$event'])
    handleKeyboardEvent(event: KeyboardEvent) {
        this.pressedKey = event.key;
    }

    @HostListener('document:keyup', ['$event'])
    handleKeyboardEvent(event: KeyboardEvent) {
        this.pressedKey = event.key;
    }*/
    pressedKey;
    @HostListener('document:keydown', ['$event'])
    keyDown(event: KeyboardEvent){
        console.log("e",event)

        console.log("this.presedKey",this.pressedKey);
        this.pressedKey = event.keyCode;
    }
    @HostListener('document:keyup', ['$event'])
    keyUp(event: KeyboardEvent){
        console.log("e",event)

        console.log("this.presedKey",this.pressedKey);
        this.pressedKey = undefined;
    }

    onBlockClick(object){
        console.log("this.presedKey",this.pressedKey);
        if(this.pressedKey === 17){
            object.selected = !object.selected;
        }
    }
}
