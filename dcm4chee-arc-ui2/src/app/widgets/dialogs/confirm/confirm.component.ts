import {Component, AfterViewChecked} from '@angular/core';
import {MdDialogRef} from "@angular/material";
import * as _ from "lodash";

@Component({
    selector: 'app-confirm',
    templateUrl: './confirm.component.html',
    styles: [`
        .vex-theme-os.confirm{
            width:500px;
            
        }
    `]
})
export class ConfirmComponent{
    _ = _;

    private _parameters;
    constructor(public dialogRef: MdDialogRef<ConfirmComponent>) {
    }
    get parameters() {
        return this._parameters;
    }

    set parameters(value) {
        this._parameters = value;
    }
    dialogKeyHandler(e, dialogRef){
        let code = (e.keyCode ? e.keyCode : e.which);
        if(code === 13){
            dialogRef.close('ok');
        }
        if(code === 27){
            dialogRef.close(null);
        }
    }

}
