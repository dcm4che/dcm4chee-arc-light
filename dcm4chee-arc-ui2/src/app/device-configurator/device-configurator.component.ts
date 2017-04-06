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
    params;
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
        this.route.params
            // .switchMap((params: Params) => this.service.getHero(+params['id']))
            .subscribe((res) => {
                $this.params = res;
                console.log("res",res);
                console.log("dev beforegetting",$this.device);
                console.log("dev from service",$this.service.device);

                // $this.service.getSchema('device.schema.json').subscribe(schema => {
/*                $this.formObj = undefined;
                $this.model = undefined;*/
                console.log("indexof| in url",res['device'].indexOf("|"));
                if(res['device'].indexOf("|") === -1){
                    console.log("in if",res['device']);
                    console.log("device",this.device);
                    console.log("schema",this.schema);
                    if(this.device && res['device'] === this.device.dicomDeviceName && this.schema) {
                        $this.deleteForm();
                        $this.showform = false;
                        $this.model = $this.device
                        let form = $this.service.convertSchemaToForm({},this.schema, res);
                        $this.formObj = form;
                        setTimeout(()=>{
                            $this.showform = true;
                        },1);
                    }else{
                        let getDeviceSchema = Observable.combineLatest(
                            $this.service.getDevice(res['device']),
                            $this.$http.get('./assets/schema/device.schema.json').map(data => data.json())
                        ).subscribe(deviceschema => {
                            $this.showform = false;
                            console.log("deviceschema",deviceschema);
                            $this.device = deviceschema[0];
                            $this.schema = deviceschema[1];
                            let formObject = $this.service.convertSchemaToForm($this.device, $this.schema, res);
                            console.log("-+-+-+-+-+formObject",formObject);
                            $this.formObj = formObject;
                            $this.model = {};
                            setTimeout(()=>{
                                $this.showform = true;
                            },1);
                        });
/*                        $this.service.getDevice(res['device']).subscribe(device => {
                            $this.device = device;
                            $this.service.device = device;
                            console.log("device",$this.device);
                            $this.model = device;
                            $this.showform = true;
                        });
                        $this.$http.get('./assets/schema/device.schema.json').map(data => data.json()).subscribe(schema => {
                            $this.showform = false;
                            console.log("schema",schema.properties.dcmDevice);
                            $this.schema = schema;
                            let form = $this.service.convertSchemaToForm({},schema, res);
                            console.log(".form after convert",form);
                            $this.formObj = form;
                            $this.showform = true;
                        });*/
                    }
                }else{
                    $this.deleteForm();
                    $this.showform = false;
                    let explod = res['device'].split("|");
                    $this.model = $this.device[explod[1]];
                    console.log("explod",explod);
                    console.log("$this.model",$this.device);
                    console.log("$this.model",$this.device[explod[1]]);
                    console.log("schema",$this.schema);
                    console.log("explod[1]",explod[1]);
                    console.log("get subschema",$this.schema.properties[explod[1]]);
                    let newSchema = $this.schema.properties[explod[1]];
                    let form = $this.service.convertSchemaToForm({},newSchema, res);
                    console.log("form",form);
/*                    _.forEach(form,(m,i)=>{
                        console.log("m.key",m.key);
                        console.log("i",i);
                        if(_.hasIn($this.device[explod[1]],m.key)){
                            console.log("in has in",$this.device[explod[1]][m.key]);
                            if(m.controlType === "checkbox"){
                                _.forEach(m.options, (o,j)=>{
                                    if(_.hasIn($this.device[explod[1]][m.key],o.key) || $this.device[explod[1]][m.key] === o.key){
                                        o.active = true;
                                    }else{
                                        o.active = false;
                                    }
                                });
                            }else{
                                m.value = $this.device[explod[1]][m.key];
                            }
                        }
                    });*/
                    console.log("form after value set",form);
                    $this.formObj = form;
                    // setTimeout(()=>{
                    //     console.log("formObj",$this.formObj);
                    //     $this.showform = true;
                    // },200);

                    setTimeout(()=>{
                        $this.showform = true;
                        // $this.model = {}
/*                        $this.model = {
                            dcmKeyStorePin: "secret",
                            dcmKeyStoreType: "JKS",
                            dcmKeyStoreURL: "${jboss.server.config.url}/dcm4chee-arc/key.jks"
                        };*/
                        console.log("model",$this.model);
                    },1);
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
