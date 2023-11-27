import {Aet} from "./models/aet";
import {Device} from "./models/device";
import {DcmWebApp} from "./models/dcm-web-app";
import {HttpHeaders} from "@angular/common/http";
import {SelectionsDicomObjects} from "./study/study/selections-dicom-objects.model";
import {DicomTableSchema, TableAction} from "./helpers/dicom-studies-table/dicom-studies-table.interfaces";

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

export type DateTimeFormatMode = "dateFormat" | "timeFormat" | "dateTimeFormat";

export interface RangeObject {
    firstDateTime:J4careDateTime;
    secondDateTime:J4careDateTime;
    mode:J4careDateTimeMode;
}

export interface ConfiguredDateTameFormatObject{
    dateFormat:string;
    timeFormat:string;
    dateTimeFormat:string;
}

export type StatisticsPage = "simple"|"detailed"

export type FilterTag = "button" | "input" | "checkbox" | "select" | "modality" | "range-picker-limit" | "range-picker-time" | "range-picker" | "code-selector" | "issuer-selector" | "p-calendar" | "multi-select" | "html-select" | "editable-select" | "editable-multi-select" | "label" | "label_large" | "dummy" | "combined" | "number" | "size_range_picker" | "modified-widget" | "person-name-picker";

export type RangeUnit = "hour" | "day" | "week" | "month" | "year";

export class SelectDropdown<T,S={}> {
    private _value:string;
    private _text:string;
    private _label:any;
    private _title?:string;
    private _htmlLabel:string;
    private _wholeObject:T;
    private _selected;
    private _description:string;
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

    get description(): string {
        return this._description;
    }

    set description(value: string) {
        this._description = value;
    }
}
export type Quantity = "count"|"size"|string;
export type StudyDateMode = "StudyReceiveDateTime"|"StudyDate"|string;
export type FilterSchema = FilterSchemaElement[];
export interface Code{
    key:string;
    label:string;
}

export interface Issuer{
    key:string;
    label:string;
}

export interface FilterSchemaElement {
    tag:FilterTag;
    filterKey?:string;
    type?:"text"|"number";
    iodFileNames?:string[]; // Used in the modified-widget to select the modified attributes
    text?:string;
    id?:string;
    description?:string;
    placeholder?:string;
    showStar?:boolean;
    maxSelectedLabels?:number;
    showSearchField?:boolean;
    showSelectedEmptyValue?:boolean;
    min?:number,
    max?:number,
    title?:string,
    onlyDate?:boolean,
    options?:SelectDropdown<any>[],
    optionsTree?:OptionsTree[],
    firstField?:FilterSchemaElement,
    secondField?:FilterSchemaElement,
    convert?:Function;
    disabled?:boolean;
    cssClass?:string;
    showRefreshIcon?:boolean;
    showDynamicLoader?:boolean;
    codes?:Code[];
    issuers?:Issuer[];
}

export type DicomMode = "study" | "patient" | "series" | "mwl" | "mpps" | "uwl" | "diff" | "export" | "thumbnail" | string;
export type StudyTab = "study" | "patient" | "mwl" | "uwl" | "diff" | "mpps";

export type DicomLevel = "patient" | "study" | "series" | "instance" | "diff" | "mwl" | string;
export type AccessLocation = "internal" | "external";
export type PaginationDirection = "prev" | "next";
export interface StudyFilterConfig {
    filterSchemaEntry?:{schema:FilterSchema,lineLength:number};
    filterSchemaMain:{schema:FilterSchema,lineLength:number};
    filterSchemaExpand?:{schema:FilterSchema,lineLength:number};
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

export type DicomResponseType = 'object'|'count'|'size'|'csv';

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

export type HttpMethod = "get"|"head"|"post"|"put"|"delete"|"option";

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

export type SelectionAction = "copy" | "move" | "merge" | "cut" | "link";

export interface DiffAttributeSet {
    actions: string;
    description: string;
    groupButtons: string;
    id: string;
    title: string;
    type: string;
}

export interface StorageSystems {
    dcmStorageID:string;
}

export type AccessControlIDMode = "level_access_control_id" | "update_access_control_id_to_matching"|"update_access_control_id_to_selections";

export interface OptionsTree {
    label?:string;
    options:SelectDropdown<any>[];
}
export interface LanguageObject {
    code:string;
    name:string;
    nativeName:string;
    flag:string;
}

export interface LanguageConfig{
    dcmuiLanguageConfigName:string;
    dcmLanguages:string[];
    dcmuiLanguageProfileObjects:LanguageProfile[]
}
export interface LanguageProfile{
    dcmuiLanguageProfileName:string;
    dcmDefaultLanguage:string;
    dcmuiLanguageProfileRole:string[];
    dcmuiLanguageProfileUsername:string;
}

export interface LocalLanguageObject{
    language:LanguageObject,
    username:string
}

export type UPSModifyMode = "create"|"edit"|"clone"|"subscribe";
export type UPSSubscribeType = "ups"|"uwl";

export interface ModifyConfig {
    saveLabel:string;
    titleLabel:string;
}

export class TimeRange{
    from:Date;
    to:Date;
    constructor(from?:Date, to?:Date){
        this.from = from;
        this.to = to;
    }
}

export interface StudyTagConfig {
    tab:StudyTab;
    title:string;
    takeActionsOver?:string[]; //Array of the permissions id strings, if empty no actions button will be taken over
    addActions?:AddActions;
    hookSchema?:(schema:DicomTableSchema, $this:any)=>DicomTableSchema;
    searchPatientAfterNoMwl?:boolean;
    tableMode?:StudyTab;
    hidePageArrows?:boolean;
    cssClass?:string;
    hideEmptyActionMenu?:boolean;
    presetFilter?:any;
    preventClearingSelected?:boolean;
}
type AddActions = {
    addPath:string,
    addFunction:(actions:Function, $this:any, currentActions:TableAction[], schema?:DicomTableSchema) => TableAction[]
};

export interface CreateDialogTemplate{
    dcmTag: string[];
    dcmuiDialog: string;
    dicomDescription: string;
    dcmuiTemplateName: string;
}