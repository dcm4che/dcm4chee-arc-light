import {Component, OnInit, Input} from '@angular/core';
import * as _ from 'lodash';
import {Globalvar} from '../../constants/globalvar';
declare var DCM4CHE: any;

@Component({
  selector: 'iod-form-generator',
  templateUrl: './iod-form-generator.component.html',
  styles: []
})
export class IodFormGeneratorComponent implements OnInit {
    @Input() object;
    @Input() prefix;
    @Input() mode;
    @Input() iod;
    @Input() externalInternalAetMode;
    objectIsArray;
    hasValue;

    constructor() { }
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
        if (_.isArray(this.object)){
            this.objectIsArray = true;
        }else{
            this.objectIsArray = false;
        }
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
}
