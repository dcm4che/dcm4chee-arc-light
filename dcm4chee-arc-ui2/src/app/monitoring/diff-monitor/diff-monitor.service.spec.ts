import { TestBed, inject } from '@angular/core/testing';

import { DiffMonitorService } from './diff-monitor.service';

describe('DiffMonitorService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [DiffMonitorService]
    });
  });

  it('should be created', inject([DiffMonitorService], (service: DiffMonitorService) => {
    expect(service).toBeTruthy();
  }));
});
