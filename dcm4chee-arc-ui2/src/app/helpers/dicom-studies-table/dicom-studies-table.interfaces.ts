import {TableSchemaElement} from "../../models/dicom-table-schema-element";

export type TableSchemaElementType = "index"|"actions"|"value"|"pipe"|"actions-menu";

export interface DicomTableSchema{
    patient:TableSchemaElement[];
    studies:TableSchemaElement[];
    series:TableSchemaElement[];
    instance:TableSchemaElement[];
}

/*export interface TableSchemaElement {
    type:TableSchemaElementType;
    header?:string;
    headerDescription?:string;
    pathToValue?:string;/!*Path or key of the value how you can find it on the data model*!/
    widthWeight?:number;/!*width weight of this table element in compare to the others*!/
    cssClass?:string;
    hook?:Function; /!*Use this to modify the data*!/
    actions?:TableAction[];
    calculatedWidth?:string;
    pipe?:DynamicPipe;
}*/

export interface Icon{
    tag:("span"|"i");
    cssClass:string;
    text?:string;
    description?:string;
    showIf?:Function
}

export interface TableAction{
    icon:Icon;
    click:Function;
    title?:string;
}

export interface ActionsMenu{
    toggle:Function;
    actions:TableAction[];
}

export interface TableSchemaConfig {
    cssTableClass?:string;
    cssTdClass?:string;
    cssThClass?:string;
    cssTrClass?:string;
    offset?:number;
}

export class DynamicPipe{

    private _pipeToken:any;
    private _pipeArgs:any[];

    constructor(pipeToken:any, pipeArgs:any[]){
        this._pipeToken = pipeToken;
        this._pipeArgs = pipeArgs;
    }


    get pipeToken(): any {
        return this._pipeToken;
    }

    set pipeToken(value: any) {
        this._pipeToken = value;
    }

    get pipeArgs(): any[] {
        return this._pipeArgs;
    }

    set pipeArgs(value: any[]) {
        this._pipeArgs = value;
    }
}
