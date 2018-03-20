import { TestBed, inject } from '@angular/core/testing';

import { CsvRetrieveService } from './csv-retrieve.service';

describe('CsvRetrieveService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CsvRetrieveService]
    });
  });

  it('should be created', inject([CsvRetrieveService], (service: CsvRetrieveService) => {
    expect(service).toBeTruthy();
  }));
});
