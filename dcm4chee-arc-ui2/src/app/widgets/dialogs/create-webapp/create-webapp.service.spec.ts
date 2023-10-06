import { TestBed } from '@angular/core/testing';

import { CreateWebappService } from './create-webapp.service';

describe('CreateWebappService', () => {
  let service: CreateWebappService;

  beforeEach(() => {
    TestBed.configureTestingModule({ teardown: { destroyAfterEach: false } });
    service = TestBed.inject(CreateWebappService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
