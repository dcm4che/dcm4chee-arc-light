import {SeriesDicom} from "./series-dicom";
import {GSPSQueryParams} from "./gsps-query-params";
import {WadoQueryParams} from "../study/study/wado-wuery-params";

export class InstanceDicom {
    private _series:SeriesDicom;
    private _offset:number;
    private _attrs:any[];
    private _showAttributes:boolean;
    private _showFileAttributes:boolean;
    private _wadoQueryParams:WadoQueryParams;
    private _video:boolean;
    private _image:boolean;
    private _numberOfFrames:number;
    private _gspsQueryParams:GSPSQueryParams[];
    private _views:any[];
    private _view:number;
    private _selected:boolean;
    private _limit:number;
    private _hasMore:boolean;
    private _showPaginations:boolean;

    constructor(
        series:SeriesDicom,
        offset:number,
        attrs:any[],
        wadoQueryParams:WadoQueryParams,
        video:boolean,
        image:boolean,
        numberOfFrames:number,
        gspsQueryParams:GSPSQueryParams[],
        views:any[],
        view:number,
        limit?:number,
        hasMore?:boolean,
        showPaginations?:boolean,
        showAttributes?:boolean,
        showFileAttributes?:boolean,
        selected?:boolean
    ){
        this._series = series;
        this._offset = offset;
        this._attrs = attrs;
        this._wadoQueryParams = wadoQueryParams;
        this._video = video;
        this._image = image;
        this._numberOfFrames = numberOfFrames;
        this._gspsQueryParams = gspsQueryParams;
        this._views = views;
        this._view = view;
        this._showAttributes = showAttributes || false;
        this._showFileAttributes = showFileAttributes || false;
        this._selected = selected || false;
        this._limit = limit || 20;
        this._hasMore = hasMore || false;
        this._showPaginations = showPaginations || false;
    }

    get series(): SeriesDicom {
        return this._series;
    }

    set series(value: SeriesDicom) {
        this._series = value;
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

    get showAttributes(): boolean {
        return this._showAttributes;
    }

    set showAttributes(value: boolean) {
        this._showAttributes = value;
    }

    get showFileAttributes(): boolean {
        return this._showFileAttributes;
    }

    set showFileAttributes(value: boolean) {
        this._showFileAttributes = value;
    }

    get wadoQueryParams(): WadoQueryParams {
        return this._wadoQueryParams;
    }

    set wadoQueryParams(value: WadoQueryParams) {
        this._wadoQueryParams = value;
    }

    get video(): boolean {
        return this._video;
    }

    set video(value: boolean) {
        this._video = value;
    }

    get image(): boolean {
        return this._image;
    }

    set image(value: boolean) {
        this._image = value;
    }

    get numberOfFrames(): number {
        return this._numberOfFrames;
    }

    set numberOfFrames(value: number) {
        this._numberOfFrames = value;
    }

    get gspsQueryParams(): GSPSQueryParams[] {
        return this._gspsQueryParams;
    }

    set gspsQueryParams(value: GSPSQueryParams[]) {
        this._gspsQueryParams = value;
    }

    get views(): any[] {
        return this._views;
    }

    set views(value: any[]) {
        this._views = value;
    }

    get view(): number {
        return this._view;
    }

    set view(value: number) {
        this._view = value;
    }

    get selected(): boolean {
        return this._selected;
    }

    set selected(value: boolean) {
        this._selected = value;
    }

    get limit(): number {
        return this._limit;
    }

    set limit(value: number) {
        this._limit = value;
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
}
