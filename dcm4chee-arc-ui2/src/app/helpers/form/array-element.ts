import {FormElement} from './form-element';
/**
 * Created by shefki on 9/20/16.
 */

export class ArrayElement extends FormElement<string>{
    controlType = 'arrayelement';
    type: string;

    constructor(options: {} = {}){
        super(options);
        this.type = options['type'] || '';
    }
}