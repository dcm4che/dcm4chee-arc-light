import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { DcmSelectComponent } from './dcm-select.component';

describe('DcmSelectComponent', () => {
  let component: DcmSelectComponent;
  let fixture: ComponentFixture<DcmSelectComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ DcmSelectComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(DcmSelectComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
