import {
    ActionsMenu,
    DynamicPipe,
    TableAction, TableSchemaElementType,
} from "../helpers/dicom-studies-table/dicom-studies-table.interfaces";



export class TableSchemaElement {
    private _type:TableSchemaElementType;
    private _header?:string;
    private _headerDescription?:string;
    description?:string;
    title?:string;
    private _pathToValue?:string;/*Path or key of the value how you can find it on the data model*/
    widthWeight?:number;/*width weight of this table element in compare to the others*/
    cssClass?:string;
    hook?:Function; /*Use this to modify the data*/
    hoverHook?:Function; /*Use this to modify the data*/
    actions?:TableAction[];
    headerActions?:TableAction[];
    calculatedWidth?:string;
    pipe?:DynamicPipe;
    pxWidth?:number;
    menu?:ActionsMenu;
    showIf:Function;
    private _elementId:string;
    showBorder?:boolean;
    showBorderPath?:string;
    saveTheOriginalValueOnTooltip?:boolean;

    constructor(
        options:{
            type?:TableSchemaElementType,
            header?:string,
            headerDescription?:string,
            pathToValue?:string,
            widthWeight?:number,
            cssClass?:string,
            hook?:Function,
            hoverHook?:Function,
            actions?:TableAction[],
            headerActions?:TableAction[],
            calculatedWidth?:string,
            pxWidth?:number
            pipe?:DynamicPipe,
            title?:string,
            menu?:ActionsMenu,
            description?:string,
            showIf?:Function,
            showBorder?:boolean,
            showBorderPath?:string,
            saveTheOriginalValueOnTooltip?:boolean
        } = {}
    ){
        this._type = options.type;
        this._header = options.header;
        this._headerDescription = options.headerDescription;
        this._pathToValue = options.pathToValue;
        this.widthWeight = options.widthWeight;
        this.cssClass = options.cssClass;
        this.hook = options.hook;
        this.hoverHook = options.hoverHook;
        this.actions = options.actions;
        this.headerActions = options.headerActions;
        this.calculatedWidth = options.calculatedWidth;
        this.pipe = options.pipe;
        this.pxWidth = options.pxWidth;
        this.title = options.title;
        this.menu = options.menu;
        this.description = options.description;
        this.showIf = options.showIf;
        this.showBorder = options.showBorder;
        this.showBorderPath = options.showBorderPath;
        this.saveTheOriginalValueOnTooltip = options.saveTheOriginalValueOnTooltip;

        this.calculateElementID();
    }

    private calculateElementID(){
        let id = '';
        [
            "_type",
            "_header",
            "_headerDescription",
            "_pathToValue"
        ].forEach(key=>{
            if(typeof this[key] === "string"){
                id += `${this[key]? (this[key]).replace(/([ \[\]\"\'\.])/mg,'') : ''}`;  //Remove some maybe problematic characters from the passed string and concat the string if not empty
            }
        });
        this._elementId = id
    }

    get elementId(): string {
        return this._elementId;
    }


    set type(value: TableSchemaElementType) {
        this._type = value;
        this.calculateElementID();
    }

    get type(): TableSchemaElementType {
        return this._type;
    }

    set header(value: string) {
        this._header = value;
        this.calculateElementID();
    }

    get header(): string {
        return this._header;
    }

    set headerDescription(value: string) {
        this._headerDescription = value;
        this.calculateElementID();
    }

    get headerDescription(): string {
        return this._headerDescription;
    }

    set pathToValue(value: string) {
        this._pathToValue = value;
        this.calculateElementID();
    }

    get pathToValue(): string {
        return this._pathToValue;
    }
}