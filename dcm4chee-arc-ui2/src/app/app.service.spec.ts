/* tslint:disable:no-unused-variable */

import { TestBed, async, inject } from '@angular/core/testing';
import { AppService } from './app.service';

describe('Service: App', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AppService]
    });
  });

  it('should ...', inject([AppService], (service: AppService) => {
    expect(service).toBeTruthy();
  }));
});
