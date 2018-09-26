import { TestBed, inject } from '@angular/core/testing';

import { StorageVerificationService } from './storage-verification.service';

describe('StorageVerificationService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [StorageVerificationService]
    });
  });

  it('should be created', inject([StorageVerificationService], (service: StorageVerificationService) => {
    expect(service).toBeTruthy();
  }));
});
