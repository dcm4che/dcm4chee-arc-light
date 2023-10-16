import { Component } from '@angular/core';
import { MatLegacyDialogRef as MatDialogRef } from '@angular/material/legacy-dialog';

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
