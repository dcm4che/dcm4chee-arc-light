import { Component, OnInit } from '@angular/core';
import {ActivatedRoute, Router, Params} from "@angular/router";
import {DropdownList} from "../helpers/form/dropdown-list";
import {InputText} from "../helpers/form/input-text";
import {FormElement} from "../helpers/form/form-element";
import {ArrayElement} from "../helpers/form/array-element";
import {ArrayObject} from "../helpers/form/array-object";
import {RadioButtons} from "../helpers/form/radio-buttons";
import {Checkbox} from "../helpers/form/checkboxes";
import {DeviceConfiguratorService} from "./device-configurator.service";
import {Http} from "@angular/http";
import * as _ from "lodash";
import {Observable} from "rxjs";

@Component({
  selector: 'app-device-configurator',
  templateUrl: './device-configurator.component.html'
})
export class DeviceConfiguratorComponent implements OnInit {
    formObj:FormElement<any>[];
    model;
    device;
    schema;
    showform;
    params = [];
    recentParams;
    submitValue;
    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private service:DeviceConfiguratorService,
        private $http:Http
    ) { }
    addModel(){
        let explod = this.params['device'].split("|");
        console.log("explod",explod);
        this.model = this.device[explod[1]];
        console.log("this.model",this.model);

    }
    submitFunction(value){

        this.service.addChangesToDevice(value,this.recentParams.devicereff);
        console.log("this.service.device",this.service.device);
    }
    ngOnInit() {
        let $this = this;
        let form;
        this.params = $this.service.pagination;
        this.route.params
            // .switchMap((params: Params) => this.service.getHero(+params['id']))
            .subscribe((params) => {
                $this.recentParams = params;
                // $this.service.getSchema('device.schema.json').subscribe(schema => {
/*                $this.formObj = undefined;
                $this.model = undefined;*/
                if(!(_.hasIn(params,"devicereff") && _.hasIn(params,"schema")) || !$this.service.schema){
                    let newPaginationObject = {
                        url:"/device/edit/"+params["device"],
                        title:params["device"],
                        devicereff:"",
                    };
                    let newPaginationIndex = _.findIndex($this.service.pagination, (p)=>{ return p.url === newPaginationObject.url});
                    if(newPaginationIndex > -1){
                        let dropedPaginations = _.dropRight($this.service.pagination,$this.service.pagination.length - newPaginationIndex-1);
                        $this.service.pagination = dropedPaginations;
                        $this.params = dropedPaginations;
                    }else{
                        $this.service.pagination.push(newPaginationObject);
                    }
                    if($this.service.device && params['device'] === $this.service.device.dicomDeviceName && $this.service.schema) {
                        $this.deleteForm();
                        $this.showform = false;
                        $this.model = $this.service.device;
                        form =  $this.service.convertSchemaToForm($this.service.device, $this.service.schema, params);
                        $this.formObj = form;
                        setTimeout(()=>{
                            $this.showform = true;
                        },1);
                    }else{
                        Observable.combineLatest(
                            $this.service.getDevice(params['device']),
                            $this.$http.get('./assets/schema/device.schema.json').map(data => data.json())
                        ).subscribe(deviceschema => {
                            $this.showform = false;
                            console.log("deviceschema",deviceschema);
                            $this.device = deviceschema[0];
                            $this.service.device = deviceschema[0];
                            $this.schema = deviceschema[1];
                            $this.service.schema = deviceschema[1];
                            let formObject = $this.service.convertSchemaToForm($this.device, $this.schema, params);
                            $this.formObj = formObject;
                            $this.model = {};
                            setTimeout(()=>{
                                $this.showform = true;
                            },1);
                        });
                    }
                }else{
                    let newModel:any = _.get(this.service.device,params["devicereff"]);
                    let newSchema = $this.service.getSchemaFromPath($this.service.schema, params['schema']);
                    let title = $this.service.getPaginationTitleFromModel(newModel,newSchema);
                    let newPaginationObject = {
                        url:"/device/edit/"+params["device"]+"/"+params["devicereff"]+"/"+params["schema"],
                        // title:_.replace(newTitle,lastreff,''),
                        title:title,
                        devicereff:params["devicereff"]
                    };
                    let newPaginationIndex = _.findIndex($this.service.pagination, (p)=>{ return p.url === newPaginationObject.url});
                    if(newPaginationIndex > -1){
                        let dropedPaginations = _.dropRight($this.service.pagination,$this.service.pagination.length - newPaginationIndex-1);
                        $this.service.pagination = dropedPaginations;
                        $this.params = dropedPaginations;
                    }else{
                        $this.service.pagination.push(newPaginationObject);
                    }
                    $this.deleteForm();
                    $this.showform = false;
                    $this.model = newModel;
                    if(_.hasIn(newSchema,"$ref") || _.hasIn(newSchema,"items.$ref") ||  _.hasIn(newSchema,"properties.$ref")){
                        let schemaName;
                        let deleteRef;
                        let refPath = '';
                        if(_.hasIn(newSchema,"properties.$ref")){
                            schemaName = newSchema.properties.$ref;
                            refPath = 'properties';
                            deleteRef = ()=>{
                                delete newSchema.properties.$ref;
                            }
                        }
                        if(_.hasIn(newSchema,"items.$ref")){
                            schemaName = newSchema.items.$ref;
                            refPath = 'items';
                            deleteRef = ()=>{
                                delete newSchema.items.$ref;
                            }
                        }
                        if(_.hasIn(newSchema,"$ref")){
                            schemaName = newSchema.$ref;
                            deleteRef = ()=>{
                                delete newSchema.$ref;
                            }
                        }
                        $this.service.getSchema(schemaName).subscribe(subRefSchema => {
                            deleteRef();
                            if(refPath === ""){
                                _.merge(newSchema,subRefSchema);
                            }else{
                                _.set(newSchema,refPath,subRefSchema);
                                refPath = '.'+refPath;
                            }
                            _.set($this.service.schema,params["schema"],newSchema);
                            form = $this.service.convertSchemaToForm($this.model,newSchema, params);
                            $this.formObj = form;
                            setTimeout(()=>{
                                $this.showform = true;
                            },1);
                        });
                    }else{
                        // let newSchema = $this.service.getSchemaFromPath($this.service.schema,schemaparam);
                        form = $this.service.convertSchemaToForm(newModel,newSchema, params);
                        _.set($this.service.schema,params["schema"],newSchema);
                        $this.formObj = form;
                        setTimeout(()=>{
                            $this.showform = true;
                        },1);
                    }
                }

            });
        // this.model = {};
        // this.model = {
        //     brave:"greatTEST",
        //     firstName:"SelamTEST",
        //     emailAddress:"testemail@htall.deEST",
        //     arraytest:[{testkey:"testkeyfrommodelTSETS"}]
        // };
        // this.formObj = [];
/*        this.formObj = [
            new DropdownList({
                key: 'brave',
                label: 'Bravery Rating',
                options: [
                    {key: 'solid',  value: 'Solid'},
                    {key: 'great',  value: 'Great'},
                    {key: 'good',   value: 'Good'},
                    {key: 'unproven', value: 'Unproven'}
                ],
                order: 3
            }),
            new InputText({
                key: 'firstName',
                label: 'First name',
                description:'Testdescriptionfirstname',
                required: true,
                order: 1
            }),
            new InputText({
                key: 'emailAddress',
                label: 'Email',
                type: 'email',
                order: 2
            }),
            new Checkbox({
                key: 'testcheckbox2',
                label: 'Testcheckbox2',
                options: [
                    {key: '1Test1',  value: '1test1', active:false},
                    {key: '1Test2',  value: '1test2', active:true},
                    {key: '1Test3',  value: '1test3', active:true},
                ]
            }),
            new ArrayElement({
                key: 'arrsingleelement',
                label: 'TestArray',
                type: 'text',
                value: []
            }),
            new RadioButtons({
                key: 'testradio',
                label: 'Testradi label',
                value:'installed',
                options: [
                    {key: 'True',  value: true},
                    {key: 'False',  value: false},
                ]
            }),
            new Checkbox({
                key: 'testcheckbox',
                label: 'Testcheckbox',
                options: [
                    {key: 'Test1',  value: 'test1', active:true},
                    {key: 'Test2',  value: 'test2', active:false},
                    {key: 'Test3',  value: 'test3', active:true},
                ]
            }),
            new ArrayElement({
                key: 'arrsingleelement2',
                label: 'TestArray2',
                type: 'number',
                value: []
            }),
            new ArrayObject({
                key: 'arraytest',
                label: 'Array test',
                order:4,
                options: [{
                    element:[
                        new InputText({
                            key:'testkey',
                            label:'Test label',
                            description:"TestDescription",
                            type:'text'
                        }),
                        new InputText({
                            key:'testke2y',
                            label:'Test label2',
                            type:'text',
                            value:"testval"
                        })
                        ,
                        new ArrayElement({
                            key: 'arrsingleelement3',
                            label: 'TestArray2',
                            type: 'number',
                            value: [1]
                        })
                        ,
                        new DropdownList({
                            key: 'brave2',
                            label: 'Bravery Rating2',
                            value:'great',
                            options: [
                                {key: 'solid',  value: 'Solid'},
                                {key: 'great',  value: 'Great'},
                                {key: 'good',   value: 'Good'},
                                {key: 'unproven', value: 'Unproven'}
                            ]
                        }),
                        new ArrayObject({
                            key: 'arraytestsub',
                            label: 'Array test',
                            order:4,
                            options: [{
                                element:[
                                    new InputText({
                                        key:'testkeysub',
                                        label:'Test labelsub',
                                        type:'text'
                                    }),
                                    new InputText({
                                        key:'testke2ysub',
                                        label:'Test label2sub',
                                        type:'text',
                                        value:"testval"
                                    })
                                    ,
                                    new DropdownList({
                                        key: 'brave2',
                                        label: 'Bravery Rating2',
                                        value:'great',
                                        options: [
                                            {key: 'solid',  value: 'Solid'},
                                            {key: 'great',  value: 'Great'},
                                            {key: 'good',   value: 'Good'},
                                            {key: 'unproven', value: 'Unproven'}
                                        ]
                                    })
                                ]
                            }]
                        })
                    ]
                }
                ,{
                    element:[
                        new InputText({
                            key:'testkey',
                            label:'Test labelOBJECT',
                            type:'text'
                        }),
                        new InputText({
                            key:'testke2y',
                            label:'Test label2',
                            type:'text'
                        })
                        ,
                        new DropdownList({
                            key: 'brave2',
                            label: 'Bravery Rating2',
                            options: [
                                {key: 'solid',  value: 'Solid'},
                                {key: 'great',  value: 'Great'},
                                {key: 'good',   value: 'Good'},
                                {key: 'unproven', value: 'Unproven'}
                            ]
                        })
                    ]
                }
                ]
            })
        ]*/
}

        deleteForm(){
            this.model = {};
            this.formObj = [];
        }
}
