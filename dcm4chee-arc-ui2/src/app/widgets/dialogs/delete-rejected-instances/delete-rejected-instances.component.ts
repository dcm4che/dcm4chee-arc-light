import { Component } from '@angular/core';
import {MatDialogRef} from "@angular/material/dialog";
import {FormsModule} from '@angular/forms';
import {RangePickerComponent} from '../../range-picker/range-picker.component';
import {CommonModule} from '@angular/common';
//import { MatLegacyDialogRef as MatDialogRef } from '@angular/material/legacy-dialog';

@Component({
    selector: 'app-delete-rejected-instances',
    templateUrl: './delete-rejected-instances.component.html',
    styles: [`
    `],
    imports: [
        FormsModule,
        RangePickerComponent,
        CommonModule
    ],
    standalone: true
})
export class DeleteRejectedInstancesComponent{
    private _rjnotes;
    private _results: any;
    constructor(public dialogRef: MatDialogRef<DeleteRejectedInstancesComponent>) { }

    get rjnotes() {
        return this._rjnotes;
    }

    set rjnotes(value) {
        this._rjnotes = value;
    }

    get results() {
        return this._results;
    }

    set results(value) {
        this._results = value;
    }
}
