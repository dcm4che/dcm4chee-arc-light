import {Component, OnInit, ViewContainerRef} from '@angular/core';
import { DicomNetworkConnection, FilterSchema, SelectDropdown} from '../../interfaces';
import {j4care} from '../../helpers/j4care.service';
import {Device} from '../../models/device';
import {Aet} from '../../models/aet';
import {LoadingBarService} from '@ngx-loading-bar/core';
import {HttpErrorHandler} from '../../helpers/http-error-handler';
import {forkJoin} from 'rxjs';
import {ExportDialogComponent} from '../../widgets/dialogs/export/export.component';
import {CreateWebappComponent} from '../../widgets/dialogs/create-webapp/create-webapp.component';
import {MatDialog, MatDialogRef} from '@angular/material/dialog';
import {FilterGeneratorComponent} from '../../helpers/filter-generator/filter-generator.component';
import {TableGeneratorComponent} from '../../helpers/table-generator/table-generator.component';
import {FormsModule} from '@angular/forms';
import {ConfigTabComponent} from '../config-tab.component';
import {SearchPipe} from '../../pipes/search.pipe';
import {CommonModule} from '@angular/common';
import {WebAppsListService} from './web-apps-list.service';

@Component({
    selector: 'app-web-apps-list',
    templateUrl: './web-apps-list.component.html',
    styleUrls: ['./web-apps-list.component.scss'],
    imports: [
        FilterGeneratorComponent,
        TableGeneratorComponent,
        FormsModule,
        ConfigTabComponent,
        SearchPipe,
        CommonModule
    ],
    standalone: true
})
export class WebAppsListComponent implements OnInit {

    filterObject = {};
    filterSchema: FilterSchema;
    tableConfig;
    webApps;
    filterHeight = 2;
    search = '';
    serviceClasses: SelectDropdown<string>;
    devices: SelectDropdown<Device>;
    aes: Aet[];
    dialogRef: MatDialogRef<CreateWebappComponent>;
    constructor(
        private service: WebAppsListService,
        private cfpLoadingBar: LoadingBarService,
        private httpErrorHandler: HttpErrorHandler,
        private viewContainerRef: ViewContainerRef,
        private dialog: MatDialog,
    ) {}

    ngOnInit() {
        this.init();
    }

    submit(e) {
        this.cfpLoadingBar.start();
        this.service.getWebApps(this.filterObject).subscribe(webApps => {
            this.webApps = webApps.map(webApp => {
                try {
                    webApp['url'] = webApp.dicomNetworkConnection.map((networkConnection: DicomNetworkConnection) => {
                        return `${j4care.getUrlFromDicomNetworkConnection(networkConnection)}${j4care.meyGetString(webApp, 'dcmWebServicePath')}`;
                    });
                } catch (e) {
                    j4care.log('Error on getting url from network', e);
                }
                return webApp;
            });
            this.cfpLoadingBar.complete();
        }, err => {
            this.httpErrorHandler.handleError(err);
            this.cfpLoadingBar.complete();
        })
    }

    init() {
        forkJoin(
            this.service.getServiceClasses(),
            this.service.getDevices(),
            this.service.getAes()
        ).subscribe(res => {
            this.serviceClasses    = <any>res[0];
            this.devices           = <any>res[1];
            this.aes               = <any>res[2];

            this.setFilterSchema();
            this.setTableConfig();
        }, err => {
            this.httpErrorHandler.handleError(err);
        });
    }

    setTableConfig() {
        this.tableConfig = {
            table: j4care.calculateWidthOfTable(this.service.getTableSchema()),
            filter: this.filterObject,
            calculate: false
        };
    }

    setFilterSchema() {
        this.filterSchema = j4care.prepareFlatFilterObject(
          this.service.getFilterSchema(
              this.devices,
              this.aes,
              this.serviceClasses
          ),
          this.filterHeight
        );
    }
    createWebApp() {
        this.dialogRef = this.dialog.open(CreateWebappComponent, {});
        this.dialogRef.afterClosed().subscribe(ok => {
            console.log('ok');
        });
    }
}
