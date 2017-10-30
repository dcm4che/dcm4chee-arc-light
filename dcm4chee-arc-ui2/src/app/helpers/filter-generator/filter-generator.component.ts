import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';

@Component({
  selector: 'filter-generator',
  templateUrl: './filter-generator.component.html'
})
export class FilterGeneratorComponent implements OnInit {

  @Input() filterObject;
  @Output() submit  = new EventEmitter();
  filterModel = {dicomDeviceName:""};
  constructor() { }

  ngOnInit() {
  }
  submitEmit(filter){
      this.submit.emit(this.filterModel);
  }
}
