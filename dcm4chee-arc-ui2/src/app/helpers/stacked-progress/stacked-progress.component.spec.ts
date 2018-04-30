import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { StackedProgressComponent } from './stacked-progress.component';

describe('StackedProgressComponent', () => {
  let component: StackedProgressComponent;
  let fixture: ComponentFixture<StackedProgressComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ StackedProgressComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(StackedProgressComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
