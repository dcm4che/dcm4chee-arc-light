import {Component, EventEmitter, forwardRef, Input, Output} from '@angular/core';
import {ControlValueAccessor, NG_VALUE_ACCESSOR} from "@angular/forms";


@Component({
    selector: 'composed-input',
    templateUrl: './composed-input.component.html',
    styleUrls: ['./composed-input.component.scss'],
    standalone: false
})
export class ComposedInputComponent/* implements ControlValueAccessor */{
  modelArray = [];
  joins:string|string[];
  _model:any;
  @Input('model')
  set model(value){
    console.log("value",value);
    this._model = value;
    this.mapValueToModelArray(value);
  }
  get model(){
    return this._model;
  }

  @Input() placeholder:string;
  @Input() inputSize:number;
  @Input() placeholderElements:string[];
  @Output() modelChange = new EventEmitter();
  protected readonly Array = Array;

  @Input() set joinString(value:string){  //join string can be a single string or a collection of strings separated with "|", where all the between strings must be declared.
    if(value && value.indexOf("|") > -1){
      this.joins = value.split("|");
    }else{
      this.joins = value;
    }
  }

  mapValueToModelArray(value){
    try{
      if(value){
        if(typeof this.joins === "string"){ // If the joins parameter doesn't contain multiple joins and therefor is a string and not an array of multiple strings/joins
          const splitedValue = value.split(this.joins); // Simply split the given string by the join parameter ( for example by ^ )
          if(!this.modelArray){                         // Check if the first string to the widget comes from outside ( therefore the modelArray is still empty ) and initiate
            this.modelArray = Array(this.inputSize);
          }
          for(let i=0;i < this.inputSize;i++){  // map the splitted strings with the internal model
              this.modelArray[i] = splitedValue[i] || '';
          }
        }else if(typeof this.joins === "object" && this.joins.length === this.inputSize-1) { // If the joins parameters contains more than one join element
          let tempValue = value;
          for(let i=0;i < this.inputSize;i++){
            const splitedValue = tempValue.split(this.joins[i]);   // Split the string by the first join element
            this.modelArray[i] = splitedValue[0] || '';            // take the first element from the array
            splitedValue.splice(0,1);                              // remove the first element that we already added to the internal model
            tempValue = splitedValue.join("");                     // join the rest of the string and repeat the same process
          }
        }
      }
    }catch (e) {
      console.error(e)
    }
  }
  onchange(e){
    console.log("this",this.modelArray);
    console.log("composed",this.getComposedValue());
    this.modelChange.emit(this.getComposedValue())
  }

  getComposedValue() {
    try{
      let composedValue = "";
      this.modelArray.forEach((value, index)=>{
        composedValue += value + (
                (this.modelArray[index+1] && index+1 < this.inputSize && this.joins) ?
                  (this.joins[index] || this.joins)
                :
                  ''
            )
      });
      return composedValue;
    }catch (e) {
      return "";
    }
  }
}
