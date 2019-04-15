import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { DcmDropDownComponent } from './dcm-drop-down.component';

describe('DcmDropDownComponent', () => {
  let component: DcmDropDownComponent;
  let fixture: ComponentFixture<DcmDropDownComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ DcmDropDownComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(DcmDropDownComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
