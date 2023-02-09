import { TestBed } from '@angular/core/testing';

import { PatientNamePickerService } from './patient-name-picker.service';
import {AppService} from "../../app.service";
class MyServiceDependencyStub {
}
describe('PatientNamePickerService', () => {
  let service: PatientNamePickerService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers:[
        {provide:AppService, useClass:MyServiceDependencyStub}
      ]
    });
    service = TestBed.inject(PatientNamePickerService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it("should convert formatted person name to dicom",()=>{
      expect(service.convertPNameFromFormattedToDicomForm(
          "Rev. John Adams, B.A. M.Div.",
          `{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME}, {NAME-SUFFIX}`
      )).toBe(
          "Adams^John^^Rev.^B.A. M.Div."
      );
  });
  it("should convert dicom person name to formatted person name",()=>{
      expect(service.convertPNameFromDicomFormToFormatted(
          "Adams^John Robert Quincy^^Rev.^B.A. M.Div.",
          `{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME}, {NAME-SUFFIX}`
      )).toBe(
          "Rev. John Robert Quincy Adams, B.A. M.Div."
      );
  });
});
