import { Component, OnInit } from '@angular/core';
import {CopyMoveObjectsComponent} from "../copy-move-objects/copy-move-objects.component";
import {MatDialogRef} from "@angular/material";
import {SelectionActionElement} from "../../../study/study/selection-action-element.models";
import {SelectDropdown} from "../../../interfaces";

@Component({
  selector: 'app-study-transferring-overview',
  templateUrl: './study-transferring-overview.component.html',
  styleUrls: ['./study-transferring-overview.component.scss']
})
export class StudyTransferringOverviewComponent implements OnInit {

    private _selectedElements:SelectionActionElement;
    rjnotes:SelectDropdown<any>[];
    title = "Move";
    Object = Object;
    target;
    reject;

    constructor(public dialogRef: MatDialogRef<StudyTransferringOverviewComponent>) { }

    ngOnInit() {
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
