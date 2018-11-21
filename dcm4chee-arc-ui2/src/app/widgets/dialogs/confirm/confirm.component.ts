import {Component, OnDestroy} from '@angular/core';
import {MatDialogRef} from '@angular/material';
import * as _ from 'lodash';

@Component({
    selector: 'app-confirm',
    templateUrl: './confirm.component.html'
})
export class ConfirmComponent{
    _ = _;

    private _parameters;
    constructor(public dialogRef: MatDialogRef<ConfirmComponent>) {
    }
    get parameters() {
        return this._parameters;
    }

    set parameters(value) {
        this._parameters = value;
    }

    dialogKeyHandler(e, dialogRef){
        let code = (e.keyCode ? e.keyCode : e.which);
        if (code === 13){
            dialogRef.close('ok');
        }
        if (code === 27){
            dialogRef.close(null);
        }
    }
}
