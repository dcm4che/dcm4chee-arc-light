import {Aet} from "./models/aet";
import {Device} from "./models/device";
import {DcmWebApp} from "./models/dcm-web-app";

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

export type FilterTag = "button"|"input"|"checkbox"|"select"|"modality"|"range-picker-limit"|"range-picker-time"|"range-picker" | "p-calendar" |"multi-select"| "html-select"|"label"|"label_large"|"dummy"|"combined"|"number"|"size_range_picker";

export type RangeUnit = "hour" | "day" | "week" | "month" | "year";

export class SelectDropdown {
    private _value:string;
    private _text:string;
    private _label:any;
    private _title?:string;
    private _htmlLabel:string;
    private _wholeObject:any;
    constructor(value:any,text:string, title?:string, label?:any, htmlLabel?:string, wholeObject?:any){
        this._value = value;
        this._text = text || value;
        this._label = label || text || value;
        this._title = title;
        this._htmlLabel = htmlLabel;
        this._wholeObject = wholeObject;
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

    get wholeObject(): any {
        return this._wholeObject;
    }

    set wholeObject(value: any) {
        this._wholeObject = value;
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
    min?:number,
    title?:string,
    options?:SelectDropdown[],
    firstField?:FilterSchemaElement,
    secondField?:FilterSchemaElement,
    convert?:Function;
    cssClass?:string;
}

export type DicomMode = "study" | "patient" | "mwl" | "diff";
export type AccessLocation = "internal" | "external";

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

export interface StudyPageConfig {
    tab:DicomMode;
    accessLocation:AccessLocation;
}

export type DicomResponseType = 'object'|'count'|'size';

export class StudyDevice {
    private _devices:any[];
    private _selectedDevice?:Device;
    private _selectedAet?:Aet;
    private _selectedWebApp?:DcmWebApp;


    constructor(
        devices:any[],
        selectedDevice?:Device,
        selectedAet?:Aet,
        selectedWebApp?:DcmWebApp
    ){
        this.devices = devices;
        this.selectedDevice = selectedDevice || undefined;
        this.selectedAet = selectedAet || undefined;
        this.selectedWebApp = selectedWebApp || undefined;

    }
    get devices(): any[] {
        return this._devices;
    }

    set devices(value: any[]) {
        this._devices = value;
        this._selectedDevice = undefined;
        this._selectedAet = undefined;
        this._selectedWebApp = undefined;
    }

    get selectedDevice(): Device {
        return this._selectedDevice;
    }

    set selectedDevice(value: Device) {
        this._selectedDevice = value;
        this._selectedAet = undefined;
        this._selectedWebApp = undefined;
    }

    get selectedAet(): Aet {
        return this._selectedAet;
    }

    set selectedAet(value: Aet) {
        this._selectedAet = value;
        this._selectedWebApp = undefined;
    }

    get selectedWebApp(): DcmWebApp {
        return this._selectedWebApp;
    }

    set selectedWebApp(value: DcmWebApp) {
        this._selectedWebApp = value;
        this._selectedAet = undefined;
    }
}