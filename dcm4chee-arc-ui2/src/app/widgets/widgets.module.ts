import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { WidgetsComponent } from './widgets.component';
import {BrowserModule} from "@angular/platform-browser";
import { TestdialogComponent } from './dialogs/testdialog.component';
import {FormsModule} from "@angular/forms";
import {MdDialogConfig} from "@angular/material";
import { MessagingComponent } from './messaging/messaging.component';
import {EditPatientComponent} from "./dialogs/edit-patient/edit-patient.component";
import { EditMwlComponent } from './dialogs/edit-mwl/edit-mwl.component';


@NgModule({
    imports: [
        CommonModule,
        BrowserModule,
        FormsModule,

    ],
    declarations: [WidgetsComponent, EditMwlComponent],
    exports:[WidgetsComponent],
    providers: [MdDialogConfig]
})
export class WidgetsModule { }
export const WidgetsComponents = [TestdialogComponent, MessagingComponent,EditPatientComponent];
