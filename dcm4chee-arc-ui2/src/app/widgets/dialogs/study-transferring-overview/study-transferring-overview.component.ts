import { Component, OnInit } from '@angular/core';
//import { MatLegacyDialogRef as MatDialogRef } from "@angular/material/legacy-dialog";
import {SelectionActionElement} from "../../../study/study/selection-action-element.models";
import {SelectDropdown} from "../../../interfaces";
import {MatDialogRef} from "@angular/material/dialog";

@Component({
    selector: 'app-study-transferring-overview',
    templateUrl: './study-transferring-overview.component.html',
    styleUrls: ['./study-transferring-overview.component.scss'],
    standalone: false
})
export class StudyTransferringOverviewComponent implements OnInit {

    private _selectedElements:SelectionActionElement;
    rjnotes:SelectDropdown<any>[];
    title = $localize `:@@move:Move`;
    Object = Object;
    target;
    reject;

    constructor(public dialogRef: MatDialogRef<StudyTransferringOverviewComponent>) { }

    ngOnInit() {
        if(this.selectedElements.action === "link"){
            this.reject = "113038^DCM";
        }
    }

    onRemoveFromSelection(e){
        this._selectedElements.toggle(e.dicomLevel,e.uniqueSelectIdObject, e.object, "preActionElements");
    }


    get selectedElements(): SelectionActionElement {
        return this._selectedElements;
    }

    set selectedElements(value: SelectionActionElement) {
        this.target = value.postActionElements.getAllAsArray()[0];
        this._selectedElements = value;
    }
}
