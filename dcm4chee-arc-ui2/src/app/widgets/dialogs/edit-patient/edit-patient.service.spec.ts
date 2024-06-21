import { TestBed } from '@angular/core/testing';

import { EditPatientService } from './edit-patient.service';

describe('EditPatientService', () => {
  let service: EditPatientService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(EditPatientService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
