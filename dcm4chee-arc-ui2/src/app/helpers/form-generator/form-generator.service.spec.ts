import { TestBed } from '@angular/core/testing';

import { FormGeneratorService } from './form-generator.service';
import {FormElement} from "../form/form-element";
import {ArrayElement} from "../form/array-element";
import {FormBuilder} from "@angular/forms";

describe('FormGeneratorService', () => {
  let service: FormGeneratorService;
  const fb = new FormBuilder();
  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(FormGeneratorService);

  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
  it("should convert FormSchema to  Form group",()=>{
/*    const formSchema:FormElement<any>[] = [
        new ArrayElement({

        })
    ];
    const fbGroup = {
      name:""
    };
    expect(service.convertSchemaToFormGroup(formSchema)).toEqual(fb.group(fbGroup));*/
  });
});
