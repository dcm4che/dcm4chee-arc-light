import { BrowserModule, Title, provideClientHydration, withEventReplay } from '@angular/platform-browser';
import {LOCALE_ID, NgModule} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {NavigationError, NoPreloading, Router, RouterModule} from '@angular/router';
import {AppComponent} from './app.component';
import { MatNativeDateModule, MAT_DATE_FORMATS, DateAdapter, MAT_DATE_LOCALE } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatIconModule } from '@angular/material/icon';
import { PageNotFoundComponent } from './page-not-found/page-not-found.component';
import { ControlComponent } from './configuration/control/control.component';
import { QueuesComponent } from './monitoring/queues/queues.component';
import { OrderByPipe } from './pipes/order-by.pipe';
import { GetKeyPipe } from './pipes/get-key.pipe';
import {WidgetsModule, WidgetsComponents} from './widgets/widgets.module';
import {CommonModule, DatePipe, registerLocaleData} from '@angular/common';
import { FormatDAPipe } from './pipes/format-da.pipe';
import { FormatTMPipe } from './pipes/format-tm.pipe';
import { FormatTagPipe } from './pipes/format-tag.pipe';
import { ContentDescriptionPipe } from './pipes/content-description.pipe';
import { PatientIssuerPipe } from './pipes/patient-issuer.pipe';
import { PersonNamePipe } from './pipes/person-name.pipe';
import { FormatAttributeValuePipe } from './pipes/format-attribute-value.pipe';
import { RemovedotsPipe } from './pipes/removedots.pipe';
import {StudiesService} from './studies/studies.service';
import {AppService} from './app.service';
import {AttributeListComponent} from './helpers/attribute-list/attribute-list.component';
import { TrimPipe } from './pipes/trim.pipe';
import { SearchPipe } from './pipes/search.pipe';
import { KeysPipe } from './pipes/keys.pipe';
import { IodFormGeneratorComponent } from './helpers/iod-form-generator/iod-form-generator.component';
import { TooltipDirective } from './helpers/tooltip/tooltip.directive';
import { ComparewithiodPipe } from './pipes/comparewithiod.pipe';
import { PlaceholderchangerDirective } from './helpers/placeholderchanger.directive';
import {QueuesService} from './monitoring/queues/queues.service';
import { DevicesComponent } from './configuration/devices/devices.component';

import {DynamicFormElementComponent} from './widgets/dynamicform/dynamic-form-element.component';
import {DynamicFormComponent} from './widgets/dynamicform/dynamic-form.component';
import { ExportComponent } from './monitoring/export/export.component';
import {ExportService} from './monitoring/export/export.service';
import { DicomConnectionFormaterPipe } from './pipes/dicom-connection-formater.pipe';
import { AssociationsComponent } from './monitoring/associations/associations.component';
import { StorageCommitmentComponent } from './monitoring/storage-commitment/storage-commitment.component';
import {StorageCommitmentService} from './monitoring/storage-commitment/storage-commitment.service';
import { ConnectionFormaterComponent } from './helpers/connection-formater/connection-formater.component';
import { AeListComponent } from './configuration/ae-list/ae-list.component';
import {CreateExporterService} from './widgets/dialogs/create-exporter/create-exporter.service';
import {DeviceConfiguratorService} from './configuration/device-configurator/device-configurator.service';
import { UtcPipe } from './pipes/utc.pipe';
import { CustomValidatorDirective } from './helpers/custom-validator/custom-validator.directive';
import {BrowserAnimationsModule} from '@angular/platform-browser/animations';
import {ControlService} from './configuration/control/control.service';
import { StorageSystemsComponent } from './monitoring/storage-systems/storage-systems.component';
import {StorageSystemsService} from './monitoring/storage-systems/storage-systems.service';
import {UploadDicomService} from './widgets/dialogs/upload-dicom/upload-dicom.service';
import {WindowRefService} from './helpers/window-ref.service';
import { MonitoringTabsComponent } from './monitoring/monitoring-tabs.component';
import {Hl7ApplicationsService} from './configuration/hl7-applications/hl7-applications.service';
import {AeListService} from './configuration/ae-list/ae-list.service';
import {HttpErrorHandler} from './helpers/http-error-handler';
import {j4care} from './helpers/j4care.service';
import {J4careHttpService} from './helpers/j4care-http.service';
import { FilterGeneratorComponent } from './helpers/filter-generator/filter-generator.component';
import { ClickOutsideDirective } from './helpers/click-outside.directive';
import {DynamicFieldService} from './widgets/dynamic-field/dynamic-field.service';
import {AuthGuard} from './helpers/permissions/auth.guard';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import {PermissionService} from './helpers/permissions/permission.service';
import { PermissionDirective } from './helpers/permissions/permission.directive';
import {LoadingBarModule} from '@ngx-loading-bar/core';
import {PermissionDeniedComponent} from './helpers/permissions/permission-denied.component';
import {CsvUploadService} from './widgets/dialogs/csv-upload/csv-upload.service';
import { StackedProgressComponent } from './helpers/stacked-progress/stacked-progress.component';
import { DiffMonitorComponent } from './monitoring/diff-monitor/diff-monitor.component';
import {DiffMonitorService} from './monitoring/diff-monitor/diff-monitor.service';
import { LargeIntFormatPipe } from './pipes/large-int-format.pipe';
import { TableGeneratorComponent } from './helpers/table-generator/table-generator.component';
import {RangePickerService} from './widgets/range-picker/range-picker.service';
import { StorageVerificationComponent } from './monitoring/storage-verification/storage-verification.component';
import {StorageVerificationService} from './monitoring/storage-verification/storage-verification.service';
import {DevicesService} from './configuration/devices/devices.service';

