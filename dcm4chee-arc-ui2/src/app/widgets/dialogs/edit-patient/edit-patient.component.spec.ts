/* tslint:disable:no-unused-variable */
import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';

import { EditPatientComponent } from './edit-patient.component';

describe('EditPatientComponent', () => {
  let component: EditPatientComponent;
  let fixture: ComponentFixture<EditPatientComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ EditPatientComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(EditPatientComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
