import { Component, OnInit, Input } from '@angular/core';
declare var DCM4CHE: any;

@Component({
    selector: 'attribute-info',
    templateUrl: './attribute-info.component.html',
    styleUrls: ['./attribute-info.component.css'],
    standalone: false
})
export class AttributeInfoComponent implements OnInit {

    @Input() value;
    @Input() dictionary;
    attributeName;
    constructor() {}

    ngOnInit() {
        switch(this.dictionary) {
            case 'dcmTag':
                this.attributeName = DCM4CHE.elementName.forTag(this.value);
                break;
            case 'dcmTransferSyntax':
                this.attributeName = DCM4CHE.TransferSyntax.nameOf(this.value);
                break;
            case 'dcmSOPClass':
                this.attributeName = DCM4CHE.SOPClass.nameOf(this.value);
                break;
        }
    }

}
