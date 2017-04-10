import { Injectable } from '@angular/core';
import {Http} from "@angular/http";
import * as _ from "lodash";
import {InputText} from "../helpers/form/input-text";
import {RadioButtons} from "../helpers/form/radio-buttons";
import {Checkbox} from "../helpers/form/checkboxes";
import {ArrayElement} from "../helpers/form/array-element";
import {ArrayObject} from "../helpers/form/array-object";
import {DropdownList} from "../helpers/form/dropdown-list";
import {Observable} from "rxjs";

@Injectable()
export class DeviceConfiguratorService {

    constructor(private $http:Http) { }
    device;
    schema;
    pagination = [
        {
            url:"/device/devicelist",
            title:"devicelist",
            devicereff:undefined
        }
    ];
    getDevice(devicename){
        return this.$http.get('../devices/' + devicename).map(device => device.json());
    }
    getSchema(schema){
        return this.$http.get('./assets/schema/' + schema).map(device => device.json());
    };
    getSchemaFromPath(schema, schemaparam){

        console.log("?schema",schema);
        console.log("?schemaparam",schemaparam);
        let paramArray = schemaparam.split('.');
        let currentschemaposition = _.cloneDeep(schema);
        let parentkey;
        let parentSchema;
        _.forEach(paramArray,(m)=>{
            if(!_.hasIn(currentschemaposition,m)){
                console.log("currentschemaposition",currentschemaposition);
                console.log("m",m);
            }else{
                parentkey = m;
                parentSchema = currentschemaposition;
                currentschemaposition = currentschemaposition[m];
            }
        });
        console.log("end currentschemaposition",currentschemaposition);
        console.log("end parentSchema",parentSchema);
        console.log("end parentkey",parentkey);

        return currentschemaposition;
    };
    replaceCharactersInTitleKey(string, object){
        console.log("object",object);
        console.log("string",string);
            let re = /{(.*?)}/g;
            let m;
            let array = [];
            do {
            m = re.exec(string);
            if (m) {
                console.log("m",m);
                console.log(m[1], m[2]);
                if(m[1]){
                    array.push(m[1]);
                }
            }
        } while (m);
        _.forEach(array,(i)=>{
            if(_.hasIn(object,i)){
               string = _.replace(string,'{'+i+'}',object[i]);
            }else{
                string = _.replace(string,'{'+i+'}',"");
            }
        })
        console.log("array",array);
        console.log("string",string);
        return string || '';
    }
    convertSchemaToForm(device, schema, params){
        let $this = this;
        let form = [];
        console.log("in convertschema ",schema);
        if(_.hasIn(schema,"type")){
            if((schema.type === "object" && _.hasIn(schema,"properties")) || (schema.type === "array" && _.hasIn(schema,"items.properties"))){
                let schemaProperties;
                let propertiesPath = "properties";
                if(_.hasIn(schema,"properties")){
                    schemaProperties = schema.properties;

                }else{
                    schemaProperties = schema.items.properties;
                    propertiesPath = "items.properties";
                }
                _.forEach(schemaProperties,(m,i)=>{
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
                                            console.log("valm",valm);
                                            console.log("vali",vali);
                                            let title;
                                            // $this.replaceCharactersInTitleKey(m.titleKey,valm);
                                            url = url +  ((params.devicereff) ? '/'+params.devicereff+'.'+i+'['+vali+']':'/'+i+'['+vali+']');
                                            url = url +  ((params.schema) ? '/'+params.schema+'.items.properties.'+i:'/properties.'+i);
                                            if(_.hasIn(m,"titleKey")){
                                                title = $this.replaceCharactersInTitleKey(m.titleKey,valm);
                                            }else{
                                               title = m.title + '['+vali+']';
                                            }
                                            options.push({
                                                title:title,
                                                description:m.description,
                                                key:i,
                                                url:url
                                            })
                                        });
                                        console.log("optionlength",options);
                                        url = '/device/edit/'+params.device;
                                        url = url +  ((params.devicereff) ? '/'+params.devicereff+'.'+i+'['+0+']':'/'+i+'['+0+']');
                                        url = url +  ((params.schema) ? '/'+params.schema+'.items.properties.'+i:'/properties.'+i);
                                        form.push({
                                            controlType:"buttondropdown",
                                            title:m.title,
                                            description:m.description,
                                            options:options,
                                            addUrl:url
                                        });
                                    }else{

                                        url = url +  ((params.devicereff) ? '/'+params.devicereff+'.'+i:'/'+i);
                                        url = url +  ((params.schema) ? '/'+params.schema+'.items.properties.'+i:'/properties.'+i);
                                        form.push({
                                            controlType:"button",
                                            title:m.title,
                                            description:m.description,
                                            key:i,
                                            url:url
                                        });
                                    }
                                }else{
                                    console.log("m",m);
                                    console.log("************ in array value",value);
                                    console.log("propertiesPath",propertiesPath);
                                    if(value && _.isObject(value) && (value.length > 0 && _.isObject(value[0]))){
                                        let options = [];
                                        _.forEach(value,(valm, vali)=>{
                                            console.log("valm",valm);
                                            console.log("vali",vali);
                                            let title;
                                            // $this.replaceCharactersInTitleKey(m.titleKey,valm);
                                            url = '/device/edit/'+params.device;
                                            url = url +  ((params.devicereff) ? '/'+params.devicereff+'.'+i+'['+vali+']':'/'+i+'['+vali+']');
                                            url = url +  ((params.schema) ? '/'+params.schema+'.items.properties.'+i:'/properties.'+i);
                                            if(_.hasIn(m,"titleKey")){
                                                title = $this.replaceCharactersInTitleKey(m.titleKey,valm);
                                            }else{
                                                title = m.title + '['+vali+']';
                                            }
                                            options.push({
                                                title:title,
                                                description:m.description,
                                                key:i,
                                                url:url
                                            })
                                        });
                                        form.push({
                                            controlType:"buttondropdown",
                                            title:m.title,
                                            description:m.description,
                                            options:options
                                        });
                                    }else{
                                        console.log("in else....",m);
                                        let type = (_.hasIn(m,"items.type")) ? m.items.type : "text";
                                        form.push(
                                            new ArrayElement({
                                                key:i,
                                                label:m.title,
                                                description:m.description,
                                                type: type,
                                                value:(value)? value:['']
                                            })
                                        );
                                    }
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
 //                            if(value && _.isObject(value)){
 //                                    let options = [];
 //                                _.forEach(value,(valm, vali)=>{
 // /*                                   console.log("valm",valm);
 //                                    console.log("vali",vali);*/
 //                                    url = '/device/edit/'+params.device;
 //                                    url = url +  ((params.devicereff) ? '/'+params.devicereff+'.'+i+'['+vali+']':'/'+i+'['+vali+']');
 //                                    url = url +  ((params.schema) ? '/'+params.schema+'.properties.'+i:'/properties.'+i);
 //                                    console.log("url",url);
 //                                    options.push({
 //                                        title:m.title+'.'+vali,
 //                                        description:m.description,
 //                                        key:i,
 //                                        url:url
 //                                    })
 //                                });
 //                                form.push({
 //                                    controlType:"buttondropdown",
 //                                    options:options
 //                                });
 //                            }else{
                                url = url +  ((params.devicereff) ? '/'+params.devicereff+'.'+i:'/'+i);
                                url = url +  ((params.schema) ? '/'+params.schema+'.'+propertiesPath+'.'+i:'/properties.'+i);
                                console.log("url",url);
                                form.push({
                                    controlType:"button",
                                    title:m.title,
                                    description:m.description,
                                    key:i,
                                    url:url
                                });
                            // }
                    }
                });
            }else{
                console.error("in else1",schema);
            }
        }else{
            //TODO
            console.error("In else convert schema to form",schema);
        }
        return form;
    }

}
