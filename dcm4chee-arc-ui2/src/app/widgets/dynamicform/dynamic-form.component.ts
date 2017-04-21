/**
 * Created by shefki on 9/20/16.
 */
import {Component, OnInit, Input, EventEmitter} from "@angular/core";
import {FormGroup} from "@angular/forms";
import {FormService} from "../../helpers/form/form.service";
import {FormElement} from "../../helpers/form/form-element";
import {Output} from "@angular/core/src/metadata/directives";
import {OrderByPipe} from "../../pipes/order-by.pipe";
import * as _ from "lodash";

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
    partSearch = "";
    prevPartSearch = "";
    listStateBeforeSearch:FormElement<any>[];

    constructor(private formservice:FormService){}
    // submi(){
    //     console.log("in submitfunctiondynamicform");
    //     this.submitFunction.emmit("test");
    // }
    ngOnInit(): void {
        console.log("formelements",this.formelements);
        let orderedGroup:any = new OrderByPipe().transform(this.formelements,"order");
        let orderValue = 0;
        let order = 0;
        _.forEach(orderedGroup, (m, i)=>{
            if(orderValue != m.order){
                let title = "";
                switch(m.order) {
                    case 1:
                        title = "Extensions";
                        order = 0;
                        break;
                    case 3:
                        title = "Child Objects";
                        order = 2;
                        break;
                    default:
                        title = "Attributes";
                        order = 4;
                }
                orderedGroup.splice(i, 0, {
                    controlType:"togglebutton",
                    title:title,
                    orderId:m.order,
                    order:order
                });
            }
            orderValue = m.order;
        });
        let formGroup:any = this.formservice.toFormGroup(orderedGroup);
        this.form = formGroup;
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

    showAll(){
        if(this.partSearch != ''){
            if(this.partSearch.length === 1 && this.prevPartSearch.length < this.partSearch.length){
                this.listStateBeforeSearch = _.cloneDeep(this.formelements);
                _.forEach(this.formelements,(m,i)=>{
                    if(!m.show){
                        m.show = true;
                    }
                });
            }
        }else{
            if(_.size(this.listStateBeforeSearch) > 0){
                this.formelements = _.cloneDeep(this.listStateBeforeSearch);
            }
        }
        this.prevPartSearch = this.partSearch;
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