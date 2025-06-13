import { Component, OnInit } from '@angular/core';
//import { MatLegacyDialogRef as MatDialogRef } from "@angular/material/legacy-dialog";
import {SelectionActionElement} from "../../../study/study/selection-action-element.models";
import {SelectDropdown} from "../../../interfaces";
import {MatDialogRef} from "@angular/material/dialog";
import {FormsModule} from '@angular/forms';
import {PatientIssuerPipe} from '../../../pipes/patient-issuer.pipe';
import {AppModule} from '../../../app.module';
import {CommonModule, NgSwitch, UpperCasePipe} from '@angular/common';
import {SelectionsDicomViewComponent} from '../../../study/study/selections-dicom-view/selections-dicom-view.component';

@Component({
    selector: 'app-study-transferring-overview',
    templateUrl: './study-transferring-overview.component.html',
    styleUrls: ['./study-transferring-overview.component.scss'],
    imports: [
        FormsModule,
        PatientIssuerPipe,
        NgSwitch,
        UpperCasePipe,
        SelectionsDicomViewComponent,
        CommonModule
    ],
    standalone: true
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
