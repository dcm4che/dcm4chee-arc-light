import {FormElement} from './form-element';
/**
 * Created by shefki on 9/26/16.
 */
export class ArrayObject extends FormElement<string>{
    controlType = 'arrayobject';
    options: {childe: FormElement<string>}[] = [];

    constructor(options: {} = {}){
        super(options);
        this.options = options['options'] || [];
    }
}