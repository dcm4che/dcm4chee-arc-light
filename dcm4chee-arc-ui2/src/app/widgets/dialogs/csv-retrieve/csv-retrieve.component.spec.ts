import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { CsvRetrieveComponent } from './csv-retrieve.component';

describe('CsvRetrieveComponent', () => {
  let component: CsvRetrieveComponent;
  let fixture: ComponentFixture<CsvRetrieveComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ CsvRetrieveComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CsvRetrieveComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
