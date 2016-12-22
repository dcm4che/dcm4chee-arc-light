/* tslint:disable:no-unused-variable */
import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';

import { EditMwlComponent } from './edit-mwl.component';

describe('EditMwlComponent', () => {
  let component: EditMwlComponent;
  let fixture: ComponentFixture<EditMwlComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ EditMwlComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(EditMwlComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
