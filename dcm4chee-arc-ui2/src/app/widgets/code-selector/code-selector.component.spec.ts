import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { CodeSelectorComponent } from './code-selector.component';

describe('CodeSelectorComponent', () => {
  let component: CodeSelectorComponent;
  let fixture: ComponentFixture<CodeSelectorComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ CodeSelectorComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CodeSelectorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
