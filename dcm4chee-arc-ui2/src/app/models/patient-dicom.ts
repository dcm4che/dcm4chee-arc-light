import {StudyDicom} from "./study-dicom";
import {MwlDicom} from "./mwl-dicom";
import {DiffDicom} from "./diff-dicom";

export class PatientDicom {
    private _attrs:any[];
    private _studies:StudyDicom[];
    private _mwls:MwlDicom[];
    private _diffs:DiffDicom[];
    private _showAttributes:boolean;
    private _showStudies:boolean;
    private _showMwls:boolean;
    private _showDiffs:boolean;
    private _offset:number;
    private _selected;
    constructor(attrs:any[], studies:StudyDicom[], showAttributes?:boolean, showStudies?:boolean, offset?:number, mwls?:MwlDicom[], showMwls?:boolean, diffs?:DiffDicom[], showDiffs?:boolean){
        this._attrs = attrs;
        this._studies = studies;
        this._mwls = mwls;
        this._diffs = diffs;
        this._showAttributes = showAttributes || false;
        this._showStudies = showStudies || false;
        this._showMwls = showMwls || false;
        this._showDiffs = showDiffs || false;
        this._offset = offset || 0;
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

    get mwls(): MwlDicom[] {
        return this._mwls;
    }

    set mwls(value: MwlDicom[]) {
        this._mwls = value;
    }

    get diffs(): DiffDicom[] {
        return this._diffs;
    }

    set diffs(value: DiffDicom[]) {
        this._diffs = value;
    }

    get showMwls(): boolean {
        return this._showMwls;
    }

    set showMwls(value: boolean) {
        this._showMwls = value;
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


    get showDiffs(): boolean {
        return this._showDiffs;
    }

    set showDiffs(value: boolean) {
        this._showDiffs = value;
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
