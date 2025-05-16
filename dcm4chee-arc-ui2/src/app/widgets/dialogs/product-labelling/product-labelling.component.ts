import { Component } from '@angular/core';
//import { MatLegacyDialogRef as MatDialogRef } from '@angular/material/legacy-dialog';
import {j4care} from "../../../helpers/j4care.service";
import {MatDialogRef} from "@angular/material/dialog";

@Component({
    selector: 'app-product-labelling',
    templateUrl: './product-labelling.component.html',
    standalone: false
})
export class ProductLabellingComponent {
    private _archive;
    year = j4care.formatDate(new Date(), "yyyy");
    constructor(public dialogRef: MatDialogRef<ProductLabellingComponent>) { }

    get archive() {
        return this._archive;
    }

    set archive(value) {
        this._archive = value;
    }
}
