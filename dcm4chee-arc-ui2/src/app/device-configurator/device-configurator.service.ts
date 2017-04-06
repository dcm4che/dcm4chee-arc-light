import { Injectable } from '@angular/core';
import {Http} from "@angular/http";
import * as _ from "lodash";
import {InputText} from "../helpers/form/input-text";
import {RadioButtons} from "../helpers/form/radio-buttons";
import {Checkbox} from "../helpers/form/checkboxes";
import {ArrayElement} from "../helpers/form/array-element";
import {ArrayObject} from "../helpers/form/array-object";
import {DropdownList} from "../helpers/form/dropdown-list";

@Injectable()
export class DeviceConfiguratorService {

    constructor(private $http:Http) { }
    device;
    getDevice(devicename){
        return this.$http.get('../devices/' + devicename).map(device => device.json());
    }
    getSchema(schema){
        return this.$http.get('./assets/schema/' + schema).map(device => device.json());
    }
    convertSchemaToForm(device, schema, params){
        let $this = this;
        let form = [];
        if(_.hasIn(schema,"type")){
            if(schema.type === "object" && _.hasIn(schema,"properties")){
                _.forEach(schema.properties,(m,i)=>{
                    console.log("i",i);
                    console.log("m",m);
                    let value;
                    if(_.hasIn(device,i)){
                        console.log("hasindevicei true",device[i]);
                        value = device[i];
                    }
                    switch(m.type) {
                        case "string":
                            if(_.hasIn(m,"enum")){
                                let options = [];
                                _.forEach(m.enum,(opt) =>{
                                    options.push({
                                        key:opt,
                                        value:opt,
                                        active:(opt === value)? true:false
                                    });
                                })
                                form.push(
                                    new DropdownList({
                                        key:i,
                                        label:m.title,
                                        description:m.description,
                                        options: options
                                    }),
                                );
                            }else{
                                form.push(
                                    new InputText({
                                        key:i,
                                        label:m.title,
                                        description:m.description,
                                        type: "string",
                                        value:value
                                    })
                                );
                            }
                            break;
                        case "boolean":
                            console.log("boolean i",i);
                            console.log("m",m);
                            form.push(
                                new RadioButtons({
                                    key:i,
                                    label:m.title,
                                    description:m.description,
                                    options: [
                                        {key: 'True',  value: true},
                                        {key: 'False',  value: false},
                                    ]
                                })
                            );
                            break;
                        case "array":
                            if(_.hasIn(m,"items.enum")){
                                let options = [];
                                _.forEach(m.items.enum,(opt) =>{
                                    options.push({
                                        key:opt,
                                        value:opt,
                                        active:(opt === value)? true:false
                                    });
                                })
                                form.push(
                                    new Checkbox({
                                        key:i,
                                        label:m.title,
                                        description:m.description,
                                        options: options
                                    })
                                )
                            }else{
                                console.log("m",m);
                                console.log("in array case button",value);
                                let url = '/device/edit/'+params.device;
                                if(_.hasIn(m,"items.$ref")) {
                                    if(value && _.isObject(value)){
                                        let options = [];
                                        _.forEach(value,(valm, vali)=>{
  /*                                          console.log("valm",valm);
                                            console.log("vali",vali);*/
                                            url = '/device/edit/'+params.device;
                                            url = url +  ((params.devicereff) ? '/'+params.devicereff+'.'+i+'['+vali+']':'/'+i+'['+vali+']');
                                            url = url +  ((params.schema) ? '/'+params.schema+'.'+i:'/'+i);
                                            console.log("url",url);
                                            options.push({
                                                title:m.title+'.'+vali,
                                                description:m.description,
                                                key:i,
                                                url:url
                                            })
                                        });
                                        form.push({
                                            controlType:"buttondropdown",
                                            options:options
                                        });
                                    }else{

                                        url = url +  ((params.devicereff) ? '/'+params.devicereff+'.'+i:'/'+i);
                                        url = url +  ((params.schema) ? '/'+params.schema+'.'+i:'/'+i);
                                        console.log("url",url);
                                        form.push({
                                            controlType:"button",
                                            title:m.title,
                                            description:m.description,
                                            key:i,
                                            url:url
                                        });
                                    }
                                }else{
                                    let type = (_.hasIn(m,"items.type")) ? m.items.type : "text";
                                    form.push(
                                        new ArrayElement({
                                            key:i,
                                            label:m.title,
                                            description:m.description,
                                            type: type,
                                            value:(value)? value:['']

                                }),
                                    );
                                }
                            }
                            break;
                        case "integer":
                            // code block
                            form.push(
                                new InputText({
                                    key:i,
                                    label:m.title,
                                    description:m.description,
                                    value:value,
                                    type: "number"
                                })
                            )
                            break;
                        default:
                            // let subschema = {};
                            // subschema[i] = $this.convertSchemaToForm(m);
                            console.log("m in button",m);
/*                            let url = '/device/edit/'+params.device;
                                url = url +  ((params.devicereff) ? '/'+params.devicereff+'.'+i:'/'+i);
                                url = url +  ((params.schema) ? '/'+params.schema+'.'+i:'/'+i);
                            console.log("url",url);
                            form.push({
                                controlType:"button",
                                key:i,
                                url:url
                            });*/
                            let url = '/device/edit/'+params.device;
                            if(value && _.isObject(value)){
                                    let options = [];
                                _.forEach(value,(valm, vali)=>{
 /*                                   console.log("valm",valm);
                                    console.log("vali",vali);*/
                                    url = '/device/edit/'+params.device;
                                    url = url +  ((params.devicereff) ? '/'+params.devicereff+'.'+i+'['+vali+']':'/'+i+'['+vali+']');
                                    url = url +  ((params.schema) ? '/'+params.schema+'.'+i:'/'+i);
                                    console.log("url",url);
                                    options.push({
                                        title:m.title+'.'+vali,
                                        description:m.description,
                                        key:i,
                                        url:url
                                    })
                                });
                                form.push({
                                    controlType:"buttondropdown",
                                    options:options
                                });
                            }else{
                                url = url +  ((params.devicereff) ? '/'+params.devicereff+'.'+i:'/'+i);
                                url = url +  ((params.schema) ? '/'+params.schema+'.'+i:'/'+i);
                                console.log("url",url);
                                form.push({
                                    controlType:"button",
                                    title:m.title,
                                    description:m.description,
                                    key:i,
                                    url:url
                                });
                            }
                    }
                });
            }
        }else{
            //TODO
            console.log("In else convert schema to form",schema);
        }
        return form;
    }

}
