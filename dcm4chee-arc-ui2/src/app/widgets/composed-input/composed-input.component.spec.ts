import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ComposedInputComponent } from './composed-input.component';

describe('ComposedInputComponent', () => {
  let component: ComposedInputComponent;
  let fixture: ComponentFixture<ComposedInputComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [ComposedInputComponent]
    });
    fixture = TestBed.createComponent(ComposedInputComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it("Should join the composed fields",()=>{
    component.inputSize = 5;
    component.joins = "^"
    component.modelArray = [
        "TestName",
        "TestSurname"
    ]
    expect(component.getComposedValue()).toEqual("TestName^TestSurname");
    component.modelArray = [
        "TestName",
        "TestSurname",
        "",
        "",
        ""
    ]
    expect(component.getComposedValue()).toEqual("TestName^TestSurname");

  });
});
