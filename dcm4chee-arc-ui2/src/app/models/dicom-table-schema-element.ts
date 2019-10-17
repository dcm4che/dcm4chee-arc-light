import {
    ActionsMenu,
    DynamicPipe,
    TableAction, TableSchemaElementType,
} from "../helpers/dicom-studies-table/dicom-studies-table.interfaces";



export class TableSchemaElement {
    type:TableSchemaElementType;
    header?:string;
    headerDescription?:string;
    description?:string;
    title?:string;
    pathToValue?:string;/*Path or key of the value how you can find it on the data model*/
    widthWeight?:number;/*width weight of this table element in compare to the others*/
    cssClass?:string;
    hook?:Function; /*Use this to modify the data*/
    actions?:TableAction[];
    calculatedWidth?:string;
    pipe?:DynamicPipe;
    pxWidth?:number;
    menu?:ActionsMenu;
    showIf:Function;
    private _elementId:string;
    showBorder?:boolean;
    showBorderPath?:string;

    constructor(
        options:{
            type?:TableSchemaElementType,
            header?:string,
            headerDescription?:string,
            pathToValue?:string,
            widthWeight?:number,
            cssClass?:string,
            hook?:Function,
            actions?:TableAction[],
            calculatedWidth?:string,
            pxWidth?:number
            pipe?:DynamicPipe,
            title?:string,
            menu?:ActionsMenu,
            description?:string,
            showIf?:Function,
            showBorder?:boolean,
            showBorderPath?:string
        } = {}
    ){
        this.type = options.type;
        this.header = options.header;
        this.headerDescription = options.headerDescription;
        this.pathToValue = options.pathToValue;
        this.widthWeight = options.widthWeight;
        this.cssClass = options.cssClass;
        this.hook = options.hook;
        this.actions = options.actions;
        this.calculatedWidth = options.calculatedWidth;
        this.pipe = options.pipe;
        this.pxWidth = options.pxWidth;
        this.title = options.title;
        this.menu = options.menu;
        this.description = options.description;
        this.showIf = options.showIf;
        this.showBorder = options.showBorder;
        this.showBorderPath = options.showBorderPath;

        this.calculateElementID();
    }

    private calculateElementID(){
        let id = '';
        [
            "type",
            "header",
            "headerDescription",
            "pathToValue"
        ].forEach(key=>{
            id += `${this[key]? (this[key]).replace(/([ \[\]\"\'\.])/mg,'') : ''}`;  //Remove some maybe problematic characters from the passed string and concat the string if not empty
        });
        this._elementId = id
    }

    get elementId(): string {
        return this._elementId;
    }
}