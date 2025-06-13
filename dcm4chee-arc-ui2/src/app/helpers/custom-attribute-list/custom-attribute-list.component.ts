import {Component, Input, OnInit} from '@angular/core';
import {AttributeListComponent} from '../attribute-list/attribute-list.component';
import {CommonModule} from '@angular/common';

@Component({
    selector: 'custom-attribute-list',
    templateUrl: './custom-attribute-list.component.html',
    styleUrls: ['./custom-attribute-list.component.scss'],
    imports: [
        AttributeListComponent,
        CommonModule
    ],
    standalone: true
})
export class CustomAttributeListComponent implements OnInit {
    @Input() dicomAttributesAsSubAttribute;
    @Input() attributesHook;
    @Input() set attributes(value){
        if(this.attributesHook){
            this.localAttributes = this.attributesHook(value);
        }else{
            this.localAttributes = value;
        }
    }

    get visible(){
        return this.localAttributes;
    }

    localAttributes;


    constructor() { }

    ngOnInit(): void {
    }

}
