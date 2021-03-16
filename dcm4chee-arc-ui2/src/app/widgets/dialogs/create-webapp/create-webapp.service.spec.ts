import { TestBed } from '@angular/core/testing';

import { CreateWebappService } from './create-webapp.service';

describe('CreateWebappService', () => {
  let service: CreateWebappService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(CreateWebappService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
