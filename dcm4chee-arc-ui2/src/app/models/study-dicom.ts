import {PatientDicom} from "./patient-dicom";
import {SeriesDicom} from "./series-dicom";

export class StudyDicom {
    private _patient:PatientDicom;
    private _offset:number;
    private _hasMore:boolean;
    private _attrs:any[];
    private _series:SeriesDicom[];
    private _showAttributes:boolean;
    private _fromAllStudies:boolean;
    private _showSeries:boolean;
    private _selected:boolean;
    private _showPaginations:boolean;

    constructor(
        attrs:any[],
        patient:PatientDicom,
        offset?:number,
        hasMore?:boolean,
        showPaginations?:boolean,
        series?:SeriesDicom[],
        showAttributes?:boolean,
        fromAllStudies?:boolean,
        selected?:boolean,
        showSeries?:boolean,
    ){
        this._attrs = attrs;
        this._patient = patient;
        this._offset = offset || 0;
        this._hasMore = hasMore || false;
        this._showPaginations = showPaginations || false;
        this._series = series || null;
        this._showAttributes = showAttributes || false;
        this._fromAllStudies = fromAllStudies || false;
        this._selected = selected || false;
        this._showSeries = showSeries || false;
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

    get hasMore(): boolean {
        return this._hasMore;
    }

    set hasMore(value: boolean) {
        this._hasMore = value;
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

    get showSeries(): boolean {
        return this._showSeries;
    }

    set showSeries(value: boolean) {
        this._showSeries = value;
    }

    get selected(): boolean {
        return this._selected;
    }

    set selected(value: boolean) {
        this._selected = value;
    }

    get showPaginations(): boolean {
        return this._showPaginations;
    }

    set showPaginations(value: boolean) {
        this._showPaginations = value;
    }
}
