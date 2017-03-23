/**
 * Created by shefki on 9/20/16.
 */
import {Component, OnInit, Input} from "@angular/core";
import {FormGroup} from "@angular/forms";
import {FormService} from "../../helpers/form/form.service";
import {FormElement} from "../../helpers/form/form-element";

@Component({
    selector:'dynamic-form',
    templateUrl:'./dynamic-form.component.html',
    providers:[ FormService ]
})
export class DynamicFormComponent implements OnInit{
    @Input() formelements:FormElement<any>[] = [];
    form: FormGroup;
    payLoad = '';

    constructor(private formservice:FormService){}

    ngOnInit(): void {
        this.form = this.formservice.toFormGroup(this.formelements);
        //Test setting some values
        this.form.patchValue({
                brave:"great",
                firstName:"Selam",
                emailAddress:"testemail@htall.de",
                arraytest:[{testkey:"testkeyfrommodel"}]
            })
        this.form.valueChanges.forEach(fe => {
            console.log("formvalue changes fe", fe);
        });
        console.log("form",this.form);
    }

    onSubmit(){
        this.payLoad = JSON.stringify(this.form.value);
        console.log("this.form.value",this.form.value);
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