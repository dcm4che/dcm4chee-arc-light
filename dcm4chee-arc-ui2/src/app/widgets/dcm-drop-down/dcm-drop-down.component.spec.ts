import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';

import { DcmDropDownComponent } from './dcm-drop-down.component';
import {ClickOutsideDirective} from '../../helpers/click-outside.directive';
import {FormsModule} from '@angular/forms';
import {SearchPipe} from '../../pipes/search.pipe';
import {BrowserAnimationsModule} from "@angular/platform-browser/animations";
import {ArrayToStringPipe} from '../../pipes/array-to-string.pipe';

describe('DcmDropDownComponent', () => {
  let component: DcmDropDownComponent;
  let fixture: ComponentFixture<DcmDropDownComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
        declarations: [],
        imports: [FormsModule, BrowserAnimationsModule],
        teardown: { destroyAfterEach: false }
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(DcmDropDownComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
