import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { WidgetsComponent } from './widgets.component';
import {BrowserModule} from "@angular/platform-browser";
import {FormsModule} from "@angular/forms";
import {MdDialogConfig} from "@angular/material";
import { MessagingComponent } from './messaging/messaging.component';
import {EditPatientComponent} from "./dialogs/edit-patient/edit-patient.component";
import { EditMwlComponent } from './dialogs/edit-mwl/edit-mwl.component';
import { CopyMoveObjectsComponent } from './dialogs/copy-move-objects/copy-move-objects.component';
import { ConfirmComponent } from './dialogs/confirm/confirm.component';
import { EditStudyComponent } from './dialogs/edit-study/edit-study.component';
import { DeleteRejectedInstancesComponent } from './dialogs/delete-rejected-instances/delete-rejected-instances.component';
import {CalendarModule} from "primeng/components/calendar/calendar";
import { CreateAeComponent } from './dialogs/create-ae/create-ae.component';
import { ProductLabellingComponent } from './dialogs/product-labelling/product-labelling.component';
import {DynamicFormElementComponent} from "./dynamicform/dynamic-form-element.component";
import {DynamicFormComponent} from "./dynamicform/dynamic-form.component";
import { CreateExporterComponent } from './dialogs/create-exporter/create-exporter.component';
import { ExportDialogComponent } from './dialogs/export/export.component';
import { CloneSelectorComponent } from './dialogs/clone-selector/clone-selector.component';
import { UploadFilesComponent } from './dialogs/upload-files/upload-files.component';
import { UploadDicomComponent } from './dialogs/upload-dicom/upload-dicom.component';


@NgModule({
    imports: [
        CommonModule,
        BrowserModule,
        FormsModule,
        CalendarModule
    ],
    declarations: [WidgetsComponent ],
    exports:[WidgetsComponent],
    providers: [MdDialogConfig]
})
export class WidgetsModule { }
export const WidgetsComponents = [
    MessagingComponent,
    EditPatientComponent,
    EditMwlComponent,
    EditStudyComponent,
    CreateAeComponent,
    CopyMoveObjectsComponent,
    ConfirmComponent,
    DeleteRejectedInstancesComponent,
    ProductLabellingComponent,
    CreateExporterComponent,
    ExportDialogComponent,
    CloneSelectorComponent,
    UploadFilesComponent,
    UploadDicomComponent
];
