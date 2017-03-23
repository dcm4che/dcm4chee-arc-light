/**
 * Created by shefki on 9/20/16.
 */
import {Component, Input, ElementRef, OnInit, ComponentFactoryResolver} from "@angular/core";
import {FormGroup, FormControl, FormArray} from "@angular/forms";
import {DynamicFormComponent} from "./dynamic-form.component";
import {FormService} from "../../helpers/form/form.service";
import {FormElement} from "../../helpers/form/form-element";
import * as _ from "lodash";

@Component({
    selector:'df-element',
    templateUrl:'./dynamic-form-element.component.html'
})
export class DynamicFormElementComponent{

    @Input() formelement:FormElement<any>;
    @Input() formelements:FormElement<any>[];
    @Input() form:FormGroup;

    constructor(private formservice:FormService, private formcomp:DynamicFormComponent, dcl: ComponentFactoryResolver, elementRef: ElementRef){
        // dcl.resolveComponentFactory(DynamicFormComponent);
    }
    get isValid(){
        return this.form.controls[this.formelement.key].valid;
    }

    addElement(element:any, formpart:FormControl[]){
        // console.log("in addelement element",element);
        // console.log("in addelement element",element[0]);
        var globarForm = this.formcomp.getForm();
        var valueObject = globarForm.value;
        element.push(element[0]);
        // console.log("in addelement element",element);
        // console.log("form",this.form.controls["arraytest"]["controls"]);
        // console.log("this.formelements",this.formelements);
        // this.form.controls["arraytest"]["controls"].push(this.form.controls["arraytest"]["controls"][0]);
        console.log("formpart",formpart);
        formpart["options"].push(formpart["options"][0]);
        // let globalform = this.formcomp.getForm();
        // console.log("globalform=",globalform.value);

        this.form = this.formservice.toFormGroup(this.formelements);
        // console.log("globalform",globalform);
        // console.log("this.form",this.form);
        this.formcomp.setForm(this.form);
        this.formcomp.setFormModel(valueObject);
    }
    addArrayElement(element:any, formpart:FormControl[], form:any){
        console.log("fompart",formpart);
        console.log("element",element);
        formpart = formpart || [];
        element = element || [];
        element.push("");
        var globarForm = this.formcomp.getForm();
        formpart.push(new FormControl(""));
        var valueObject = globarForm.value;
        this.form = this.formservice.toFormGroup(this.formelements);
        this.form.patchValue(valueObject);
        this.formcomp.setForm(this.form);
        this.formcomp.setFormModel(valueObject);
    }
    removeArrayElement(element:any, i:number, form:any){
        if(element.value.length > i){
            var globarForm = this.formcomp.getForm();
            var valueObject = globarForm.value;
            element.value.splice(i, 1);
            form.controls[element.key].value.splice(i,1);
            this.form = this.formservice.toFormGroup(this.formelements);
            // console.log("valueObject",valueObject);
            // form.patchValue(valueObject);
            this.formcomp.setForm(this.form);
            this.formcomp.setFormModel(valueObject);
        }
    }
    checkboxChange(e, form, formelement){
        if(e.target.checked && !_.hasIn(form.controls[formelement.key].value, e.target.defaultValue)){
            form.controls[formelement.key].value.push(e.target.defaultValue);
        }else{
            console.log("findeindex",_.indexOf(form.controls[formelement.key].value,e.target.defaultValue));
            form.controls[formelement.key].value.splice(_.indexOf(form.controls[formelement.key].value,e.target.defaultValue),1);
        }
    }
}
