import { TestBed } from '@angular/core/testing';

import { PersonNamePickerService } from './person-name-picker.service';
import {AppService} from "../../app.service";
class MyServiceDependencyStub {
}
describe('PatientNamePickerService', () => {
  let service: PersonNamePickerService;

  beforeEach(() => {
    TestBed.configureTestingModule({
    providers: [
        { provide: AppService, useClass: MyServiceDependencyStub }
    ],
    teardown: { destroyAfterEach: false }
});
    service = TestBed.inject(PersonNamePickerService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it("should convert formatted person name to dicom",()=>{
      expect(service.convertPNameFromFormattedToDicomForm(
          "Rev. John test Adams, B.A. M.Div.",
          `{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME}, {NAME-SUFFIX}`
      )).toBe(
          "Adams^John^test^Rev.^B.A. M.Div."
      );
      expect(service.convertPNameFromFormattedToDicomForm(
          "Rev. John Adams, B.A. M.Div.",
          `{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME}, {NAME-SUFFIX}`
      )).toBe(
          "Adams^John^^Rev.^B.A. M.Div."
      );
      expect(service.convertPNameFromFormattedToDicomForm(
          "Rev. John Adams",
          `{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME}, {NAME-SUFFIX}`
      )).toBe(
          "Adams^John^^Rev.^"
      );
      expect(service.convertPNameFromFormattedToDicomForm(
          "John Adams M.Div.",
          `{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME}, {NAME-SUFFIX}`
      )).toBe(
          "Adams^John^^M.Div.^"
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
