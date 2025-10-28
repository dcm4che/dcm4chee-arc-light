import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';

import { IssuerSelectorComponent } from './issuer-selector.component';
import {AppService} from '../../app.service';
class MockAppService {
  global:any = {};
  someMethod() { return 'mocked value'; }
}
describe('IssuerSelectorComponent', () => {
  let component: IssuerSelectorComponent;
  let fixture: ComponentFixture<IssuerSelectorComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
        declarations: [],
          providers:[{ provide: AppService, useClass: MockAppService }],
        teardown: { destroyAfterEach: false }
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(IssuerSelectorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
    it('Should handle issuers with no keys gracefully', () => {
        component.issuers = [
                { key: 'AccessionNumber' },
                { key: 'IssuerOfAccessionNumberSequence.LocalNamespaceEntityID' },
                { key: 'IssuerOfAccessionNumberSequence.UniversalEntityID' },
                { key: 'IssuerOfAccessionNumberSequence.UniversalEntityIDType' }
            ];
        component.filterModel = {
            'AccessionNumber':'',
            'IssuerOfAccessionNumberSequence.LocalNamespaceEntityID':'',
            'IssuerOfAccessionNumberSequence.UniversalEntityID':'',
            'IssuerOfAccessionNumberSequence.UniversalEntityIDType':''
        };
        component.initSplitters();
        expect(component.splitters).toEqual(['^','^','^']);
    });
    it('Should extract all parts from a string that contains a delimiter', () => {
        expect(component.extractModelsFromString(
            'tes^ba^df^bbs',
            {
                'AccessionNumber':'',
                'IssuerOfAccessionNumberSequence.LocalNamespaceEntityID':'',
                'IssuerOfAccessionNumberSequence.UniversalEntityID':'',
                'IssuerOfAccessionNumberSequence.UniversalEntityIDType':''
            },
            [
                { key: 'AccessionNumber' },
                { key: 'IssuerOfAccessionNumberSequence.LocalNamespaceEntityID' },
                { key: 'IssuerOfAccessionNumberSequence.UniversalEntityID' },
                { key: 'IssuerOfAccessionNumberSequence.UniversalEntityIDType' }
            ],
            ['^','^','^']
            )).toEqual({
            'AccessionNumber':'tes',
            'IssuerOfAccessionNumberSequence.LocalNamespaceEntityID':'ba',
            'IssuerOfAccessionNumberSequence.UniversalEntityID':'df',
            'IssuerOfAccessionNumberSequence.UniversalEntityIDType':'bbs'
        });
    });
    it('Should extract all parts from a string that contains a delimiter', () => {
        expect(component.extractModelsFromString(
            'b^^^as&df&basd',
            {
                'PatientID':'',
                'IssuerOfPatientID':'',
                'IssuerOfPatientIDQualifiersSequence.UniversalEntityID':'',
                'IssuerOfPatientIDQualifiersSequence.UniversalEntityIDType':''
            },
            [
                { key: 'PatientID' },
                { key: 'IssuerOfPatientID' },
                { key: 'IssuerOfPatientIDQualifiersSequence.UniversalEntityID' },
                { key: 'IssuerOfPatientIDQualifiersSequence.UniversalEntityIDType' }
            ],
            ['^^^','&','&']
            )).toEqual({
            'PatientID':'b',
            'IssuerOfPatientID':'as',
            'IssuerOfPatientIDQualifiersSequence.UniversalEntityID':'df',
            'IssuerOfPatientIDQualifiersSequence.UniversalEntityIDType':'basd'
        });
    });
    it('Should extract all parts from a string that contains a delimiter', () => {
        expect(component.extractModelsFromString(
            'test',
            {
                'PatientID':'',
                'IssuerOfPatientID':'',
                'IssuerOfPatientIDQualifiersSequence.UniversalEntityID':'',
                'IssuerOfPatientIDQualifiersSequence.UniversalEntityIDType':''
            },
            [
                { key: 'PatientID' },
                { key: 'IssuerOfPatientID' },
                { key: 'IssuerOfPatientIDQualifiersSequence.UniversalEntityID' },
                { key: 'IssuerOfPatientIDQualifiersSequence.UniversalEntityIDType' }
            ],
            ['^^^','&','&']
            )).toEqual({
            'PatientID':'test',
            'IssuerOfPatientID':'',
            'IssuerOfPatientIDQualifiersSequence.UniversalEntityID':'',
            'IssuerOfPatientIDQualifiersSequence.UniversalEntityIDType':''
        });
    });
});
