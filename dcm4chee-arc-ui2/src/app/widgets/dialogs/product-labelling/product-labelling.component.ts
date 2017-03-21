import { Component, OnInit } from '@angular/core';
import {MdDialogRef} from "@angular/material";

@Component({
  selector: 'app-product-labelling',
  templateUrl: './product-labelling.component.html'
})
export class ProductLabellingComponent {
    private _archive;
    constructor(public dialogRef: MdDialogRef<ProductLabellingComponent>) { }

    get archive() {
        return this._archive;
    }

    set archive(value) {
        this._archive = value;
    }
}
