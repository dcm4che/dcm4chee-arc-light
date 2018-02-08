import {ChangeDetectorRef, Component, ElementRef, EventEmitter, Input, OnInit, Output, ViewChild} from '@angular/core';
import {DynamicFieldService} from "./dynamic-field.service";

@Component({
  selector: 'dynamic-field',
  templateUrl: './dynamic-field.component.html',
  styleUrls: ['./dynamic-field.component.scss']
})
export class DynamicFieldComponent implements OnInit {

    loader = false;
    elements;
    model = {};
    key = '';
    longMode = false;
    search = '';
    @Input() checked;
    @Input() mode;
    @Output() onValueChange = new EventEmitter();
    @Input() type;
    @ViewChild('checkboxes') elementView: ElementRef;
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

    }
    valueChanged(){
        if(this.type === 'array')
            this.onValueChange.emit(Object.keys(this.model).filter(key=>{return this.model[key];}));
        else
            this.onValueChange.emit(this.model);
    }
    getObject(functionName){
        if(Array.isArray(this.checked)){
            this.checked = this.checked || [];
            this.checked.forEach(element =>{
                this.model[element] = true;
            });
            this.type = "array";
        }else{
            this.checked = this.checked || '';
            this.model = this.checked;
            this.type = "string";
        }
        this.loader = true;
        this.service[functionName]().subscribe((res)=>{
          this.elements = res;
          this.loader = false;
          this.ref.detectChanges();
          if(this.type === 'array'){
              let height = this.elementView.nativeElement.offsetHeight;
              if(height > 200){
                  this.longMode = true;
                  this.ref.detectChanges();
              }
          }
        },(err)=>{
          this.loader = false;
        });
    }

}