import {StudyService} from './study/study/study.service';
import { DynamicPipePipe } from './pipes/dynamic-pipe.pipe';
import {OptionService} from './widgets/dropdown/option.service';
import {RetrieveMonitoringService} from './monitoring/external-retrieve/retrieve-monitoring.service';
import { ArrayToStringPipe } from './pipes/array-to-string.pipe';
import {KeycloakService} from './helpers/keycloak-service/keycloak.service';
import {KeycloakHttpClient} from './helpers/keycloak-service/keycloak-http-client.service';
import {MetricsService} from './monitoring/metrics/metrics.service';
import {WebAppsListService} from './configuration/web-apps-list/web-apps-list.service';

import {SelectionsDicomViewService} from './study/study/selections-dicom-view/selections-dicom-view.service';
import {MY_FORMATS} from './constants/globalvar';
import {MAT_MOMENT_DATE_ADAPTER_OPTIONS, MomentDateAdapter} from '@angular/material-moment-adapter';

import { CustomDatePipe } from './pipes/custom-date.pipe';
import {UploadFilesService} from './widgets/dialogs/upload-files/upload-files.service';
import {MatSelectModule} from '@angular/material/select';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatButtonModule} from '@angular/material/button';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatInputModule} from '@angular/material/input';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatRadioModule} from '@angular/material/radio';
import {MatMenuModule} from '@angular/material/menu';
import {MAT_DIALOG_DATA, MatDialogModule} from '@angular/material/dialog';
import { MatFormFieldModule} from '@angular/material/form-field';


// registerLocaleData(localeDe, 'de-DE');



