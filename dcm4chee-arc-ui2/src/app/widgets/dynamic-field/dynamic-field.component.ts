import {ChangeDetectorRef, Component, ElementRef, EventEmitter, Input, OnInit, Output, ViewChild} from '@angular/core';
import {DynamicFieldService} from "./dynamic-field.service";
import * as _ from "lodash";
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
    warning = false;
    showRaw = false;
    Object = Object;
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
        console.log("elements",this.elements)

    }
    valueChanged(){
        if(this.type === 'array')
            this.onValueChange.emit(Object.keys(this.model).filter(key=>{return this.model[key];}));
        else
            this.onValueChange.emit(this.model);
    }
    showRawValues(){
        this.showRaw = !this.showRaw;
    }
    getObject(functionName){
        if(Array.isArray(this.checked)){
            this.checked = this.checked || [];
            this.checked.forEach(element =>{
                if(element != "")
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
        console.log("element",this.elements);
        console.log("element",this.model);
        console.log("checked",this.checked);
        if(this.checked && _.isArray(this.checked) && this.checked.length > 1){
            this.checked.forEach(c=>{
                let found = false;
                this.elements.forEach(e =>{
                    if(c === e[this.key]){
                        found = true;
                    }
                });
                if(!found){
                    this.warning = true;
                }
            });
        }
          this.detectChanges();
          if(this.type === 'array' && this.elementView && this.elementView.nativeElement){
              let height = this.elementView.nativeElement.offsetHeight;
              if(height > 200){
                  this.longMode = true;
                  this.detectChanges();
              }
          }
        },(err)=>{
          this.loader = false;
        });
    }
    detectChanges(){
        if(!(_.hasIn(this.ref, "destroyed") && _.get(this.ref,"destroyed"))){
            this.ref.detectChanges();
        }
    }
/*    update(){
        console.log("this.checked",this.checked);
        this.checked.forEach(element =>{
            if(element != "")
                this.model[element] = true;
        });
    }*/
    deleteElement(e){
        var index = this.checked.indexOf(e);
        if (index !== -1) this.checked.splice(index, 1);
        delete this.model[e];
        this.valueChanged();
    }
}
