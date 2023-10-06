import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PersonNamePickerComponent } from './person-name-picker.component';

describe('PatientNamePickerComponent', () => {
  let component: PersonNamePickerComponent;
  let fixture: ComponentFixture<PersonNamePickerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
    declarations: [PersonNamePickerComponent],
    teardown: { destroyAfterEach: false }
})
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(PersonNamePickerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
