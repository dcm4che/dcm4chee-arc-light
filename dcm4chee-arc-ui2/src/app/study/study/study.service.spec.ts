import { TestBed, inject } from '@angular/core/testing';

import { StudyService } from './study.service';
import {AeListService} from "../../configuration/ae-list/ae-list.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {StorageSystemsService} from "../../monitoring/storage-systems/storage-systems.service";
import {DevicesService} from "../../configuration/devices/devices.service";

class StudyServiceDependenc{
}

describe('StudyService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
          StudyService,
          {provide:AeListService, useClass:StudyServiceDependenc},
          {provide:J4careHttpService, useClass:StudyServiceDependenc},
          {provide:StorageSystemsService, useClass:StudyServiceDependenc},
          {provide:DevicesService, useClass:StudyServiceDependenc}
      ]
    });
  });

  it('should be created', inject([StudyService], (service: StudyService) => {
    expect(service).toBeTruthy();
  }));
});
