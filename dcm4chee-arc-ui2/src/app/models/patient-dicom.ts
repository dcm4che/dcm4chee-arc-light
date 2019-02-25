import * as _ from "lodash";
import {StudyDicom} from "./study-dicom";

export class PatientDicom {
    private _attrs:any[];
    private _studies:StudyDicom[];
    private _showAttributes:boolean;
    constructor(attrs:any[], studies:StudyDicom[], showAttributes?:boolean){
        this._attrs = attrs;
        this._studies = studies;
        this._showAttributes = showAttributes;
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
}
