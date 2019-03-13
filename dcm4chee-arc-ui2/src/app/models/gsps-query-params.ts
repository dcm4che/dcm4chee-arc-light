export class GSPSQueryParams{
    private _studyUID:string;
    private _seriesUID:string;
    private _objectUID:string;
    private _contentType:string;
    private _frameNumber:number;
    private _presentationSeriesUID:string;
    private _presentationUID:string;

    constructor(
        studyUID:string,
        seriesUID:string,
        objectUID:string,
        contentType:string,
        frameNumber:number,
        presentationSeriesUID:string,
        presentationUID:string
    ){
        this._studyUID = studyUID;
        this._seriesUID = seriesUID;
        this._objectUID = objectUID;
        this._contentType = contentType;
        this._frameNumber = frameNumber;
        this._presentationSeriesUID = presentationSeriesUID;
        this._presentationUID = presentationUID;
    }
    get studyUID(): string {
        return this._studyUID;
    }

    set studyUID(value: string) {
        this._studyUID = value;
    }

    get seriesUID(): string {
        return this._seriesUID;
    }

    set seriesUID(value: string) {
        this._seriesUID = value;
    }

    get objectUID(): string {
        return this._objectUID;
    }

    set objectUID(value: string) {
        this._objectUID = value;
    }

    get contentType(): string {
        return this._contentType;
    }

    set contentType(value: string) {
        this._contentType = value;
    }

    get frameNumber(): number {
        return this._frameNumber;
    }

    set frameNumber(value: number) {
        this._frameNumber = value;
    }

    get presentationSeriesUID(): string {
        return this._presentationSeriesUID;
    }

    set presentationSeriesUID(value: string) {
        this._presentationSeriesUID = value;
    }

    get presentationUID(): string {
        return this._presentationUID;
    }

    set presentationUID(value: string) {
        this._presentationUID = value;
    }
}