import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ModifiedWidgetComponent } from './modified-widget.component';

describe('ModifiedWidgetComponent', () => {
  let component: ModifiedWidgetComponent;
  let fixture: ComponentFixture<ModifiedWidgetComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ModifiedWidgetComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ModifiedWidgetComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
