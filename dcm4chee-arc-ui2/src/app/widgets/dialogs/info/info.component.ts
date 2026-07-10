import { Component } from '@angular/core';
import {MatDialogContent, MatDialogRef} from '@angular/material/dialog';
//import { MatLegacyDialogRef as MatDialogRef } from '@angular/material/legacy-dialog';

@Component({
    selector: 'app-info',
    templateUrl: './info.component.html',
    imports: [
        MatDialogContent
    ],
    standalone: true
})
export class InfoComponent{

    private _info: any = {
        title: $localize `:@@info:Info`,
        content: undefined
    };
    constructor(public dialogRef: MatDialogRef<InfoComponent>) {

    }

    get info() {
        return this._info;
    }

    set info(value) {
        this._info = value;
    }
}
