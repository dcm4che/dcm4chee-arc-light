import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpModule } from '@angular/http';
import { RouterModule }   from '@angular/router';
import {AppComponent} from './app.component';
import {MaterialModule, MdDialogConfig} from "@angular/material";
import { StudiesComponent } from './studies/studies.component';
import { PageNotFoundComponent } from './page-not-found/page-not-found.component';
import { ControlComponent } from './control/control.component';
import { QueuesComponent } from './queues/queues.component';
import {SlimLoadingBarModule} from "ng2-slim-loading-bar";
import { OrderByPipe } from './pipes/order-by.pipe';
import { GetKeyPipe } from './pipes/get-key.pipe';
import {WidgetsModule, WidgetsComponents} from "./widgets/widgets.module";
import {CommonModule, DatePipe} from "@angular/common";
import { FormatDAPipe } from './pipes/format-da.pipe';
import { FormatTMPipe } from './pipes/format-tm.pipe';
import { FormatTagPipe } from './pipes/format-tag.pipe';
import { ContentDescriptionPipe } from './pipes/content-description.pipe';
import { FormatAttributeValuePipe } from './pipes/format-attribute-value.pipe';
import { AttributeNameOfPipe } from './pipes/attribute-name-of.pipe';
import { RemovedotsPipe } from './pipes/removedots.pipe';
import {StudiesService} from "./studies/studies.service";
import {AppService} from "./app.service";
import { AttributeListComponent } from './helpers/attribute-list/attribute-list.component';
import { FileAttributeListComponent } from './helpers/file-attribute-list/file-attribute-list.component';
import {CalendarModule} from "primeng/components/calendar/calendar";
import {SelectItem} from "primeng/components/common/api";
import {DropdownModule} from "primeng/components/dropdown/dropdown";
import { TrimPipe } from './pipes/trim.pipe';
import { SearchPipe } from './pipes/search.pipe';
import { KeysPipe } from './pipes/keys.pipe';
import { IodFormGeneratorComponent } from './helpers/iod-form-generator/iod-form-generator.component';
import { TooltipDirective } from './helpers/tooltip/tooltip.directive';

@NgModule({
    declarations: [
        AppComponent,
        StudiesComponent,
        PageNotFoundComponent,
        ControlComponent,
        QueuesComponent,
        OrderByPipe,
        GetKeyPipe,
        WidgetsComponents,
        FormatDAPipe,
        FormatTMPipe,
        FormatTagPipe,
        ContentDescriptionPipe,
        FormatAttributeValuePipe,
        AttributeNameOfPipe,
        RemovedotsPipe,
        AttributeListComponent,
        FileAttributeListComponent,
        TrimPipe,
        SearchPipe,
        KeysPipe,
        IodFormGeneratorComponent,
        TooltipDirective
    ],
    imports: [
        BrowserModule,
        FormsModule,
        HttpModule,
        MaterialModule.forRoot(),
        SlimLoadingBarModule.forRoot(),
        WidgetsModule,
        CommonModule,
        CalendarModule,
        DropdownModule,
        RouterModule.forRoot([
            {
              path: '',
              redirectTo: '/studies',
              pathMatch: 'full'
            },
            { path: 'studies', component: StudiesComponent },
            { path: 'control', component: ControlComponent },
            { path: '**', component: PageNotFoundComponent }
      ])
    ],
    entryComponents:[WidgetsComponents],
    providers: [MdDialogConfig, WidgetsComponents, AppService, StudiesService, DatePipe, CalendarModule, DropdownModule],
    bootstrap: [AppComponent]
})
export class AppModule { }
