import {ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {DynamicFieldService} from "./dynamic-field.service";

@Component({
  selector: 'dynamic-field',
  templateUrl: './dynamic-field.component.html',
  styleUrls: ['./dynamic-field.component.css']
})
export class DynamicFieldComponent implements OnInit {

    loader = false;
    elements;
    model = {};
    key = '';
    @Input() checked;
    @Input() mode;
    @Output() onValueChange = new EventEmitter();
    constructor(
        private service:DynamicFieldService,
        private ref: ChangeDetectorRef
    ) { }

    ngOnInit() {
        switch (this.mode){
            case 'dcmAETitle':
                this.getObject('getAets');
                this.key = 'dicomAETitle';
            break;
            case 'dicomDeviceName':
                this.getObject('getDevice');
                this.key = 'dicomDeviceName';
            break;
            case 'hl7ApplicationName':
                this.getObject('getHl7');
                this.key = 'hl7ApplicationName';
            break;
        }
      this.checked = this.checked || [];
      this.checked.forEach(element =>{
          this.model[element] = true;
      })
    }
    valueChanged(){
      this.onValueChange.emit(Object.keys(this.model).filter(key=>{return this.model[key];}));
    }
    getObject(functionName){
      this.loader = true;
      this.service[functionName]().subscribe((res)=>{
          this.elements = res;
          this.loader = false;
          this.ref.detectChanges();
      },(err)=>{
          this.loader = false;
      });
    }

}
