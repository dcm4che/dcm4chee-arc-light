import { TestBed } from '@angular/core/testing';

import { UploadFilesService } from './upload-files.service';

describe('UploadFilesService', () => {
  let service: UploadFilesService;

  beforeEach(() => {
    TestBed.configureTestingModule({ teardown: { destroyAfterEach: false } });
    service = TestBed.inject(UploadFilesService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
