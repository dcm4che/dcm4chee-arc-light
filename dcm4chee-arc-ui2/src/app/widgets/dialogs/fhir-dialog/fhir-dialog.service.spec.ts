import { TestBed } from '@angular/core/testing';

import { FhirDialogService } from './fhir-dialog.service';

describe('FhirDialogService', () => {
  let service: FhirDialogService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(FhirDialogService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
