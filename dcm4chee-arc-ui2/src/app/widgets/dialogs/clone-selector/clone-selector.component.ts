import { Component } from '@angular/core';
import {MatDialogRef} from '@angular/material/dialog';
import {MatOption, MatSelect} from '@angular/material/select';
import {FormsModule} from '@angular/forms';

//import { MatLegacyDialogRef as MatDialogRef } from '@angular/material/legacy-dialog';

@Component({
    selector: 'app-clone-selector',
    templateUrl: './clone-selector.component.html',
    imports: [
    MatSelect,
    FormsModule,
    MatOption
],
    standalone: true
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
