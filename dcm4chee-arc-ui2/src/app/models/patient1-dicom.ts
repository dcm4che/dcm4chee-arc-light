import {Study1Dicom} from "./study1-dicom";
import {MwlDicom} from "./mwl-dicom";
import {MppsDicom} from "./mpps-dicom";
import {DiffDicom} from "./diff-dicom";
import {UwlDicom} from "./uwl-dicom";

export class Patient1Dicom {
    private _attrs:any[];
    private _studies:Study1Dicom[];
    private _showAttributes:boolean;
    private _showStudies:boolean;
    private _offset:number;
    private _selected;
    constructor(attrs:any[], studies:Study1Dicom[], showAttributes?:boolean, showStudies?:boolean, offset?:number){
        this._attrs = attrs;
        this._studies = studies;
        this._showAttributes = showAttributes || false;
        this._showStudies = showStudies || false;
        this._offset = offset || 0;
    }


    get attrs(): any[] {
        return this._attrs;
    }

    set attrs(value: any[]) {
        this._attrs = value;
    }

    get studies(): Study1Dicom[] {
        return this._studies;
    }

    set studies(value: Study1Dicom[]) {
        this._studies = value;
    }

    get showAttributes(): boolean {
        return this._showAttributes;
    }

    set showAttributes(value: boolean) {
        this._showAttributes = value;
    }

    get showStudies(): boolean {
        return this._showStudies;
    }

    set showStudies(value: boolean) {
        this._showStudies = value;
    }

    get offset(): number {
        return this._offset;
    }

    set offset(value: number) {
        this._offset = value;
    }

    get selected() {
        return this._selected;
    }

    set selected(value) {
        this._selected = value;
    }
}
