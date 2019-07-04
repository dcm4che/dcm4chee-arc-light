import { TestBed, inject } from '@angular/core/testing';

import { MetricsService } from './metrics.service';

describe('MetricsService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [MetricsService]
    });
  });

  it('should be created', inject([MetricsService], (service: MetricsService) => {
    expect(service).toBeTruthy();
  }));
});
