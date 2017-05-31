import { Component } from '@angular/core';
import {MdDialogRef} from '@angular/material';

@Component({
  selector: 'app-remove-part-selector',
  templateUrl: './remove-part-selector.component.html'
})
export class RemovePartSelectorComponent{
    selectedOption;
    private _toRemoveElement;

    constructor(public dialogRef: MdDialogRef<RemovePartSelectorComponent>) { }

    get toRemoveElement() {
        return this._toRemoveElement;
    }

    set toRemoveElement(value) {
        this._toRemoveElement = value;
    }
}
