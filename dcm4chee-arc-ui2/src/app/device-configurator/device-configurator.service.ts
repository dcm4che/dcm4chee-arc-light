import {Injectable, OnInit} from '@angular/core';
import {Http} from "@angular/http";
import * as _ from "lodash";
import {InputText} from "../helpers/form/input-text";
import {RadioButtons} from "../helpers/form/radio-buttons";
import {Checkbox} from "../helpers/form/checkboxes";
import {ArrayElement} from "../helpers/form/array-element";
import {ArrayObject} from "../helpers/form/array-object";
import {DropdownList} from "../helpers/form/dropdown-list";
import {Observable} from "rxjs";
import {InputNumber} from "../helpers/form/input-number";

@Injectable()
export class DeviceConfiguratorService{

    constructor(private $http:Http) {
        this.pagination = [
            {
                url:"/device/devicelist",
                title:"devicelist",
                devicereff:undefined
            }
        ];
    }
    device;
    schema;
    pagination = [];
    getPaginationTitleFromModel(model,schemaObject){
        let title ="object";
        if(_.hasIn(schemaObject,"type") && schemaObject.type === "array"){
            if(model){
                title = this.replaceCharactersInTitleKey(schemaObject.titleKey,model);
            }else{
                title = "[NEW]";
            }
        }else{
            if(_.hasIn(schemaObject,"title")){
                title = schemaObject.title;
            }
        }
        return title;
    }
    removeExtensionFromDevice(devicereff){
        console.log("in service devicereff",devicereff);
        _.unset(this.device,devicereff);
        console.log("this.device",this.device);
    }
    getDevice(devicename){
        return this.$http.get('../devices/' + devicename).map(device => device.json());
    }
    getSchema(schema){
        return this.$http.get('./assets/schema/' + schema).map(device => device.json());
    };
    getSchemaFromPath(schema, schemaparam){
        let paramArray = schemaparam.split('.');
        let currentschemaposition = _.cloneDeep(schema);
        let parentkey;
        let parentSchema;
        if(_.hasIn(schema,schemaparam)){

            _.forEach(paramArray,(m)=>{
                if(!_.hasIn(currentschemaposition,m)){
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
            //TODO that means that in the schema thare is a $ref
            return null;
        }
    };
    addChangesToDevice(value, devicereff){
        /*
        * Check if the changed part is a child (or in the root)
        * */
        if(devicereff){
            //If the part is already in the device override / call setWith with the child refference otherwise use lodash to append the object
            if(_.hasIn(this.device,devicereff)){
                this.setWith(_.get(this.device,devicereff),value);
            }else{
                let newValue = {};
                this.setWith(newValue,value);
                //TODO observe,change becous of bug when the device is new
                // _.setWith(this.device, devicereff, newValue, Object);
                _.set(this.device, devicereff, newValue);
            }
        }else{
            //The root of the device was changed call setWith
            this.setWith(this.device,value);
        }
    }
    setWith(device,value){
        _.forEach(value,(m,i)=>{
            if(_.hasIn(device,i)){
                if(!_.isPlainObject(device[i]) && !(_.isArray(device[i]) && device[i].length > 0 && _.isPlainObject(device[i][0]))){
                    let newValue = this.getWrightValue(device[i],m);
                    if(newValue != null){
                        device[i] = newValue;
                    }
                }
            }else{
                let newValue = this.getWrightValue(device[i],m);
                if(newValue){
                    device[i] = newValue;
                }
            }
        });
/*        _.assignWith(device, value, (obj,obj2)=>{
            if(obj === undefined && obj2 != undefined && obj2 != ''){
                return obj2;
            }
            if(obj != undefined  && obj2 != undefined && ((obj2 != '' && obj2 != "inherent") || (obj2.length == 1 && obj2[0] != ''))){
                return obj2;
            }
            if((obj != undefined && (obj === true || obj === false)) && (obj2 === undefined || obj2 === "")){
                return obj;
            }
            if((obj != undefined && (obj === true || obj === false)) && (obj2 != undefined && (obj2 === true || obj2 === false))){
                return obj2;
            }
            //Handle dicomInstalled with inherent
            if(obj === undefined && (obj2 === false || obj2 === true)){
                return obj2;
            }
            if((obj === true || obj === false) && (obj2 === "inherent" || obj2 === false || obj2 === true)){
                return (obj2 === "inherent") ? null : obj2;
            }
            return null;
        });*/
        _.forEach(device,(m,i)=>{
            if(m === null){
                delete device[i];
            }
        });
    }
    getWrightValue(obj,obj2){
        if(!_.isEqual(obj,obj2)){
            if(obj === undefined && obj2 != undefined && obj2 != ''){
                return obj2;
            }
            //Deleting value
            if(_.isString(obj) && obj != "" && obj2 === ""){
                return obj2;
            }
            //Updating array
            if(_.isArray(obj) && _.isArray(obj2)){
                return obj2;
            }
            if(obj != undefined  && obj2 != undefined && ((obj2 != '' && obj2 != "inherent") || (obj2.length == 1 && obj2[0] != ''))){
                return obj2;
            }
            if((obj != undefined && (obj === true || obj === false)) && (obj2 === undefined || obj2 === "")){
                return null;
            }
            if((obj != undefined && (obj === true || obj === false)) && (obj2 != undefined && (obj2 === true || obj2 === false))){
                return obj2;
            }
            //Handle dicomInstalled with inherent
            if(obj === undefined && (obj2 === false || obj2 === true)){
                return obj2;
            }
            if((obj === true || obj === false) && (obj2 === "inherent" || obj2 === false || obj2 === true)){
                return (obj2 === "inherent") ? null : obj2;
            }
        }
        return null;
    }
    saveDevice(){

    }
    updateDevice(){
        //TODO check if devicename was changed
        if(_.hasIn(this.device,"dicomDeviceName") && this.device.dicomDeviceName != ""){
            console.log("paginationtitle",this.pagination[1].title);
            console.log("this.device.dicomDeviceName",this.device.dicomDeviceName);
            return this.$http.put('../devices/' + this.device.dicomDeviceName,this.device).map(device => device.json());
        }else{
            return null;
        }
    }
    createDevice(){
        if(_.hasIn(this.device,"dicomDeviceName") && this.device.dicomDeviceName != ""){
            return this.$http.post('../devices/' + this.device.dicomDeviceName,this.device).map(device => device.json());
        }else{
            return null;
        }
    }
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
                let requiredArray;
                if(_.hasIn(schema,"properties")){
                    schemaProperties = schema.properties;
                    requiredArray = schema.required || [];
                }else{
                    schemaProperties = schema.items.properties;
                    propertiesPath = "items.properties";
                    requiredArray = schema.items.required || [];
                }
                _.forEach(schemaProperties,(m,i)=>{
                    let value;
                    let required = (_.indexOf(requiredArray,i) > -1);
                    let validation = {
                        required:required
                    };
                    if(_.hasIn(m,"minimum")){
                        validation["minimum"] = m.minimum;
                    }
                    if(_.hasIn(m,"maximum")){
                        validation["maximum"] = m.maximum;
                    }
                    if(_.hasIn(m,"pattern")){
                        validation["pattern"] = m.pattern;
                    }
                    if(_.hasIn(device,i)){
                        value = device[i];
                    }
                    let newOrderSuffix = 0;
                    if(m.order){
                        newOrderSuffix = parseInt(m.order) / 100;
                    }
                    let options = [];
                    switch(m.type) {
                        case "string":
                            if(i === "dicomDeviceName" && _.hasIn(device,"dicomDeviceName") && device.dicomDeviceName != ""){
                                form.push({
                                    controlType:"constantField",
                                    key:i,
                                    label:m.title,
                                    description:m.description,
                                    order:(5+newOrderSuffix),
                                    value:value,
                                    show:true
                                });
                            }else{
                                if(_.hasIn(m,"enum")){
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
                                            order:(5+newOrderSuffix),
                                            validation:validation,
                                            value:value
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
                                            order:(5+newOrderSuffix),
                                            validation:validation
                                        })
                                    );
                                }
                            }
                            break;
                        case "boolean":
                            if(i === "dicomVendorData"){
                                if(_.hasIn(device,"dicomDeviceName") && device.dicomDeviceName != ""){ //Show upload button just if the device is on edit mode (preventing trying to upload files for a device that doesn't exist yet)
                                    if(_.hasIn(device,"dicomVendorData") && device.dicomVendorData === true && _.hasIn(device,"dicomDeviceName") && device.dicomDeviceName != ""){ // If the vendordata is tru than show the download lin
                                        form.push({
                                            controlType:"filedownload",
                                            key:i,
                                            label:m.title,
                                            deviceName:device.dicomDeviceName,
                                            description:m.description,
                                            order:(5+newOrderSuffix),
                                            downloadUrl:`../devices/${device.dicomDeviceName}/vendordata`,
                                            show:true
                                        });
                                    }else{
                                        //If the vendor data is missing or false than show the upload button
                                        form.push({
                                            controlType:"fileupload",
                                            modus:"upload",
                                            key:i,
                                            label:m.title,
                                            deviceName:device.dicomDeviceName,
                                            description:m.description,
                                            order:(5+newOrderSuffix),
                                            show:true
                                        });
                                    }
                                }
                            }else{
                                if(i === "dicomInstalled" && _.hasIn(params,"devicereff") && _.hasIn(params,"schema")){
                                    options = [
                                        {key: 'True',  value: true},
                                        {key: 'False',  value: false},
                                        {key: 'Inherent',  value: "inherent"},
                                    ];
                                    if(value != undefined && value != ""){
                                        //true
                                        if(value === true){
                                            options[0]['active'] = true;
                                        }else{
                                           //false
                                            options[1]['active'] = true;
                                        }
                                    }else{
                                        //Inherent
                                        options[2]['active'] = true;
                                    }
                                }else{
                                    options = [
                                        {key: 'True',  value: true},
                                        {key: 'False',  value: false}
                                    ];
                                    if(value != undefined && value != ""){
                                        //true
                                        if(value === true){
                                            options[0]['active'] = true;
                                        }else{
                                            //false
                                            options[1]['active'] = true;
                                        }
                                    }
                                }
                                form.push(
                                    new RadioButtons({
                                        key:i,
                                        label:m.title,
                                        description:m.description,
                                        options: options,
                                        order:(5+newOrderSuffix),
                                        validation:validation
                                    })
                                );
                            }
                            break;
                        case "array":
                            if(i == "dicomNetworkConnectionReference"){
                                _.forEach(this.device["dicomNetworkConnection"],(opt,i) =>{
                                    options.push({
                                        value:"/dicomNetworkConnection/"+i,
                                        key:opt.cn+" ("+opt.dicomHostname+((opt.dicomPort)?":"+opt.dicomPort:'')+")",
                                        active:(_.indexOf(value,"/dicomNetworkConnection/"+i) > -1)?true:false
                                    });
                                })
                                form.push(
                                    new Checkbox({
                                        key:i,
                                        label:m.title,
                                        description:m.description,
                                        options: options,
                                        order:(5+newOrderSuffix),
                                        validation:validation
                                    })
                                )
                            }else{
                                console.log("this.device",this.device);
                                if(_.hasIn(m,"items.enum")){
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
                                            order:(5+newOrderSuffix),
                                            validation:validation
                                        })
                                    )
                                }else{
                                    let url = '';
                                    if(_.hasIn(m,"items.$ref")) {
                                        if(value && _.isObject(value)){
                                            if(value.length === 0){
                                                url = '/device/edit/'+params.device;
                                                url = url +  ((params.devicereff) ? '/'+params.devicereff+'.'+i+'[0]':'/'+i+'[0]');
                                                url = url +  ((params.schema) ? '/'+params.schema+'.'+propertiesPath+'.'+i:'/properties.'+i);
                                                console.log("url",url);
                                                form.push({
                                                    controlType:"buttondropdown",
                                                    key:i,
                                                    label:m.title,
                                                    description:m.description,
                                                    addUrl:url,
                                                    order:(3+newOrderSuffix)
                                                });
                                            }else{
                                                options = [];
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
                                                        forCloneUrl:((params.devicereff) ? '/'+params.devicereff+'.'+i+'['+vali+']':'/'+i+'['+vali+']')
                                                    })
                                                });
                                                let addUrl = '/device/edit/'+params.device;
                                                addUrl = addUrl +  ((params.devicereff) ? '/'+params.devicereff+'.'+i+'['+(maxVali+1)+']':'/'+i+'['+(maxVali+1)+']');
                                                addUrl = addUrl +  ((params.schema) ? '/'+params.schema+'.'+propertiesPath+'.'+i:'/properties.'+i);
                                                console.log("addUrl",addUrl);
                                                form.push({
                                                    controlType:"buttondropdown",
                                                    key:i,
                                                    label:m.title,
                                                    description:m.description,
                                                    options:options,
                                                    addUrl:addUrl,
                                                    order:(3+newOrderSuffix)
                                                });
                                            }
                                        }else{
                                            //TODO Observe, changed because of bug when the extendsions and the childe not presend on object
                                            /*
                                            url = '/device/edit/'+params.device;
                                            url = url +  ((params.devicereff) ? '/'+params.devicereff+'.'+i:'/'+i);
                                            url = url +  ((params.schema) ? '/'+params.schema+'.'+propertiesPath+'.'+i:'/properties.'+i);
                                            console.log("url",url);
                                            form.push({
                                                controlType:"button",
                                                label:m.title,
                                                title:m.title,
                                                description:m.description,
                                                url:url,
                                                devicereff:(params.schema) ? params.schema+'.'+propertiesPath+'.'+i:'properties.'+i,
                                                order:(1+newOrderSuffix),
                                                value:_.size(value)
                                            });*/
                                            let addUrl = '/device/edit/'+params.device;
                                            addUrl = addUrl +  ((params.devicereff) ? '/'+params.devicereff+'.'+i+'[0]':'/'+i+'[0]');
                                            addUrl = addUrl +  ((params.schema) ? '/'+params.schema+'.'+propertiesPath+'.'+i:'/properties.'+i);
                                            form.push({
                                                controlType:"buttondropdown",
                                                key:i,
                                                label:m.title,
                                                description:m.description,
                                                options:[],
                                                addUrl:addUrl,
                                                order:(3+newOrderSuffix)
                                            });
                                        }
                                    }else{
                                        if(value && _.isObject(value) && (value.length > 0 && _.isObject(value[0]))){
                                            options = [];
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
                                                    forCloneUrl:((params.devicereff) ? '/'+params.devicereff+'.'+i+'['+vali+']':'/'+i+'['+vali+']'),
                                                    order:(3+newOrderSuffix)
                                                })
                                            });
                                            let addUrl = '/device/edit/'+params.device;
                                            addUrl = addUrl +  ((params.devicereff) ? '/'+params.devicereff+'.'+i+'['+(maxVali+1)+']':'/'+i+'['+(maxVali+1)+']');
                                            addUrl = addUrl +  ((params.schema) ? '/'+params.schema+'.'+propertiesPath+'.'+i:'/properties.'+i);
                                            console.log("*addUrl",addUrl);
                                            form.push({
                                                controlType:"buttondropdown",
                                                key:i,
                                                label:m.title,
                                                description:m.description,
                                                options:options,
                                                addUrl:addUrl,
                                                order:(3+newOrderSuffix)
                                            });
                                        }else{
                                            if(_.hasIn(m,"items.properties")){
                                                let addUrl = '/device/edit/'+params.device;
                                                addUrl = addUrl +  ((params.devicereff) ? '/'+params.devicereff+'.'+i+'[0]':'/'+i+'[0]');
                                                addUrl = addUrl +  ((params.schema) ? '/'+params.schema+'.'+propertiesPath+'.'+i:'/properties.'+i);
                                                form.push({
                                                    controlType:"buttondropdown",
                                                    key:i,
                                                    label:m.title,
                                                    description:m.description,
                                                    options:[],
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
                                                        order:(5+newOrderSuffix),
                                                        validation:validation
                                                    })
                                                );
                                            }
                                        }
                                    }
                                }
                            }
                            break;
                        case "integer":
                            // code block

                            form.push(
                                new InputNumber({
                                    key:i,
                                    label:m.title,
                                    description:m.description,
                                    value:parseFloat(value),
                                    type: "number",
                                    order:(5+newOrderSuffix),
                                    validation:validation
                                })
                            )
                            break;
                        default:
                            let url = '/device/edit/'+params.device;
                                url = url +  ((params.devicereff) ? '/'+params.devicereff+'.'+i:'/'+i);
                                url = url +  ((params.schema) ? '/'+params.schema+'.'+propertiesPath+'.'+i:'/properties.'+i);
                                form.push({
                                    controlType:"button",
                                    label:m.title,
                                    title:m.title,
                                    description:m.description,
                                    url:url,
                                    devicereff:(params.devicereff) ? params.devicereff+'.'+i:i,
                                    order:(1+newOrderSuffix),
                                    value:_.size(value)
                                });
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
