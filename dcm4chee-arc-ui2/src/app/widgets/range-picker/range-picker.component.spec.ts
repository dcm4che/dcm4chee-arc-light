import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { RangePickerComponent } from './range-picker.component';

describe('RangePickerComponent', () => {
  let component: RangePickerComponent;
  let fixture: ComponentFixture<RangePickerComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ RangePickerComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(RangePickerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
