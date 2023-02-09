import {StudyDicom} from "./study-dicom";
import {MwlDicom} from "./mwl-dicom";
import {MppsDicom} from "./mpps-dicom";
import {DiffDicom} from "./diff-dicom";
import {UwlDicom} from "./uwl-dicom";

export class PatientDicom {
    private _attrs:any[];
    private _studies:StudyDicom[];
    private _mwls:MwlDicom[];
    private _mpps:MppsDicom[];
    private _uwls:UwlDicom[];
    private _diffs:DiffDicom[];
    private _showAttributes:boolean;
    private _showStudies:boolean;
    private _showMwls:boolean;
    private _showMpps:boolean;
    private _showUwls:boolean;
    private _showDiffs:boolean;
    private _offset:number;
    private _selected;
    constructor(attrs:any[], studies:StudyDicom[], showAttributes?:boolean, showStudies?:boolean, offset?:number,
                mwls?:MwlDicom[], showMwls?:boolean, diffs?:DiffDicom[], showDiffs?:boolean,
                uwls?:UwlDicom[], showUwls?:boolean, mpps?:MppsDicom[], showMpps?:boolean){
        this._attrs = attrs;
        this._studies = studies;
        this._mwls = mwls;
        this._mpps = mpps;
        this._uwls = uwls;
        this._diffs = diffs;
        this._showAttributes = showAttributes || false;
        this._showStudies = showStudies || false;
        this._showMwls = showMwls || false;
        this._showMpps = showMpps || false;
        this._showDiffs = showDiffs || false;
        this._offset = offset || 0;
        this._showUwls = showUwls;
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

    get mpps(): MppsDicom[] {
        return this._mpps;
    }

    set mpps(value: MppsDicom[]) {
        this._mpps = value;
    }

    get uwls(): UwlDicom[] {
        return this._uwls;
    }

    set uwls(value: UwlDicom[]) {
        this._uwls = value;
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

    get showMpps(): boolean {
        return this._showMpps;
    }

    set showMpps(value: boolean) {
        this._showMpps = value;
    }

    get showUwls(): boolean {
        return this._showUwls;
    }

    set showUwls(value: boolean) {
        this._showUwls = value;
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
export const PATIENT_NAME_PARTS = [
    "FAMILY-NAME",
    "GIVEN-NAME",
    "MIDDLE-NAME",
    "NAME-PREFIX",
    "NAME-SUFFIX",
    "I_FAMILY-NAME",
    "I_GIVEN-NAME",
    "I_MIDDLE-NAME",
    "I_NAME-PREFIX",
    "I_NAME-SUFFIX",
    "P_FAMILY-NAME",
    "P_GIVEN-NAME",
    "P_MIDDLE-NAME",
    "P_NAME-PREFIX",
    "P_NAME-SUFFIX"
];