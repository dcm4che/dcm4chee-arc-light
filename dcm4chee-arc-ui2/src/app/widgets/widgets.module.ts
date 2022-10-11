import { NgModule, Injectable } from '@angular/core';
import { CommonModule } from '@angular/common';
import { WidgetsComponent } from './widgets.component';
import {BrowserModule} from '@angular/platform-browser';
import {FormsModule} from '@angular/forms';
import { MatDialogConfig } from '@angular/material/dialog';
import { MessagingComponent } from './messaging/messaging.component';
import {EditPatientComponent} from './dialogs/edit-patient/edit-patient.component';
import { EditMwlComponent } from './dialogs/edit-mwl/edit-mwl.component';
import { ConfirmComponent } from './dialogs/confirm/confirm.component';
import { EditStudyComponent } from './dialogs/edit-study/edit-study.component';
import { EditSeriesComponent } from './dialogs/edit-series/edit-series.component';
import { DeleteRejectedInstancesComponent } from './dialogs/delete-rejected-instances/delete-rejected-instances.component';
import { CreateAeComponent } from './dialogs/create-ae/create-ae.component';
import { ProductLabellingComponent } from './dialogs/product-labelling/product-labelling.component';
import { CreateExporterComponent } from './dialogs/create-exporter/create-exporter.component';
import { ExportDialogComponent } from './dialogs/export/export.component';
import { CloneSelectorComponent } from './dialogs/clone-selector/clone-selector.component';
import { UploadVendorComponent } from './dialogs/upload-vendor/upload-vendor.component';
import { UploadDicomComponent } from './dialogs/upload-dicom/upload-dicom.component';
import { RemovePartSelectorComponent } from './dialogs/remove-part-selector/remove-part-selector.component';
import { InfoComponent } from './dialogs/info/info.component';
import { UploadFilesComponent } from './dialogs/upload-files/upload-files.component';
import { SpecificCharPickerComponent } from './specific-char-picker/specific-char-picker.component';
import { DictionaryPickerComponent } from './dictionary-picker/dictionary-picker.component';
import { TimePickerComponent } from './time-picker/time-picker.component';
import { DurationPickerComponent } from './duration-picker/duration-picker.component';
import { SchedulePickerComponent } from './schedule-picker/schedule-picker.component';
import { AttributeInfoComponent } from './attribute-info/attribute-info.component';
import { ViewerComponent } from './dialogs/viewer/viewer.component';
import {DicomFlatListComponent} from "./dicom-list/dicom-flat-list.component";
import { ModalityComponent } from './modality/modality.component';
import { RangePickerComponent } from './range-picker/range-picker.component';
import { DynamicFieldComponent } from './dynamic-field/dynamic-field.component';
import { DcmSelectComponent } from './dcm-select/dcm-select.component';
import { CsvUploadComponent } from './dialogs/csv-upload/csv-upload.component';
import { SizeRangePickerComponent } from './size-range-picker/size-range-picker.component';
import { DropdownComponent } from './dropdown/dropdown.component';
import {OptionComponent} from "./dropdown/option.component";
import { DcmDropDownComponent } from './dcm-drop-down/dcm-drop-down.component';
import { StudyTransferringOverviewComponent } from './dialogs/study-transferring-overview/study-transferring-overview.component';
import { MediaViewerComponent } from './dialogs/media-viewer/media-viewer.component';
import { CodeSelectorComponent } from './code-selector/code-selector.component';
import { LanguagePickerComponent } from './language-picker/language-picker.component';
import {ModifyUpsComponent} from "./dialogs/modify-ups/modify-ups.component";
import { CreateWebappComponent } from './dialogs/create-webapp/create-webapp.component';
import { DeviceCloneComponent } from './dialogs/device-clone/device-clone.component';
import { ModifiedWidgetComponent } from './modified-widget/modified-widget.component';


@Injectable()
@NgModule({
    imports: [
        CommonModule,
        BrowserModule,
        FormsModule
    ],
    declarations: [WidgetsComponent],
    exports: [WidgetsComponent],
    providers: [MatDialogConfig]
})
export class WidgetsModule { }
export const WidgetsComponents = [
    MessagingComponent,
    EditPatientComponent,
    EditMwlComponent,
    EditStudyComponent,
    EditSeriesComponent,
    ModifyUpsComponent,
    CreateAeComponent,
    CreateWebappComponent,
    ConfirmComponent,
    DeleteRejectedInstancesComponent,
    ProductLabellingComponent,
    CreateExporterComponent,
    ExportDialogComponent,
    CloneSelectorComponent,
    UploadVendorComponent,
    UploadDicomComponent,
    RemovePartSelectorComponent,
    InfoComponent,
    UploadFilesComponent,
    SpecificCharPickerComponent,
    DictionaryPickerComponent,
    TimePickerComponent,
    DurationPickerComponent,
    SchedulePickerComponent,
    AttributeInfoComponent,
    ViewerComponent,
    DicomFlatListComponent,
    ModalityComponent,
    RangePickerComponent,
    DynamicFieldComponent,
    DcmSelectComponent,
    CsvUploadComponent,
    SizeRangePickerComponent,
    DropdownComponent,
    OptionComponent,
    DcmDropDownComponent,
    StudyTransferringOverviewComponent,
    MediaViewerComponent,
    CodeSelectorComponent,
    LanguagePickerComponent,
    DeviceCloneComponent,
    ModifiedWidgetComponent
];
