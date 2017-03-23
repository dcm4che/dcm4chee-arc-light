/**
 * Created by shefki on 9/20/16.
 */

export class FormElement<T>{
    value: T;
    key: string;
    label: string;
    required: boolean;
    order: number;
    description:string;
    controlType: string;
    constructor(options: {
        value?: T,
        key?: string,
        label?: string,
        required?: boolean,
        order?: number,
        description?: string,
        controlType?: string
    } = {}) {
        this.value = options.value;
        this.key = options.key || '';
        this.label = options.label || '';
        this.required = !!options.required;
        this.order = options.order === undefined ? 1 : options.order;
        this.description = options.description || '';
        this.controlType = options.controlType || '';
    }
}