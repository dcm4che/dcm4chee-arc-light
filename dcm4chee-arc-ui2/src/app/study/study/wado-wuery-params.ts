export class WadoQueryParams{
    private _studyUID:string;
    private _seriesUID:string;
    private _objectUID:string;

    constructor(
        studyUID:string,
        seriesUID:string,
        objectUID:string
    ){
        this._studyUID = studyUID;
        this._seriesUID = seriesUID;
        this._objectUID = objectUID;
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
}
