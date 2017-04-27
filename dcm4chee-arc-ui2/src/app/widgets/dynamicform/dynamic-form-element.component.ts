/**
 * Created by shefki on 9/20/16.
 */
import {Component, Input, ElementRef, OnInit, ComponentFactoryResolver} from "@angular/core";
import {FormGroup, FormControl, FormArray} from "@angular/forms";
import {DynamicFormComponent} from "./dynamic-form.component";
import {FormService} from "../../helpers/form/form.service";
import {FormElement} from "../../helpers/form/form-element";
import * as _ from "lodash";
import {Router} from "@angular/router";

@Component({
    selector:'df-element',
    templateUrl:'./dynamic-form-element.component.html'
})
export class DynamicFormElementComponent{

    @Input() formelement:FormElement<any>;
    @Input() formelements:FormElement<any>[];
    @Input() form:FormGroup;
    @Input() partSearch:string;
    // activetab = "tab_1";
    constructor(private formservice:FormService, private formcomp:DynamicFormComponent, dcl: ComponentFactoryResolver, elementRef: ElementRef, private router:Router){
        // dcl.resolveComponentFactory(DynamicFormComponent);
    }
    get isValid(){
        return this.form.controls[this.formelement.key].valid;
    }

    addElement(element:any, formpart:FormControl[]){
        var globalForm = this.formcomp.getForm();
        var valueObject = globalForm.value;
        element.push(element[0]);
        formpart["options"].push(formpart["options"][0]);
        this.form = this.formservice.toFormGroup(this.formelements);
        this.formcomp.setForm(this.form);
        this.formcomp.setFormModel(valueObject);
    }
    clone(formelement,controls){
        console.log("formelement",formelement);
    }
    addArrayElement(element:any, formpart:FormControl[], form:any){
        formpart = formpart || [];
        element = element || [];
        element.push("");
        var globalForm = this.formcomp.getForm();
        formpart.push(new FormControl(""));
        var valueObject = globalForm.value;
        this.form = this.formservice.toFormGroup(this.formelements);
        this.form.patchValue(valueObject);
        this.formcomp.setForm(this.form);
        this.formcomp.setFormModel(valueObject);
    }
    removeArrayElement(element:any, i:number, form:any){
        if(element.value.length > i){
            //Remove from react form
            (<FormArray>this.form.controls[element.key]).removeAt(i);
            //reflect the changes to the dome
            _.forEach(this.formelements,(m,j)=>{
                if(m.key === element.key){
                    this.formelements[j].value = this.form.value[element.key];
                }
            });
        }
    }
    checkboxChange(e, form, formelement){
        if(e.target.checked && !_.hasIn(form.controls[formelement.key].value, e.target.defaultValue)){
            form.controls[formelement.key].value.push(e.target.defaultValue);
        }else{
            form.controls[formelement.key].value.splice(_.indexOf(form.controls[formelement.key].value,e.target.defaultValue),1);
        }
    }
    navigateTo(e){
        if(e != '-'){
            this.router.navigateByUrl(e);
        }
    }

    toggleTab(orderId){
        var globalForm = this.formcomp.getForm();
        var valueObject = globalForm.value;

        _.forEach(this.formelements,(m,i)=>{
           if(Math.floor(m.order) === orderId+1){
               if(m.show === true){
                   m.show = false;
               }else{
                   m.show = true;
               }
           } else{
               m.show = false;
           }
        });
        this.form = this.formservice.toFormGroup(this.formelements);
        this.form.patchValue(valueObject);
        this.formcomp.setForm(this.form);
        this.formcomp.setFormModel(valueObject);
        // this.activetab = 'tab_'+(orderId-1);
    }
}
