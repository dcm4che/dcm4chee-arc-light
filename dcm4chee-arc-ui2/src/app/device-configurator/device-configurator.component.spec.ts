/* tslint:disable:no-unused-variable */
import { NO_ERRORS_SCHEMA} from '@angular/core';

import { DeviceConfiguratorComponent } from './device-configurator.component';
import {ActivatedRoute, RouterModule, Router, Routes} from '@angular/router';
import {DeviceConfiguratorService} from './device-configurator.service';
import {Http, ConnectionBackend, HttpModule} from '@angular/http';
import {async, ComponentFixture, TestBed} from '@angular/core/testing';
import {MaterialModule} from "@angular/material";
import {RouterTestingModule} from "@angular/router/testing";
import {J4careHttpService} from "../helpers/j4care-http.service";
import {AppService} from "../app.service";
import {DevicesService} from "../devices/devices.service";
import {AeListService} from "../ae-list/ae-list.service";
import {Hl7ApplicationsService} from "../hl7-applications/hl7-applications.service";
import {ControlService} from "../control/control.service";
import {SlimLoadingBarService} from "ng2-slim-loading-bar";
import {HttpErrorHandler} from "../helpers/http-error-handler";

describe('DeviceConfiguratorComponent', () => {
  let component: DeviceConfiguratorComponent;
  let fixture: ComponentFixture<DeviceConfiguratorComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
        schemas: [ NO_ERRORS_SCHEMA ],
        imports: [RouterModule, HttpModule, MaterialModule.forRoot(),RouterTestingModule],
        declarations: [ DeviceConfiguratorComponent ],
        providers: [
            DeviceConfiguratorService,
            J4careHttpService,
            AppService,
            DevicesService,
            AeListService,
            Hl7ApplicationsService,
            ControlService,
            SlimLoadingBarService,
            HttpErrorHandler
        ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(DeviceConfiguratorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
