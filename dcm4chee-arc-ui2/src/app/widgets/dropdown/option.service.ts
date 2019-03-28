import {Injectable, Input, Output} from "@angular/core";
import {Subject} from "rxjs/Subject";
import {SelectDropdown} from "../../interfaces";


@Injectable()
export class OptionService {


    private _currentStateOfTheValue;
    private setValueSource = new Subject<SelectDropdown>();

    valueSet$ = this.setValueSource.asObservable();
    setValue(value: SelectDropdown) {
        console.log('in set value', value);
        this._currentStateOfTheValue = value;
        this.setValueSource.next(value);
    }


    get currentStateOfTheValue() {
        return this._currentStateOfTheValue;
    }

    set currentStateOfTheValue(value) {
        this._currentStateOfTheValue = value;
    }
}