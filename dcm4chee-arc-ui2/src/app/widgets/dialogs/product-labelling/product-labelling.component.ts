import { Component } from '@angular/core';
import {MatDialogRef} from '@angular/material';

@Component({
  selector: 'app-product-labelling',
  templateUrl: './product-labelling.component.html'
})
export class ProductLabellingComponent {
    private _archive;
    constructor(public dialogRef: MatDialogRef<ProductLabellingComponent>) { }

    get archive() {
        return this._archive;
    }

    set archive(value) {
        this._archive = value;
    }
}
