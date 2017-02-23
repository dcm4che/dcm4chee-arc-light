import { Component } from '@angular/core';
import {MdDialogRef} from "@angular/material";

@Component({
    selector: 'app-confirm',
    templateUrl: './confirm.component.html',
    styles: [`
        .vex-theme-os.confirm{
            width:500px;
            
        }
    `]
})
export class ConfirmComponent {

    private _parameters;
    constructor(public dialogRef: MdDialogRef<ConfirmComponent>) {

    }
    get parameters() {
        return this._parameters;
    }

    set parameters(value) {
        this._parameters = value;
    }
}
