import {Component, OnInit, Input} from '@angular/core';
import * as _ from 'lodash-es';
import {Globalvar} from '../../constants/globalvar';
import {AppService} from "../../app.service";
import {CommonModule, NgClass} from '@angular/common';
import {RangePickerComponent} from '../../widgets/range-picker/range-picker.component';
import {FormsModule} from '@angular/forms';
import {SpecificCharPickerComponent} from '../../widgets/specific-char-picker/specific-char-picker.component';
import {AppModule} from '../../app.module';
import {PlaceholderchangerDirective} from '../placeholderchanger.directive';
import {j4care} from '../j4care.service';
declare var DCM4CHE: any;

@Component({
    selector: 'iod-form-generator',
    templateUrl: './iod-form-generator.component.html',
    imports: [
        NgClass,
        RangePickerComponent,
        FormsModule,
        SpecificCharPickerComponent,
        PlaceholderchangerDirective,
        CommonModule
    ],
    styles:[`
        .active.sqiod:hover{
            background: rgba(0, 0, 0, 0.6);
            color: white;
            input{
                color: black;
            }
        }
        .add_multi_button{
            float: right;
            width: 50px;
            height: 28px;
            background: white;
            color: #00000087;
            border: navajowhite;
            margin: 5px 20px;
            &:hover{
                background: #212121;
                color: white;
            }
        }
    `],
    standalone: true
})
export class IodFormGeneratorComponent implements OnInit {
    @Input() object;
    @Input() prefix;
    @Input() mode;
    @Input() iod;
    @Input() externalInternalAetMode;
    objectIsArray;
    hasValue;
    _=_;
    constructor(
        public appService:AppService
    ) { }
    privateCreator(tag) {
        if ('02468ACE'.indexOf(tag.charAt(3)) < 0) {
            let block = tag.slice(4, 6);
            if (block !== '00') {
                let el = this.object[tag.slice(0, 4) + '00' + block];
                return el && el.Value && el.Value[0];
            }
        }
        return undefined;
    }

    ngOnInit() {
        console.log('attr=', this.object);
        console.log('oninit object', this.object);
        console.log('prefix', this.prefix);
        if (this.object && this.object.Value){
            console.log('has value', this.object);

            this.hasValue = true;
        }else{
            this.hasValue = false;
        }
        console.log('hasValue=', this.hasValue);
        this.objectIsArray = _.isArray(this.object);
        console.log('objectisarray', this.objectIsArray);
    }
    getKeys(obj){
        if (_.isArray(obj)){
            return obj;
        }else{
            // console.log("objectkeys=",Object.keys(obj));
            return Object.keys(obj);
        }
    }
    options = Globalvar.OPTIONS;
    DCM4CHE = DCM4CHE;
    activeBlock = false;
    onChange(newValue, model) {
        _.set(this, model, newValue);
    }
    removeAttr(attrcode, i?){
        console.log('attrcode', attrcode);
        console.log('arguments', arguments);
        switch (arguments.length) {
            case 2:
                if (this.object[arguments[0]].Value && this.object[arguments[0]].Value.length === 1){
                    delete  this.object[arguments[0]];
                }else{
                    this.object[arguments[0]].Value.splice(arguments[1], 1);
                }
                break;
            default:
                delete  this.object[arguments[0]];
                break;
        }
    };
    trackByFn(index, item) {
        return index; // or item.id
    }
    charChange(event){
        console.log("test in charchange",event);
    }
    addPart(object,o, iod, i, io){
        try{
            if(object[o] && _.hasIn(object[o], 'Value') && _.isArray(object[o].Value) && object[o].Value[i]){
                let clonedObject = _.cloneDeep(object[o].Value[i])
                j4care.traverse(clonedObject, (value, key,obj,savedKeys)=>{
                    console.log("key",obj[key]);
                    console.log("key",value[key]);
                    console.log("test",obj);
                    console.log("savedKeys",savedKeys);
                    if(key == "0" && savedKeys === "Value[Value]" && typeof value === "string"){
                        obj[key] = "";
                    }
                    return obj[key];
                });
                console.log("clonedObject",clonedObject);
                object[o].Value.splice(i+1, 0, clonedObject);
            }
        }catch (e) {
            console.warn(e);
        }
    }
}
