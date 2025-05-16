import {Component, Input, OnInit} from '@angular/core';

@Component({
    selector: 'custom-attribute-list',
    templateUrl: './custom-attribute-list.component.html',
    styleUrls: ['./custom-attribute-list.component.scss'],
    standalone: false
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
