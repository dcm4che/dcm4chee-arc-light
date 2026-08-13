import {
    ActionsMenu,
    DynamicPipe,
    TableAction, TableSchemaElementType,
} from "../helpers/dicom-studies-table/dicom-studies-table.interfaces";



export class TableSchemaElement {
    private _type?: TableSchemaElementType;
    private _header?: string;
    private _headerDescription?: string;
    description?: string;
    title?: string;
    onClick?:Function;
    private _pathToValue?: string;/*Path or key of the value how you can find it on the data model*/
    private _key?: string;  /*key of the value how you can find it on the data model*/
    widthWeight?: number;/*width weight of this table element in compare to the others*/
    cssClass?: string;
    hook?: Function; /*Use this to modify the data*/
    attributesHook?: Function;
    modifyData?: Function;
    hoverHook?: Function; /*Use this to modify the data*/
    actions?: TableAction[];
    buttons?: any[];
    headerActions?:TableAction[];
    calculatedWidth?:string;
    pipe?:DynamicPipe;
    pxWidth?:number;
    menu?:ActionsMenu;
    showIf:Function;
    private _elementId:string;
    showBorder?:boolean;
    showBorderPath?:string;
    diffMode?: boolean;
    saveTheOriginalValueOnTooltip?:boolean;
    hideTooltip?: boolean;
    search?:string
    constructor(
        options: {
            type?: TableSchemaElementType,
            header?: string,
            headerDescription?: string,
            pathToValue?: string,
            key?: string,
            widthWeight?: number,
            cssClass?: string,
            hook?: Function,
            onClick?: Function,
            buttons?: any[],
            attributesHook?: Function,
            modifyData?: Function,
            hoverHook?: Function,
            actions?: TableAction[],
            headerActions?: TableAction[],
            calculatedWidth?: string,
            pxWidth?: number
            pipe?: DynamicPipe,
            title?:string,
            menu?: ActionsMenu,
            search?:string,
            description?: string,
            showIf?: Function,
            showBorder?: boolean,
            showBorderPath?: string,
            diffMode?: boolean,
            hideTooltip?: boolean,
            saveTheOriginalValueOnTooltip?: boolean
        } = {}
    ){
        this._key = options.key || options.pathToValue;
        this._type = options.type;
        this._header = options.header;
        this._headerDescription = options.headerDescription;
        this._pathToValue = options.pathToValue;
        this.widthWeight = options.widthWeight;
        this.cssClass = options.cssClass;
        this.hook = options.hook;
        this.hoverHook = options.hoverHook;
        this.actions = options.actions;
        this.buttons = options.buttons;
        this.onClick = options.onClick;
        this.headerActions = options.headerActions;
        this.calculatedWidth = options.calculatedWidth;
        this.pipe = options.pipe;
        this.pxWidth = options.pxWidth;
        this.title = options.title;
        this.menu = options.menu;
        this.description = options.description;
        this.showIf = options.showIf;
        this.showBorder = options.showBorder;
        this.hideTooltip = options.hideTooltip;
        this.showBorderPath = options.showBorderPath;
        this.diffMode = options.diffMode || false;
        this.search = options.search;
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
        this._key = value;
        this.calculateElementID();
    }

    get pathToValue(): string {
        return this._pathToValue;
    }

    get key(): string {
        return this._key;
    }

    set key(value: string) {
        this._key = value;
        this._pathToValue = value;
    }
}