@NgModule({ declarations: [
        AppComponent,
        PageNotFoundComponent,
        CustomValidatorDirective,
        PermissionDeniedComponent
    ],
    bootstrap: [AppComponent],
    exports: [
    ],
    imports: [BrowserModule,
        FormsModule,
        MatIconModule,
        MatSelectModule,
        MatProgressBarModule,
        MatInputModule,
        MatFormFieldModule,
        MatDialogModule,
        MatProgressSpinnerModule,
        MatCheckboxModule,
        MatRadioModule,
        MatMenuModule,
        MatDatepickerModule,
        MatNativeDateModule,
        MatButtonModule,
        CommonModule,
        ReactiveFormsModule,
        BrowserAnimationsModule,
        LoadingBarModule,
        RouterModule.forRoot([
            {
                path: '',
                redirectTo: '/study/study',
                pathMatch: 'full'
            }, {
                path: 'index',
                redirectTo: '/study/study',
                pathMatch: 'full'
            }, {
                path: 'index.html',
                redirectTo: '/study/study',
                pathMatch: 'full'
            },
            {
                path: 'monitoring',
                redirectTo: '/monitoring/queues',
                pathMatch: 'full'
            },
            {
                path: 'study/:tab',
                canActivate: [AuthGuard],
                loadComponent: () =>
                    import('./study/study/study.component').then(m => m.StudyComponent)
            },
            {path: 'permission-denied', component: PermissionDeniedComponent},
            {
                path: 'monitoring/control',
                loadComponent: () => import('./configuration/control/control.component').then(m => m.ControlComponent),
                canActivate: [AuthGuard]
            },
            {
                path: 'monitoring/export',
                loadComponent: () => import('./monitoring/export/export.component').then(m => m.ExportComponent),
                canActivate: [AuthGuard]
            },
            {
                path: 'monitoring/external',
                loadComponent: () => import('./monitoring/external-retrieve/retrieve-monitoring.component')
                    .then(m => m.RetrieveMonitoringComponent),
                canActivate: [AuthGuard]
            },
            {
                path: 'monitoring/queues',
                loadComponent: () => import('./monitoring/queues/queues.component').then(m => m.QueuesComponent),
                canActivate: [AuthGuard]
            },
            {
                path: 'monitoring/associations',
                loadComponent: () => import('./monitoring/associations/associations.component').then(m => m.AssociationsComponent),
                canActivate: [AuthGuard]
            },
            {
                path: 'monitoring/storage-commitment',
                loadComponent: () => import('./monitoring/storage-commitment/storage-commitment.component')
                    .then(m => m.StorageCommitmentComponent),
                canActivate: [AuthGuard]
            },
            {
                path: 'monitoring/storage-systems',
                loadComponent: () => import('./monitoring/storage-systems/storage-systems.component').then(m => m.StorageSystemsComponent),
                canActivate: [AuthGuard]
            },
            {
                path: 'monitoring/storage-verification',
                loadComponent: () => import('./monitoring/storage-verification/storage-verification.component')
                    .then(m => m.StorageVerificationComponent),
                canActivate: [AuthGuard]
            },
            {
                path: 'monitoring/diff',
                loadComponent: () => import('./monitoring/diff-monitor/diff-monitor.component').then(m => m.DiffMonitorComponent),
                canActivate: [AuthGuard]
            },
            {
                path: 'monitoring/metrics',
                loadComponent: () => import('./monitoring/metrics/metrics.component').then(m => m.MetricsComponent),
                canActivate: [AuthGuard]
            },
            {
                path: 'device/devicelist',
                loadComponent: () => import('./configuration/devices/devices.component').then(m => m.DevicesComponent),
                canActivate: [AuthGuard]
            },
            {
                path: 'device/aelist',
                loadComponent: () => import('./configuration/ae-list/ae-list.component').then(m => m.AeListComponent),
                canActivate: [AuthGuard]
            },
            {
                path: 'device/webappslist',
                loadComponent: () => import('./configuration/web-apps-list/web-apps-list.component').then(m => m.WebAppsListComponent),
                canActivate: [AuthGuard]
            },
            {
                path: 'device/hl7applications',
                loadComponent: () => import('./configuration/hl7-applications/hl7-applications.component')
                    .then(m => m.Hl7ApplicationsComponent),
                canActivate: [AuthGuard]
            },
            {
                path: 'device/edit/:device',
                loadComponent: () => import('./configuration/device-configurator/device-configurator.component')
                    .then(m => m.DeviceConfiguratorComponent),
                canActivate: [AuthGuard]
            },
            {
                path: 'device/edit/:device/:devicereff',
                loadComponent: () => import('./configuration/device-configurator/device-configurator.component')
                    .then(m => m.DeviceConfiguratorComponent),
                canActivate: [AuthGuard]
            },
            {
                path: 'device/edit/:device/:devicereff/:schema',
                loadComponent: () => import('./configuration/device-configurator/device-configurator.component')
                    .then(m => m.DeviceConfiguratorComponent),
                canActivate: [AuthGuard]
            },
            {
                path: 'device/edit/:device/:devicereff/:schema/:clone',
                loadComponent: () => import('./configuration/device-configurator/device-configurator.component')
                    .then(m => m.DeviceConfiguratorComponent),
                canActivate: [AuthGuard]
            },
            {path: '**', component: PageNotFoundComponent}
        ], {
            useHash: false,
            preloadingStrategy: NoPreloading
        }),
        WidgetsComponents,
        PlaceholderchangerDirective,
        PermissionDirective, ClickOutsideDirective,
    ], providers: [
        WidgetsComponents,
        WidgetsModule,
        MatDatepickerModule,
        AppService,
        ControlService,
        QueuesService,
        DevicesService,
        ExportService,
        J4careHttpService,
        DatePipe,
        StorageCommitmentService,
        StorageSystemsService,
        CreateExporterService,
        DeviceConfiguratorService,
        UploadDicomService,
        WindowRefService,
        StudiesService,
        Hl7ApplicationsService,
        AeListService,
        HttpErrorHandler,
        j4care,
        RetrieveMonitoringService,
        DynamicFieldService,
        AuthGuard,
        PermissionService,
        CsvUploadService,
        DiffMonitorService,
        RangePickerService,
        StorageVerificationService,
        StudyService,
        ContentDescriptionPipe,
        PatientIssuerPipe,
        PersonNamePipe,
        ArrayToStringPipe,
        OptionService,
        KeycloakService,
        KeycloakHttpClient,
        MetricsService,
        WebAppsListService,
        SelectionsDicomViewService,
        UploadFilesService,
        CustomDatePipe,
        DynamicPipePipe,
        Title,
        /*        {
                    provide: MatDialogRef,
                    useValue: {}
                },*/
        { provide: LOCALE_ID, useValue: 'en-US' },
        { provide: LOCALE_ID, useValue: 'de' },
        { provide: MAT_DIALOG_DATA, useValue: {} },
        {
            provide: DateAdapter,
            useClass: MomentDateAdapter,
            deps: [MAT_DATE_LOCALE, MAT_MOMENT_DATE_ADAPTER_OPTIONS]
        },
        // { provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: MY_FORMATS },
        { provide: MAT_DATE_FORMATS, useValue: MY_FORMATS },
        provideHttpClient(withInterceptorsFromDi()),
        provideClientHydration(withEventReplay()),
    ] })
export class AppModule {
    constructor(private router: Router) {
        this.router.events.subscribe(event => {
            // After activating the lazy-loaded approach, if the KeyCloak-ideal time is passed,
            // fetching the chunks triggers a redirect error instead of retrieving the chunks.
            // So we need a page reload to get out from the error and ui blockade.

            if (event instanceof NavigationError) {
                console.warn('NavigationError:', event);
                if (event.error && event.error.message?.includes('dynamically imported module')) {
                    window.location.reload();
                }
            }
        });
    }
}
