/* tslint:disable:no-unused-variable */

import { TestBed, async, inject } from '@angular/core/testing';
import { StudiesService } from './studies.service';

describe('Service: Studies', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [StudiesService]
    });
  });

  it('should ...', inject([StudiesService], (service: StudiesService) => {
    expect(service).toBeTruthy();
  }));
});
