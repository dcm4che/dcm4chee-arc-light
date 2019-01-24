import { TestBed, inject } from '@angular/core/testing';

import { StudyService } from './study.service';
import {AeListService} from "../../configuration/ae-list/ae-list.service";

class StudyServiceDependenc{
}

describe('StudyService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
          StudyService,
          {provide:AeListService, useClass:StudyServiceDependenc}
      ]
    });
  });

  it('should be created', inject([StudyService], (service: StudyService) => {
    expect(service).toBeTruthy();
  }));
});
