import { Pipe, PipeTransform } from '@angular/core';
declare var DCM4CHE: any;
@Pipe({
  name: 'attributeNameOf'
})
export class AttributeNameOfPipe implements PipeTransform {
    constructor() {

    }
  transform(value: any, args?: any): any {
     return DCM4CHE.elementName.forTag(value);
  }

}
