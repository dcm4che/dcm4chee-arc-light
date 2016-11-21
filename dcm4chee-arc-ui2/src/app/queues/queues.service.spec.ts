/* tslint:disable:no-unused-variable */

import { TestBed, async, inject } from '@angular/core/testing';
import { QueuesService } from './queues.service';

describe('Service: Queues', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [QueuesService]
    });
  });

  it('should ...', inject([QueuesService], (service: QueuesService) => {
    expect(service).toBeTruthy();
  }));
});
