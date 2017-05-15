import { Component, OnInit } from '@angular/core';
import {MdDialogRef} from "@angular/material";

@Component({
  selector: 'app-clone-selector',
  templateUrl: './clone-selector.component.html'
})
export class CloneSelectorComponent {

    private _select;

    constructor(public dialogRef: MdDialogRef<CloneSelectorComponent>) { }

    get select() {
        return this._select;
    }

    set select(value) {
        this._select = value;
    }
}
