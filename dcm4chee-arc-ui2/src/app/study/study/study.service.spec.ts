import { TestBed, inject } from '@angular/core/testing';

import { StudyService } from './study.service';
import {AeListService} from "../../configuration/ae-list/ae-list.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {StorageSystemsService} from "../../monitoring/storage-systems/storage-systems.service";
import {DevicesService} from "../../configuration/devices/devices.service";
import {WebAppsListService} from "../../configuration/web-apps-list/web-apps-list.service";
import {RetrieveMonitoringService} from "../../monitoring/external-retrieve/retrieve-monitoring.service";
import {PermissionService} from "../../helpers/permissions/permission.service";
import {LargeIntFormatPipe} from "../../pipes/large-int-format.pipe";
import {KeycloakService} from "../../helpers/keycloak-service/keycloak.service";
import {AppService} from "../../app.service";
import {HttpClient, HttpHandler} from "@angular/common/http";
import {Router} from "@angular/router";

class StudyServiceDependenc{
}

describe('StudyService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
          StudyService,
          {provide:KeycloakService, useClass:StudyServiceDependenc},
          {provide:AppService, useClass:StudyServiceDependenc},
          {provide:AeListService, useClass:StudyServiceDependenc},
          {provide:J4careHttpService, useClass:StudyServiceDependenc},
          {provide:StorageSystemsService, useClass:StudyServiceDependenc},
          {provide:DevicesService, useClass:StudyServiceDependenc},
          {provide:WebAppsListService, useClass:StudyServiceDependenc},
          {provide:RetrieveMonitoringService, useClass:StudyServiceDependenc},
          {provide:PermissionService, useClass:StudyServiceDependenc},
          {provide:LargeIntFormatPipe, useClass:StudyServiceDependenc}
      ]
    });
  });

  it('should be created', inject([StudyService], (service: StudyService) => {
    expect(service).toBeTruthy();
  }));
});
