import { Component } from '@angular/core';
import {MdDialogRef} from "@angular/material";


@Component({
    selector: 'app-testdialog',
    template: `
<h3>Hallo Selam prej widgets2</h3>
<form #testform="ngForm">
   <label for="name">Name:</label>
   <input type="text" [(ngModel)]="name" name="name">
   <label for="nachname">Nachname</label>
   <input type="text" [(ngModel)]="nachname" name="nachname">
</form>

  <button type="button" (click)="dialogRef.close(testform.value)">Yes</button>
  <button type="button" (click)="dialogRef.close(null)">No</button>
  `
})
export class TestdialogComponent {
    constructor(public dialogRef: MdDialogRef<TestdialogComponent>) { }
    private _name;
    private _nachname;

    get name(): string {
        return this._name;
    }

    set name(value: string) {
        this._name = value;
    }

    get nachname(): string {
        return this._nachname;
    }

    set nachname(value: string) {
        this._nachname = value;
    }
}