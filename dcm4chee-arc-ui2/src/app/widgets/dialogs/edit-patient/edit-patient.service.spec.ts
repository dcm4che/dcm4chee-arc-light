import { TestBed } from '@angular/core/testing';

import { EditPatientService } from './edit-patient.service';

describe('EditPatientService', () => {
  let service: EditPatientService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(EditPatientService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
  it("Should extract patient identifiers from string",()=>{
    expect(service.extractPatientIdentifiers("")).toEqual(undefined);
    expect(service.extractPatientIdentifiers(undefined)).toEqual(undefined);
    expect(service.extractPatientIdentifiers("test")).toEqual({
      PatientID:{
        text:"test",
        modelPath:"00100020.Value[0]",
        firstLevelCode:"00100020"
      }
    });
    expect(service.extractPatientIdentifiers("pie^^^issuerOfPID&universalentitiid&universalentitiidtype^TEXT")).toEqual({
      PatientID:{
        text:"pie",
        modelPath:"00100020.Value[0]",
        firstLevelCode:"00100020"
      },
      IssuerOfPatientID:{
        text:"issuerOfPID",
        modelPath:"00100021.Value[0]",
        firstLevelCode:"00100021"
      },
      UniversalEntityID: {
        text:"universalentitiid",
        modelPath:"00100024.Value[0][00400032].Value[0]",
        firstLevelCode:"00100024"
      },
      UniversalEntityIDType: {
        text:"universalentitiidtype",
        modelPath:"00100024.Value[0][00400033].Value[0]",
        firstLevelCode:"00100024"
      },
      TypeofPatientID:{
        text:"typeOfPID",
        modelPath:"00100022.Value[0]",
        firstLevelCode:"00100022"
      }
    });
    expect(service.extractPatientIdentifiers("pie^^^issuerOfPID")).toEqual({
      PatientID:{
        text:"pie",
        modelPath:"00100020.Value[0]",
        firstLevelCode:"00100020"
      },
      IssuerOfPatientID:{
        text:"issuerOfPID",
        modelPath:"00100021.Value[0]",
        firstLevelCode:"00100021"
      }
    });
    expect(service.extractPatientIdentifiers("pie^^^issuerOfPID&universalentitiid")).toEqual({
      PatientID:{
        text:"pie",
        modelPath:"00100020.Value[0]",
        firstLevelCode:"00100020"
      },
      IssuerOfPatientID:{
        text:"issuerOfPID",
        modelPath:"00100021.Value[0]",
        firstLevelCode:"00100021"
      },
      UniversalEntityID: {
        text:"universalentitiid",
        modelPath:"00100024.Value[0][00400032].Value[0]",
        firstLevelCode:"00100024"
      }
    });
    expect(service.extractPatientIdentifiers("pie^^^issuerOfPID&universalentitiid&universalentitiidtype")).toEqual({
      PatientID:{
        text:"pie",
        modelPath:"00100020.Value[0]",
        firstLevelCode:"00100020"
      },
      IssuerOfPatientID:{
        text:"issuerOfPID",
        modelPath:"00100021.Value[0]",
        firstLevelCode:"00100021"
      },
      UniversalEntityID: {
        text:"universalentitiid",
        modelPath:"00100024.Value[0][00400032].Value[0]",
        firstLevelCode:"00100024"
      },
      UniversalEntityIDType: {
        text:"universalentitiidtype",
        modelPath:"00100024.Value[0][00400033].Value[0]",
        firstLevelCode:"00100024"
      }
    });
    expect(service.extractPatientIdentifiers("pie^^^issuerOfPID&&universalentitiidtype^typeOfPID")).toEqual({
      PatientID:{
        text:"pie",
        modelPath:"00100020.Value[0]",
        firstLevelCode:"00100020"
      },
      IssuerOfPatientID:{
        text:"issuerOfPID",
        modelPath:"00100021.Value[0]",
        firstLevelCode:"00100021"
      },
      UniversalEntityIDType: {
        text:"universalentitiidtype",
        modelPath:"00100024.Value[0][00400033].Value[0]",
        firstLevelCode:"00100024"
      },
      TypeofPatientID:{
        text:"typeOfPID",
        modelPath:"00100022.Value[0]",
        firstLevelCode:"00100022"
      }
    });
    expect(service.extractPatientIdentifiers("pie^^^&universalentitiid&universalentitiidtype^typeOfPID")).toEqual({
      PatientID:{
        text:"pie",
        modelPath:"00100020.Value[0]",
        firstLevelCode:"00100020"
      },
      UniversalEntityID: {
        text:"universalentitiid",
        modelPath:"00100024.Value[0][00400032].Value[0]",
        firstLevelCode:"00100024"
      },
      UniversalEntityIDType: {
        text:"universalentitiidtype",
        modelPath:"00100024.Value[0][00400033].Value[0]",
        firstLevelCode:"00100024"
      },
      TypeofPatientID:{
        text:"typeOfPID",
        modelPath:"00100022.Value[0]",
        firstLevelCode:"00100022"
      }
    });
  });
});
