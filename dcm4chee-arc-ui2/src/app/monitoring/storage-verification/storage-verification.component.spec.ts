import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { StorageVerificationComponent } from './storage-verification.component';

describe('StorageVerificationComponent', () => {
  let component: StorageVerificationComponent;
  let fixture: ComponentFixture<StorageVerificationComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ StorageVerificationComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(StorageVerificationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
