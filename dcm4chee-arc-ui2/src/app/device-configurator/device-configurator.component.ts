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
        console.log("submitfunction in deviceconfig",value);
    }
    ngOnInit() {
        let $this = this;
        this.params = $this.service.pagination;
        this.route.params
            // .switchMap((params: Params) => this.service.getHero(+params['id']))
            .subscribe((params) => {


                console.log("params",params);
                console.log("dev beforegetting",$this.device);
                console.log("dev from service",$this.service.device);

                // $this.service.getSchema('device.schema.json').subscribe(schema => {
/*                $this.formObj = undefined;
                $this.model = undefined;*/
                if(!(_.hasIn(params,"devicereff") && _.hasIn(params,"schema")) && !$this.service.schema){
                    $this.service.pagination.push({
                        url:"/device/edit/"+params["device"],
                        title:params["device"],
                        devicereff:"",
                    });
                    if(this.device && params['device'] === this.device.dicomDeviceName && this.schema) {
                        $this.deleteForm();
                        $this.showform = false;
                        $this.model = $this.device
                        let form = $this.service.convertSchemaToForm({},this.schema, params);
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
                            console.log("-+-+-+-+-+formObject",formObject);
                            console.log("-+-+-+-+-+device",$this.device);
                            $this.formObj = formObject;
                            $this.model = {};
                            setTimeout(()=>{
                                $this.showform = true;
                            },1);
                        });
                    }
                }else{
                    let lastreff = $this.service.pagination[$this.service.pagination.length-1].devicereff;
                    console.log("lastreff",lastreff);
                    let newTitle = params["devicereff"];
                    let newPaginationObject = {
                        url:"/device/edit/"+params["device"]+"/"+params["devicereff"]+"/"+params["schema"],
                        title:_.replace(newTitle,lastreff,''),
                        devicereff:params["devicereff"]
                    };
                    let newPaginationIndex = _.findIndex($this.service.pagination, (p)=>{ return p.url === newPaginationObject.url});
                    console.log("newPaginationIndex",newPaginationIndex);
                    if(newPaginationIndex > -1){
                        console.log("++++++++++++++++++++++in dropright", $this.service.pagination.length - newPaginationIndex);
                        console.log("beforedrop", _.cloneDeep($this.service.pagination));
                        let dropedPaginations = _.dropRight($this.service.pagination,$this.service.pagination.length - newPaginationIndex-1);
                        console.log("droppedpaginations",dropedPaginations);
                        $this.service.pagination = dropedPaginations;
                        console.log("afterdrop", _.cloneDeep($this.service.pagination));
                        $this.params = dropedPaginations;
                    }else{
                        $this.service.pagination.push(newPaginationObject);
                    }
                    $this.deleteForm();
                    $this.showform = false;
                    let getDevice = params['devicereff'];
                    let schemaparam = params['schema'];
                    let form;
                    $this.model = _.get($this.service.device,getDevice);
                    console.log("device",$this.service.device);
                    console.log("schema",_.cloneDeep($this.service.schema));
                    let newSchema = $this.service.getSchemaFromPath($this.service.schema,schemaparam);
                    console.log("newschema after init",_.cloneDeep(newSchema));
                    if(_.hasIn(newSchema,"$ref") || _.hasIn(newSchema,"items.$ref") ||  _.hasIn(newSchema,"properties.$ref")){
                        let schemaName;
                        let deleteRef;
                        let refPath = '';
                        if(_.hasIn(newSchema,"properties.$ref")){
                            console.log("----in1");
                            schemaName = newSchema.properties.$ref;
                            refPath = 'properties';
                            deleteRef = ()=>{
                                delete newSchema.properties.$ref;
                            }
                        }
                        if(_.hasIn(newSchema,"items.$ref")){
                            console.log("----in2");
                            schemaName = newSchema.items.$ref;
                            refPath = 'items';
                            deleteRef = ()=>{
                                delete newSchema.items.$ref;
                            }
                        }
                        if(_.hasIn(newSchema,"$ref")){
                            console.log("----in3");

                            schemaName = newSchema.$ref;
                            deleteRef = ()=>{
                                delete newSchema.$ref;
                            }
                        }
                        $this.service.getSchema(schemaName).subscribe(subRefSchema => {
                            console.log("mainschema",_.cloneDeep($this.service.schema));
                            deleteRef();
                            console.log("subrefschema",_.cloneDeep(subRefSchema));
                            console.log("afterdeleteref",_.cloneDeep(newSchema));
                            if(refPath === ""){
                                _.merge(newSchema,subRefSchema);
                                console.log("in if merge newschema",_.cloneDeep(newSchema));
                            }else{
                                _.set(newSchema,refPath,subRefSchema);
                                console.log("in else set newschema",_.cloneDeep(newSchema));
                                refPath = '.'+refPath;
                            }
                            _.set($this.service.schema,params["schema"],newSchema);
                            console.log("mainschema2",_.cloneDeep($this.service.schema));

                            console.log("currentschma after merge",newSchema);
                            //TODO if array new shchema should be childe of items
                            console.log("service.schema after set",$this.service.schema);
                            form = $this.service.convertSchemaToForm($this.model,newSchema, params);
                            console.log("form after value if",form);
                            $this.formObj = form;
                            setTimeout(()=>{
                                $this.showform = true;
                            },1);
                        });
                    }else{
                        form = $this.service.convertSchemaToForm($this.model,newSchema, params);
                        console.log("form after value setelse",form);
                        _.set($this.service.schema,params["schema"],newSchema);
                        console.log("service.schema after set",$this.service.schema);
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
