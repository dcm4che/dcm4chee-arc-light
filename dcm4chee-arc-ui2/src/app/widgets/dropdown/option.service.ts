import {Injectable, Input, Output} from "@angular/core";
import {Subject} from "rxjs/Subject";
import {SelectDropdown} from "../../interfaces";


@Injectable()
export class OptionService {


    private _currentStateOfTheValue;
    private setValueSource = new Subject<{id:string, value:SelectDropdown<any>}>();

    valueSet$ = this.setValueSource.asObservable();
    setValue(object:{id:string ,value:SelectDropdown<any>}) {
        console.log('in set value', object);
        this._currentStateOfTheValue[object.id] = object.value;
        this.setValueSource.next(object);
    }


    get currentStateOfTheValue() {
        return this._currentStateOfTheValue;
    }

    set currentStateOfTheValue(value) {
        this._currentStateOfTheValue = value;
    }
    get
}