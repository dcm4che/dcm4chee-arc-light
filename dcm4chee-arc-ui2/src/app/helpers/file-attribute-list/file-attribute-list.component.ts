import { Component, OnInit } from '@angular/core';
import {Input} from "@angular/core/src/metadata/directives";
import {Http} from "@angular/http";
import {SlimLoadingBarService} from "ng2-slim-loading-bar";
import * as _ from "lodash";
import {AppService} from "../../app.service";

@Component({
  selector: 'file-attribute-list',
  templateUrl: './file-attribute-list.component.html'
})
export class FileAttributeListComponent implements OnInit {

    @Input() studyuid;
    @Input() seriesuid;
    @Input() objectuid;
    @Input() aet;
    rows2 = [];
    constructor(public $http:Http, public cfpLoadingBar:SlimLoadingBarService, public mainservice:AppService) { }
    ngOnInit() {
        this.init();
    }
    init(){
        this.cfpLoadingBar.start();
        let url = "../aets/" +
            this.aet +
            "/rs/studies/" +
            this.studyuid +
            "/series/" +
            this.seriesuid +
            "/instances/" +
            this.objectuid +
            "/metadata";
        let $this = this;
        this.$http.get(url)
            .map(response => response.json())
            .subscribe((response) => {
                let attrs = response[0];
                console.log("attrs",attrs);
                console.log("this1",$this);
                console.log("this2",this);
                // $this.test("", attrs, $this.rows2);
                $this.attrs2rows("", attrs, $this.rows2);
                console.log("after attrs2call", $this.rows2);
                $this.cfpLoadingBar.complete();
            }, (err) => {
                // vex.dialog.alert("Error loading Attributes!");
                $this.mainservice.setMessage({
                    "title": "Error "+err.status,
                    "text": err.statusText,
                    "status": "error"
                });
                $this.cfpLoadingBar.complete();
            });
    };

    attrs2rows(level, attrs, rows) {
        var keys = Object.keys(attrs);
        keys.sort();
        let $this = this;
        keys.forEach(function (tag) {
            var el = attrs[tag];
            rows.push({ level: level, tag: tag, el: el });
            if (el.vr === 'SQ') {
                var itemLevel = level + ">";
                _.forEach(el.Value, function (item, index) {
                    rows.push({ level: itemLevel, item: index });
                    $this.attrs2rows(itemLevel, item, rows);
                });
            }
        });
    };
}
