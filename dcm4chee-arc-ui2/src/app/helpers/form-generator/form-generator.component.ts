import {Component, Input, OnInit} from '@angular/core';
import {FormElement} from "../form/form-element";
import {FormGeneratorService} from "./form-generator.service";
import {NgForm} from "@angular/forms";
import * as _ from 'lodash-es';

@Component({
  selector: 'form-generator',
  templateUrl: './form-generator.component.html',
  styleUrls: ['./form-generator.component.scss']
})
export class FormGeneratorComponent implements OnInit{
  @Input() schema:FormElement<any>[];

  @Input() showLabels:boolean = false;
  _ = _;
  constructor(
      private service:FormGeneratorService
  ){}
  model:any = {};
  ngOnInit(): void {
    //this.model = this.service.convertSchemaToFormGroup(this.schema);
  }

  onSubmit(form: NgForm) {
    console.log("form",form);
  }

  modelChange(element: FormElement<any>, $event: any) {
    console.log("element",element);
    console.log("e",$event)
    console.log("model",this.model);
  }
}
