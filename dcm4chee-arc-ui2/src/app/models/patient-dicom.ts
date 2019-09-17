import * as _ from "lodash";
import {StudyDicom} from "./study-dicom";

export class PatientDicom {
    private _attrs:any[];
    private _studies:StudyDicom[];
    private _showAttributes:boolean;
    private _showStudies:boolean;
    private _selected;
    constructor(attrs:any[], studies:StudyDicom[], showAttributes?:boolean, showStudies?:boolean){
        this._attrs = attrs;
        this._studies = studies;
        this._showAttributes = showAttributes || false;
        this._showStudies = showStudies || false;
    }


    get attrs(): any[] {
        return this._attrs;
    }

    set attrs(value: any[]) {
        this._attrs = value;
    }

    get studies(): StudyDicom[] {
        return this._studies;
    }

    set studies(value: StudyDicom[]) {
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


    get selected() {
        return this._selected;
    }

    set selected(value) {
        this._selected = value;
    }
}
