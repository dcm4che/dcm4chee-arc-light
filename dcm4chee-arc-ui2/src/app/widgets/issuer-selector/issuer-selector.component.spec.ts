import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';

import { IssuerSelectorComponent } from './issuer-selector.component';

describe('IssuerSelectorComponent', () => {
  let component: IssuerSelectorComponent;
  let fixture: ComponentFixture<IssuerSelectorComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [ IssuerSelectorComponent ]
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
});
