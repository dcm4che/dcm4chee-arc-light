/* tslint:disable:no-unused-variable */
import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA} from '@angular/core';

import { DeviceConfiguratorComponent } from './device-configurator.component';
import {ActivatedRoute, RouterModule, Router, Routes} from "@angular/router";
import {RouterTestingModule} from "@angular/router/testing";
import {DeviceConfiguratorService} from "./device-configurator.service";
import {Http, ConnectionBackend, HttpModule} from "@angular/http";

describe('DeviceConfiguratorComponent', () => {
  let component: DeviceConfiguratorComponent;
  let fixture: ComponentFixture<DeviceConfiguratorComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
        schemas: [ NO_ERRORS_SCHEMA ],
        imports:[RouterModule,RouterTestingModule, HttpModule],
        declarations: [ DeviceConfiguratorComponent ],
        providers:[DeviceConfiguratorService]
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
