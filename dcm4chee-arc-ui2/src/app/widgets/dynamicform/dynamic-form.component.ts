/**
 * Created by shefki on 9/20/16.
 */
import {Component, OnInit, Input, EventEmitter} from "@angular/core";
import {FormGroup} from "@angular/forms";
import {FormService} from "../../helpers/form/form.service";
import {FormElement} from "../../helpers/form/form-element";
import {Output} from "@angular/core/src/metadata/directives";

@Component({
    selector:'dynamic-form',
    templateUrl:'./dynamic-form.component.html',
    providers:[ FormService ]
})
export class DynamicFormComponent implements OnInit{
    @Input() formelements:FormElement<any>[] = [];
    @Input() model;
    @Output() submitFunction = new EventEmitter<any>();
    form: FormGroup;
    payLoad = '';

    constructor(private formservice:FormService){}
    // submi(){
    //     console.log("in submitfunctiondynamicform");
    //     this.submitFunction.emmit("test");
    // }
    ngOnInit(): void {
        this.form = this.formservice.toFormGroup(this.formelements);
        console.log("after convert form",this.form);
        //Test setting some values
        console.log("this.model=",this.model);
        if(this.model){
            this.form.patchValue(this.model);
        }
        this.form.valueChanges.forEach(fe => {
            console.log("formvalue changes fe", fe);
        });
        console.log("form",this.form);
    }

    onSubmit(){
        this.payLoad = JSON.stringify(this.form.value);
        console.log("this.form.value",this.form.value);
        this.submitFunction.emit(this.form.value);
    }
    getForm(){
        return this.form;
    }
    setFormModel(model:any){
        this.form.patchValue(model);
    }
    setForm(form:any){
        this.form = form;
        this.form.valueChanges.forEach(fe => {
            console.log("formvalue changes fe", fe);
        });
    }

}