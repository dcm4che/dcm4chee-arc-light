import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ExternalRetrieveComponent } from './external-retrieve.component';

describe('ExternalRetrieveComponent', () => {
  let component: ExternalRetrieveComponent;
  let fixture: ComponentFixture<ExternalRetrieveComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ExternalRetrieveComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ExternalRetrieveComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
