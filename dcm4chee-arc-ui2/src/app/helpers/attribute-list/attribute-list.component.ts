import { Component, OnInit } from '@angular/core';
declare var DCM4CHE: any;
import * as _ from "lodash";
import {Input} from "@angular/core/src/metadata/directives";

@Component({
  selector: 'attribute-list',
  templateUrl: './attribute-list.component.html'
})
export class AttributeListComponent implements OnInit {

    @Input() attrs;
    rows = [];

    constructor() {
        console.log("attrs1",this.attrs);

    }

    ngOnInit() {
        console.log("attrs2",this.attrs);
        this.attrs2rows("", this.attrs, this.rows);
    }

    attrs2rows(level, attrs, rows) {
        console.log("in attrs2rows");
        function privateCreator(tag) {
            if ("02468ACE".indexOf(tag.charAt(3)) < 0) {
                let block = tag.slice(4, 6);
                if (block !== "00") {
                    let el = attrs[tag.slice(0, 4) + "00" + block];
                    return el && el.Value && el.Value[0];
                }
            }
            return undefined;
        }

        let $this = this;
        Object.keys(attrs).sort().forEach(function (tag) {
            let el = attrs[tag];
            rows.push({ level: level, tag: tag, name: DCM4CHE.elementName.forTag(tag, privateCreator(tag)), el: el });
            if (el.vr === 'SQ') {
                let itemLevel = level + ">";
                _.forEach(el.Value, function (item, index) {
                    rows.push({ level: itemLevel, item: index });
                    $this.attrs2rows(itemLevel, item, rows);
                });
            }
        });
        console.log("attrs ende",attrs);

    };
}
