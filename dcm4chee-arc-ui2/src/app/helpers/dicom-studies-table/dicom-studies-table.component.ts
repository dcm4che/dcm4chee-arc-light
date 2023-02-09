import {Component, EventEmitter, HostListener, Input, OnInit, Output} from '@angular/core';
import {DicomTableSchema, DynamicPipe, TableSchemaConfig} from "./dicom-studies-table.interfaces";
import {PatientDicom} from "../../models/patient-dicom";
import {Patient1Dicom} from "../../models/patient1-dicom";
import * as _ from "lodash-es";
import {j4care} from "../j4care.service";
import {StudyWebService} from "../../study/study/study-web-service.model";
import {DicomLevel, PaginationDirection} from "../../interfaces";
import {DynamicPipePipe} from "../../pipes/dynamic-pipe.pipe";

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
    @Input() patients1:Patient1Dicom[];
    @Input() title:string;
    @Input() studyWebService:StudyWebService;
    @Output() onPaginationClick = new EventEmitter();
    @Input() searchList;

    hover_mode = 'patient';
    active_td = '';
    showStudyMenu = false;
    constructor(
        private dynamicPipe:DynamicPipePipe
    ) { }
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
        value.mpps = j4care.calculateWidthOfTable(value.mpps);
        value.uwl = j4care.calculateWidthOfTable(value.uwl);
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

    getTooltip(object, table) {
        if(table.type === "value"){
            if(table.hook){
                return table.hook(_.get(object.attrs,table.pathToValue), object.attrs, table.pathToValue, object)  ;
            }else{
                return _.get(object.attrs,table.pathToValue)
            }
        }else{
            if(table.type==="pipe" || table.pipe){
                if(table.pathToValue){
                    if(table.saveTheOriginalValueOnTooltip){
                        let extractOriginal = _.get(object.attrs,table.pathToValue);
                        if(extractOriginal && extractOriginal["Alphabetic"]){
                            extractOriginal = extractOriginal["Alphabetic"];
                        }
                        return JSON.stringify({
                            original:extractOriginal,
                            transformed:this.dynamicPipe.transform(_.get(object.attrs,table.pathToValue),table.pipe)
                        })
                    }else{
                        return this.dynamicPipe.transform(_.get(object.attrs,table.pathToValue),table.pipe);
                    }
                }
                return this.dynamicPipe.transform(object.attrs,table.pipe);
            }
        }
    }
}
