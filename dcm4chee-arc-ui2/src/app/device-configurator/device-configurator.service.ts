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
    getPaginationTitleFromModel(model,schemaObject){
        let title ="object";
        if(_.hasIn(schemaObject,"type") && schemaObject.type === "array"){
            title = this.replaceCharactersInTitleKey(schemaObject.titleKey,model);
        }else{
            if(_.hasIn(schemaObject,"title")){
                title = schemaObject.title;
            }
        }
        return title;
    }
    getDevice(devicename){
        return this.$http.get('../devices/' + devicename).map(device => device.json());
    }
    getSchema(schema){
        return this.$http.get('./assets/schema/' + schema).map(device => device.json());
    };
    getSchemaFromPath(schema, schemaparam){
        console.log("getSchemaFromPath schema",schema);
        console.log("schemaparam",schemaparam);
        let paramArray = schemaparam.split('.');
        let currentschemaposition = _.cloneDeep(schema);
        let parentkey;
        let parentSchema;
        if(_.hasIn(schema,schemaparam)){

            _.forEach(paramArray,(m)=>{
                if(!_.hasIn(currentschemaposition,m)){
                    console.log("in if csp=", currentschemaposition);
                    console.log("in if m=", m);
                    currentschemaposition = null;
                    return null;
                }else{
                    parentkey = m;
                    parentSchema = currentschemaposition;
                    currentschemaposition = currentschemaposition[m];
                }
            });

            return currentschemaposition;
        }else{
            return null;
        }
    };
    replaceCharactersInTitleKey(string, object){
            let re = /{(.*?)}/g;
            let m;
            let array = [];
            do {
            m = re.exec(string);
            if (m) {
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
        return string || '';
    }
    convertSchemaToForm(device, schema, params){
        console.log("device=",device);
        console.log("schema=",schema);
        console.log("params=",params);
        let $this = this;
        let form = [];
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
                    let value;
                    if(_.hasIn(device,i)){
                        value = device[i];
                    }
                    let newOrderSuffix = 0;
                    if(m.order){
                        newOrderSuffix = parseInt(m.order) / 100;
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
                                        options: options,
                                        order:(5+newOrderSuffix)
                                    }),
                                );
                            }else{
                                form.push(
                                    new InputText({
                                        key:i,
                                        label:m.title,
                                        description:m.description,
                                        type: "string",
                                        value:value,
                                        order:(5+newOrderSuffix)
                                    })
                                );
                            }
                            break;
                        case "boolean":
                            form.push(
                                new RadioButtons({
                                    key:i,
                                    label:m.title,
                                    description:m.description,
                                    options: [
                                        {key: 'True',  value: true},
                                        {key: 'False',  value: false},
                                    ],
                                    order:(5+newOrderSuffix)
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
                                        options: options,
                                        order:(5+newOrderSuffix)
                                    })
                                )
                            }else{
                                console.log("m",m);
                                console.log("params",params);
                                let url = '';
                                if(_.hasIn(m,"items.$ref")) {
                                    if(value && _.isObject(value)){
                                        let options = [];
                                        let maxVali = 0;
                                        _.forEach(value,(valm, vali)=>{
                                            let title;
                                            maxVali = parseInt(vali);
                                            // $this.replaceCharactersInTitleKey(m.titleKey,valm);
                                            url = '/device/edit/'+params.device;
                                            url = url +  ((params.devicereff) ? '/'+params.devicereff+'.'+i+'['+vali+']':'/'+i+'['+vali+']');
                                            url = url +  ((params.schema) ? '/'+params.schema+'.'+propertiesPath+'.'+i:'/properties.'+i);
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
                                        let addUrl = '/device/edit/'+params.device;
                                        addUrl = addUrl +  ((params.devicereff) ? '/'+params.devicereff+'.'+i+'['+(maxVali+1)+']':'/'+i+'['+(maxVali+1)+']');
                                        addUrl = addUrl +  ((params.schema) ? '/'+params.schema+'.'+propertiesPath+'.'+i:'/properties.'+i);
                                        console.log("addUrl",addUrl);
                                        form.push({
                                            controlType:"buttondropdown",
                                            title:m.title,
                                            description:m.description,
                                            options:options,
                                            addUrl:addUrl,
                                            order:(3+newOrderSuffix)
                                        });
                                    }else{
                                        url = '/device/edit/'+params.device;
                                        url = url +  ((params.devicereff) ? '/'+params.devicereff+'.'+i:'/'+i);
                                        url = url +  ((params.schema) ? '/'+params.schema+'.'+propertiesPath+'.'+i:'/properties.'+i);
                                        console.log("url",url);
                                        form.push({
                                            controlType:"button",
                                            title:m.title,
                                            description:m.description,
                                            url:url,
                                            order:(1+newOrderSuffix)
                                        });
                                    }
                                }else{
                                    if(value && _.isObject(value) && (value.length > 0 && _.isObject(value[0]))){
                                        let options = [];
                                        let maxVali = 0;
                                        _.forEach(value,(valm, vali)=>{
                                            let title;
                                            maxVali = parseInt(vali);
                                            // $this.replaceCharactersInTitleKey(m.titleKey,valm);
                                            url = '/device/edit/'+params.device;
                                            url = url +  ((params.devicereff) ? '/'+params.devicereff+'.'+i+'['+vali+']':'/'+i+'['+vali+']');
                                            url = url +  ((params.schema) ? '/'+params.schema+'.'+propertiesPath+'.'+i:'/properties.'+i);
                                            if(_.hasIn(m,"titleKey")){
                                                title = $this.replaceCharactersInTitleKey(m.titleKey,valm);
                                            }else{
                                                title = m.title + '['+vali+']';
                                            }
                                            options.push({
                                                title:title,
                                                description:m.description,
                                                key:i,
                                                url:url,
                                                order:(3+newOrderSuffix)
                                            })
                                        });
                                        let addUrl = '/device/edit/'+params.device;
                                        addUrl = addUrl +  ((params.devicereff) ? '/'+params.devicereff+'.'+i+'['+(maxVali+1)+']':'/'+i+'['+(maxVali+1)+']');
                                        addUrl = addUrl +  ((params.schema) ? '/'+params.schema+'.'+propertiesPath+'.'+i:'/properties.'+i);
                                        console.log("*addUrl",addUrl);
                                        form.push({
                                            controlType:"buttondropdown",
                                            title:m.title,
                                            description:m.description,
                                            options:options,
                                            addUrl:addUrl,
                                            order:(3+newOrderSuffix)
                                        });
                                    }else{
                                        let type = (_.hasIn(m,"items.type")) ? m.items.type : "text";
                                        form.push(
                                            new ArrayElement({
                                                key:i,
                                                label:m.title,
                                                description:m.description,
                                                type: type,
                                                value:(value)? value:[''],
                                                order:(5+newOrderSuffix)
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
                                    type: "number",
                                    order:(5+newOrderSuffix)
                                })
                            )
                            break;
                        default:
                            // let subschema = {};
                            // subschema[i] = $this.convertSchemaToForm(m);
/*                            let url = '/device/edit/'+params.device;
                                url = url +  ((params.devicereff) ? '/'+params.devicereff+'.'+i:'/'+i);
                                url = url +  ((params.schema) ? '/'+params.schema+'.'+i:'/'+i);
                            form.push({
                                controlType:"button",
                                key:i,
                                url:url
                            });*/
                            let url = '/device/edit/'+params.device;
 //                            if(value && _.isObject(value)){
 //                                    let options = [];
 //                                _.forEach(value,(valm, vali)=>{
 //                                    url = '/device/edit/'+params.device;
 //                                    url = url +  ((params.devicereff) ? '/'+params.devicereff+'.'+i+'['+vali+']':'/'+i+'['+vali+']');
 //                                    url = url +  ((params.schema) ? '/'+params.schema+'.properties.'+i:'/properties.'+i);
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
                                form.push({
                                    controlType:"button",
                                    title:m.title,
                                    description:m.description,
                                    url:url,
                                    order:(1+newOrderSuffix)
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
