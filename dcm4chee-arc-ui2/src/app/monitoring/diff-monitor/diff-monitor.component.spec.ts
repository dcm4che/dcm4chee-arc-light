import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { DiffMonitorComponent } from './diff-monitor.component';

describe('DiffMonitorComponent', () => {
  let component: DiffMonitorComponent;
  let fixture: ComponentFixture<DiffMonitorComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ DiffMonitorComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(DiffMonitorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
