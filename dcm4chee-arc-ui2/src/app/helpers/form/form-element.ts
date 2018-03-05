/**
 * Created by shefki on 9/20/16.
 */

export class FormElement<T>{
    value: T;
    key: string;
    label: string;
    validation: any;
    order: number;
    description: string;
    controlType: string;
    show: boolean;
    format: string;
    constructor(options: {
        value?: T,
        key?: string,
        label?: string,
        validation?: any,
        order?: number,
        description?: string,
        controlType?: string,
        show?: boolean;
        format?: string;
    } = {}) {
        this.value = options.value;
        this.key = options.key || '';
        this.label = options.label || '';
        this.validation = options.validation;
        this.order = options.order === undefined ? 1 : options.order;
        this.description = options.description || '';
        this.controlType = options.controlType || '';
        this.show = options.show || false;
        this.format = options.format || undefined;
    }
}