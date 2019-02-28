import {PatientDicom} from "./patient-dicom";
import {SeriesDicom} from "./series-dicom";

export class StudyDicom {
    private _patient:PatientDicom;
    private _offset:number;
    private _moreSeries:boolean;
    private _attrs:any[];
    private _series:SeriesDicom[];
    private _showAttributes:boolean;
    private _fromAllStudies:boolean;
    private _selected:boolean;

    constructor(
        attrs:any[],
        patient:PatientDicom,
        offset?:number,
        moreSeries?:boolean,
        series?:SeriesDicom[],
        showAttributes?:boolean,
        fromAllStudies?:boolean,
        selected?:boolean
    ){
        this.attrs = attrs;
        this.patient = patient;
        this.offset = offset || 0;
        this.moreSeries = moreSeries || false;
        this.series = series || null;
        this.showAttributes = showAttributes || false;
        this.fromAllStudies = fromAllStudies || false;
        this.selected = selected || false;
    }


    get patient(): PatientDicom {
        return this._patient;
    }

    set patient(value: PatientDicom) {
        this._patient = value;
    }

    get offset(): number {
        return this._offset;
    }

    set offset(value: number) {
        this._offset = value;
    }

    get moreSeries(): boolean {
        return this._moreSeries;
    }

    set moreSeries(value: boolean) {
        this._moreSeries = value;
    }

    get attrs(): any[] {
        return this._attrs;
    }

    set attrs(value: any[]) {
        this._attrs = value;
    }

    get series(): SeriesDicom[] {
        return this._series;
    }

    set series(value: SeriesDicom[]) {
        this._series = value;
    }

    get showAttributes(): boolean {
        return this._showAttributes;
    }

    set showAttributes(value: boolean) {
        this._showAttributes = value;
    }

    get fromAllStudies(): boolean {
        return this._fromAllStudies;
    }

    set fromAllStudies(value: boolean) {
        this._fromAllStudies = value;
    }

    get selected(): boolean {
        return this._selected;
    }

    set selected(value: boolean) {
        this._selected = value;
    }
}
