import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {j4care} from "../j4care.service";

@Component({
  selector: 'filter-generator',
  templateUrl: './filter-generator.component.html'
})
export class FilterGeneratorComponent implements OnInit {

  @Input() schema;
  @Input() model;
  @Output() submit  = new EventEmitter();
  constructor() { }

  ngOnInit() {
  }
  submitEmit(){
      this.model = j4care.clearEmptyObject(this.model);
      this.submit.emit(this.model);
  }
}
