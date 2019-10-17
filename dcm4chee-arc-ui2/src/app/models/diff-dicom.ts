import {PatientDicom} from "./patient-dicom";

export class DiffDicom {
    private _patient:PatientDicom;
    private _offset:number;
    private _hasMore:boolean;
    private _attrs:any[];
    private _showAttributes:boolean;
    private _showPaginations:boolean;
    private _showBorder:boolean;
    private _diffHeaders:any;


    constructor(
        attrs: any[],
        patient: PatientDicom,
        offset: number,
        diffHeaders?: any,
        showBorder?:boolean,
        hasMore?: boolean,
        showPaginations?: boolean,
        showAttributes?: boolean
    ){
        this._patient = patient;
        this._offset = offset || 0;
        this._hasMore = hasMore || false;
        this._showBorder = showBorder || false;
        this._attrs = attrs;
        this._diffHeaders = diffHeaders;
        this._showAttributes = showAttributes || false;
        this._showPaginations = showPaginations || false;
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

    get showAttributes(): boolean {
        return this._showAttributes;
    }

    set showAttributes(value: boolean) {
        this._showAttributes = value;
    }

    get showPaginations(): boolean {
        return this._showPaginations;
    }

    set showPaginations(value: boolean) {
        this._showPaginations = value;
    }

    get showBorder(): boolean {
        return this._showBorder;
    }

    set showBorder(value:boolean) {
        this._showBorder = value;
    }


    get diffHeaders(): any {
        return this._diffHeaders;
    }

    set diffHeaders(value: any) {
        this._diffHeaders = value;
    }
}
