import { Component } from '@angular/core';
import {MatDialogRef} from '@angular/material';

@Component({
  selector: 'app-clone-selector',
  templateUrl: './clone-selector.component.html'
})
export class CloneSelectorComponent {
    selectedOption;
    private _toCloneElement;

    constructor(public dialogRef: MatDialogRef<CloneSelectorComponent>) { }

    get toCloneElement() {
        return this._toCloneElement;
    }

    set toCloneElement(value) {
        this._toCloneElement = value;
    }
}
