import { TestBed, inject } from '@angular/core/testing';

import { DeviceConfiguratorService } from './device-configurator.service';

describe('DeviceConfiguratorService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [DeviceConfiguratorService]
    });
  });

  it('should ...', inject([DeviceConfiguratorService], (service: DeviceConfiguratorService) => {
    expect(service).toBeTruthy();
  }));
});
