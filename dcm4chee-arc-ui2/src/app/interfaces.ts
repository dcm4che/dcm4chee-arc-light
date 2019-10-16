import {Aet} from "./models/aet";
import {Device} from "./models/device";
import {DcmWebApp} from "./models/dcm-web-app";
import {HttpHeaders} from "@angular/common/http";
import {SelectionsDicomObjects} from "./study/study/selections-dicom-objects.model";

export interface J4careDateTime {
    FullYear:string;
    Month:string;
    Date:string;
    Hours:string;
    Minutes:string;
    Seconds:string;
    dateObject?:Date;
}
export type J4careDateTimeMode = "range" | "leftOpen" | "rightOpen" | "single";

export interface RangeObject {
    firstDateTime:J4careDateTime;
    secondDateTime:J4careDateTime;
    mode:J4careDateTimeMode;
}

export type StatisticsPage = "simple"|"detailed"

export type FilterTag = "button"|"input"|"checkbox"|"select"|"modality"|"range-picker-limit"|"range-picker-time"|"range-picker" | "p-calendar" |"multi-select"| "html-select" | "editable-select" |"label"|"label_large"|"dummy"|"combined"|"number"|"size_range_picker";

export type RangeUnit = "hour" | "day" | "week" | "month" | "year";

export class SelectDropdown<T,S={}> {
    private _value:string;
    private _text:string;
    private _label:any;
    private _title?:string;
    private _htmlLabel:string;
    private _wholeObject:T;
    private _selected;
    constructor(value:any,text:string, title?:string, label?:any, htmlLabel?:string, wholeObject?:T, selected?:boolean){
        this._value = value;
        this._text = text || value;
        this._label = label || text || value;
        this._title = title;
        this._htmlLabel = htmlLabel;
        this._wholeObject = wholeObject;
        this._selected = selected || false;
    }

    get value(): string {
        return this._value;
    }

    set value(value: string) {
        this._value = value;
    }

    get text(): string {
        return this._text;
    }

    set text(value: string) {
        this._text = value;
    }

    get label(): any {
        return this._label;
    }

    set label(value:any) {
        this._label = value;
    }

    get title(): string {
        return this._title;
    }

    set title(value: string) {
        this._title = value;
    }


    get htmlLabel(): string {
        return this._htmlLabel;
    }

    set htmlLabel(value: string) {
        this._htmlLabel = value;
    }

    get wholeObject(): T {
        return this._wholeObject;
    }

    set wholeObject(value: T) {
        this._wholeObject = value;
    }

    get selected() {
        return this._selected;
    }

    set selected(value) {
        this._selected = value;
    }
}
export type Quantity = "count"|"size"|string;
export type StudyDateMode = "StudyReceiveDateTime"|"StudyDate"|string;
export type FilterSchema = FilterSchemaElement[];

export interface FilterSchemaElement {
    tag:FilterTag;
    filterKey?:string;
    type?:"text"|"number";
    text?:string;
    id?:string;
    description?:string;
    placeholder?:string;
    showStar?:boolean;
    maxSelectedLabels?:number;
    showSearchField?:boolean;
    min?:number,
    max?:number,
    title?:string,
    options?:SelectDropdown<any>[],
    firstField?:FilterSchemaElement,
    secondField?:FilterSchemaElement,
    convert?:Function;
    disabled?:boolean;
    cssClass?:string;
    showRefreshIcon?:boolean;
    showDynamicLoader?:boolean;
}



export type DicomMode = "study" | "patient" | "mwl" | "diff" | "export";
export type DicomLevel = "patient" | "study" | "series" | "instance";
export type AccessLocation = "internal" | "external";
export type PaginationDirection = "prev" | "next";
export interface StudyFilterConfig {
    filterSchemaEntry?:{schema:FilterSchema,lineLength:number};
    filterSchemaMain:{schema:FilterSchema,lineLength:number};
    filterSchemaExpand:{schema:FilterSchema,lineLength:number};
    filterEntryModel:any;
    filterModel:any;
    expand:boolean;
    quantityText:{
        count:string,
        size:string
    }
}

export interface SelectedDetailObject{
    uniqueSelectIdObject:UniqueSelectIdObject;
    object:any;
    dicomLevel:DicomLevel;
    requestReady:any;
}

export interface UniqueSelectIdObject {
    id:string;
    idParts:string[];
}
export interface DicomSelectObject {
    idObject?:UniqueSelectIdObject,
    object?:any,
    dicomLevel?:DicomLevel
}
export interface StudyPageConfig {
    tab:DicomMode;
    title:string;
}

export type DicomResponseType = 'object'|'count'|'size';

export interface DcmNetworkConnection{
    dcmBindAddress?:string;
    dcmClientBindAddress?:string;
    dcmProtocol?:string;
}
export class DicomNetworkConnection{
    private _cn:string;
    private _dicomHostname:string;
    private _dicomPort:number;
    private _dcmNetworkConnection:DcmNetworkConnection;
    private _dicomInstalled:boolean;
    private _dicomTLSCipherSuite:string[];

    constructor(
        connection:{
            cn?:string;
            dicomHostname?:string;
            dicomPort?:number;
            dcmNetworkConnection?:DcmNetworkConnection;
            dicomInstalled?:boolean;
            dicomTLSCipherSuite?:string[];
        }={}
    ){
        this._cn = connection.cn;
        this._dicomHostname = connection.dicomHostname;
        this._dicomPort = connection.dicomPort;
        this._dcmNetworkConnection = connection.dcmNetworkConnection;
        this._dicomInstalled = connection.dicomInstalled;
        this._dicomTLSCipherSuite = connection.dicomTLSCipherSuite;
    }
    get cn(): string {
        return this._cn;
    }

    set cn(value: string) {
        this._cn = value;
    }

    get dicomHostname(): string {
        return this._dicomHostname;
    }

    set dicomHostname(value: string) {
        this._dicomHostname = value;
    }

    get dicomPort(): number {
        return this._dicomPort;
    }

    set dicomPort(value: number) {
        this._dicomPort = value;
    }

    get dcmNetworkConnection(): DcmNetworkConnection {
        return this._dcmNetworkConnection;
    }

    set dcmNetworkConnection(value: DcmNetworkConnection) {
        this._dcmNetworkConnection = value;
    }

    get dicomInstalled():boolean {
        return this._dicomInstalled;
    }

    set dicomInstalled(value:boolean) {
        this._dicomInstalled = value;
    }

    get dicomTLSCipherSuite(): string[] {
        return this._dicomTLSCipherSuite;
    }

    set dicomTLSCipherSuite(value: string[]) {
        this._dicomTLSCipherSuite = value;
    }
}

export type HttpMethod = "get"|"head"|"post"|"put"|"delete";

export interface DcmWebAppRequestParam {
    doNotEncode:boolean;
    header:any;
    dcmWebApp:DcmWebApp;
    params:any;
    data:any;
}

export interface MetricsDescriptors{
    dcmMetricsName:string;
    dcmMetricsRetentionPeriod:string;
    dcmUnit:string;
    dicomDescription:string;
}

export type SelectionAction = "copy" | "move" | "merge" | "cut";

export interface DiffAttributeSet {
    actions: string;
    description: string;
    groupButtons: string;
    id: string;
    title: string;
    type: string;
}