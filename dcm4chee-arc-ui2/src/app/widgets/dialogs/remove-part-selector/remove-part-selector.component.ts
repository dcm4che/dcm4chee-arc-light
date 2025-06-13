import { Component } from '@angular/core';
import {MatDialogRef} from "@angular/material/dialog";
import {MatOption, MatSelect} from '@angular/material/select';
import {FormsModule} from '@angular/forms';
import {CommonModule} from '@angular/common';
//import { MatLegacyDialogRef as MatDialogRef } from '@angular/material/legacy-dialog';

@Component({
    selector: 'app-remove-part-selector',
    templateUrl: './remove-part-selector.component.html',
    imports: [
        MatSelect,
        FormsModule,
        MatOption,
        CommonModule
    ],
    standalone: true
})
export class RemovePartSelectorComponent{
    selectedOption;
    private _toRemoveElement;

    constructor(public dialogRef: MatDialogRef<RemovePartSelectorComponent>) { }

    get toRemoveElement() {
        return this._toRemoveElement;
    }

    set toRemoveElement(value) {
        this._toRemoveElement = value;
    }
}
