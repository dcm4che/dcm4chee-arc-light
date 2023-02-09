import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PatientNamePickerComponent } from './patient-name-picker.component';

describe('PatientNamePickerComponent', () => {
  let component: PatientNamePickerComponent;
  let fixture: ComponentFixture<PatientNamePickerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ PatientNamePickerComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(PatientNamePickerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
