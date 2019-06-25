/**
 * Created by shefki on 9/20/16.
 */
import {Component, OnInit, Input, EventEmitter} from '@angular/core';
import {FormBuilder, FormGroup} from '@angular/forms';
import {FormService} from '../../helpers/form/form.service';
import {FormElement} from '../../helpers/form/form-element';
import {Output} from '@angular/core';
import {OrderByPipe} from '../../pipes/order-by.pipe';
import * as _ from 'lodash';
import {SearchPipe} from '../../pipes/search.pipe';
import {AppService} from "../../app.service";
import {DeviceConfiguratorComponent} from "../../configuration/device-configurator/device-configurator.component";
import {ActivatedRoute} from "@angular/router";
import {KeycloakService} from "../../helpers/keycloak-service/keycloak.service";

@Component({
    selector: 'dynamic-form',
    templateUrl: './dynamic-form.component.html',
    providers: [ FormService ]
})
export class DynamicFormComponent implements OnInit{
    @Input() formelements: FormElement<any>[] = [];
    @Input() model;
    @Input() dontShowSearch;
    @Input() dontGroup;
    @Input() readOnlyMode;
    @Output() submitFunction = new EventEmitter<any>();
    form: FormGroup;
    payLoad = '';
    partSearch = '';
    prevPartSearch = '';
    pressedKey = [];
    listStateBeforeSearch: FormElement<any>[];
    filteredFormElements: FormElement<any>[];
    exceptionValidation = false;
    constructor(
        private formservice: FormService,
        private mainservice:AppService,
        private route: ActivatedRoute,
        private fb: FormBuilder
    ){}
    // submi(){
    //     console.log("in submitfunctiondynamicform");
    //     this.submitFunction.emmit("test");
    // }
    ngOnInit(){
        this.initCheck(10);
    }
    initCheck(retries){
        let $this = this;
        if((KeycloakService.keycloakAuth && KeycloakService.keycloakAuth.authenticated) || (_.hasIn(this.mainservice,"global.notSecure") && this.mainservice.global.notSecure)){
            this.init();
        }else{
            if (retries){
                setTimeout(()=>{
                    $this.initCheck(retries-1);
                },20);
            }else{
                this.init();
            }
        }
    }
    init(): void {
        console.log('formelements', this.formelements);
        let orderedGroupClone:any;
        let orderedGroup: any;
        let orderValue = 0;
        let order = 0;
        let diffState = 0;
        let materialIconName;
        orderedGroup = new OrderByPipe().transform(this.formelements, 'order');
        orderedGroupClone = _.cloneDeep(orderedGroup);
        orderedGroupClone = new OrderByPipe().transform(orderedGroupClone, 'order');
        // this.filteredFormElements = _.cloneDeep(this.formelements);
        // let orderedGroupClone =  new OrderByPipe().transform(this.formelements, 'order');
        _.forEach(orderedGroup, (m, i) => {
            if (orderValue != parseInt(m.order)){
                let title = '';
                if (1 <= m.order && m.order < 3){
                    title = 'Extensions';
                    materialIconName = 'extension';
                    order = 0;
                }else{
                    if (3 <= m.order && m.order  < 4) {
                        title = 'Child Objects';
                        materialIconName = 'subdirectory_arrow_right';
                        order = 2;
                    }else{
                        title = 'Attributes';
                        materialIconName = 'list';
                        order = 4;
                    }
                }
                orderedGroupClone.splice(i+diffState, 0, {
                    controlType: 'togglebutton',
                    title: title,
                    orderId: order,
                    order: order,
                    materialIconName: materialIconName
                });
                diffState++;
            }
            orderValue = parseInt(m.order);
        });
        this.formelements = orderedGroupClone;
        let formGroup: FormGroup = this.formservice.toFormGroup(orderedGroupClone);
        this.form = formGroup;
        console.log("hr",window.location);
        console.log('after convert form', this.form);
        //Test setting some values
        console.log('this.model=', this.model);
        this.route.params
            .subscribe((params) => {
                console.log("params",params);
                console.log("this.model",this.model);
                if(params.devicereff === "dcmDevice.dcmArchiveDevice" && (!this.model || (this.model && !_.hasIn(this.model,"dcmDevice.dcmArchiveDevice")))){
                    // console.log("this.service.device",this.service.device);
                    // _.set(this.service.device,"dcmDevice.dcmArchiveDevice",{});
                    this.exceptionValidation = true;
                }
            });
/*        if(this.model){
            this.form.patchValue(this.model);
        }*/

/*
        this.form.valueChanges
            .debounceTime(500)
            .distinctUntilChanged()
            .subscribe(fe => {
                console.log('insubscribe changes fe', fe);
                console.log('form', this.form);
            });
*/

        console.log('form', this.form);
    }

    filterFormElements(){
        if (this.partSearch != ''){
            if ( (this.partSearch.length === 1 && this.prevPartSearch.length < this.partSearch.length) ||
                (!this.prevPartSearch && !this.listStateBeforeSearch)
            ) {
                this.listStateBeforeSearch = _.cloneDeep(this.formelements);
            }
            this.formelements = new OrderByPipe().transform(this.listStateBeforeSearch, 'order');
            this.formelements = new SearchPipe().transform(this.formelements, this.partSearch);
        }else{
            if (_.size(this.listStateBeforeSearch) > 0){
                this.formelements = _.cloneDeep(this.listStateBeforeSearch);
            }
        }
        this.prevPartSearch = this.partSearch;
    }
    onSubmit(valid){
        console.log('this.form.value', this.form.value);
        if(valid){
            this.payLoad = JSON.stringify(this.form.value);
            this.submitFunction.emit(this.form.value);
        }
    }
    getForm(){
        return this.form;
    }
    setFormModel(model: any){
        this.form.patchValue(model);
    }
    setForm(form: any){
        this.form = form;
    }
    keyDown(e){
        console.log("e2",e.keyCode);
        this.pressedKey.push(e.keyCode);
        if(this.pressedKey.indexOf(17) > -1 && e.keyCode === 13){
            console.log("combinatnion pressed");
            this.onSubmit(this.form.valid);
        }
    }
    keyUp(e){
        console.log("e2",e.keyCode);
        let index = this.pressedKey.indexOf(e.keyCode);
        if (index > -1) {
            this.pressedKey.splice(index, 1);
        }
    }
}