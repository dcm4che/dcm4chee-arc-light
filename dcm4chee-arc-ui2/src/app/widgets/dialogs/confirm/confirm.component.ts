import {Component, OnDestroy} from '@angular/core';
import { MatLegacyDialogRef as MatDialogRef } from '@angular/material/legacy-dialog';
import * as _ from 'lodash-es';

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
        if(!_.hasIn(value,"cancelButton")){
            value.cancelButton = $localize `:@@CANCEL:CANCEL`;
        }
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
