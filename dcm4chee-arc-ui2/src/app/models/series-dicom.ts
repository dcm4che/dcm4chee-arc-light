import {StudyDicom} from "./study-dicom";
import {InstanceDicom} from "./instance-dicom";

export class SeriesDicom {
    private _study:StudyDicom;
    private _offset:number;
    private _attrs:any[];
    private _instances:InstanceDicom[];
    private _moreInstances:boolean;
    private _showAttributes:boolean;
    private _selected:boolean;
    private _showInstances:boolean;
    private _hasMore:boolean;
    private _showPaginations:boolean;

    constructor(
        study:StudyDicom,
        attrs:any[],
        offset?:number,
        hasMore?:boolean,
        showPaginations?:boolean,
        instances?:InstanceDicom[],
        moreInstances?:boolean,
        showAttributes?:boolean,
        selected?:boolean,
        showInstances?:boolean,
    ){
        this._study = study;
        this._attrs = attrs;
        this._offset = offset || 0;
        this._hasMore = hasMore || false;
        this._showPaginations = showPaginations || false;
        this._showInstances = showInstances || false;
        this._instances = instances;
        this._moreInstances = moreInstances || false;
        this._showAttributes = showAttributes || false;
        this._selected = selected || false;
        this._showInstances = showInstances || false;
    }

    get study(): StudyDicom {
        return this._study;
    }

    set study(value: StudyDicom) {
        this._study = value;
    }

    get offset(): number {
        return this._offset;
    }

    set offset(value: number) {
        this._offset = value;
    }

    get attrs(): any[] {
        return this._attrs;
    }

    set attrs(value: any[]) {
        this._attrs = value;
    }

    get hasMore(): boolean {
        return this._hasMore;
    }

    set hasMore(value: boolean) {
        this._hasMore = value;
    }

    get showPaginations(): boolean {
        return this._showPaginations;
    }

    set showPaginations(value: boolean) {
        this._showPaginations = value;
    }

    get instances(): InstanceDicom[] {
        return this._instances;
    }

    set instances(value: InstanceDicom[]) {
        this._instances = value;
    }

    get moreInstances(): boolean {
        return this._moreInstances;
    }

    set moreInstances(value: boolean) {
        this._moreInstances = value;
    }

    get showAttributes(): boolean {
        return this._showAttributes;
    }

    set showAttributes(value: boolean) {
        this._showAttributes = value;
    }

    get selected(): boolean {
        return this._selected;
    }

    set selected(value: boolean) {
        this._selected = value;
    }

    get showInstances(): boolean {
        return this._showInstances;
    }

    set showInstances(value: boolean) {
        this._showInstances = value;
    }
}
