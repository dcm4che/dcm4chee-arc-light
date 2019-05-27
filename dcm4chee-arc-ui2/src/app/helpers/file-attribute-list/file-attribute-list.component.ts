import { Component, OnInit } from '@angular/core';
import {Input} from '@angular/core';
import {Http} from '@angular/http';
import * as _ from 'lodash';
import {AppService} from '../../app.service';
import {WindowRefService} from "../window-ref.service";
import {HttpErrorHandler} from "../http-error-handler";
import {J4careHttpService} from "../j4care-http.service";
import {LoadingBarService} from "@ngx-loading-bar/core";

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
    constructor(
        public $http:J4careHttpService,
        public cfpLoadingBar: LoadingBarService,
        public mainservice: AppService,
        public httpErrorHandler:HttpErrorHandler
    ) { }
    ngOnInit() {
        this.init();
    }
    init(){
        this.cfpLoadingBar.start();
        let url = './rs/aets/' +
            this.aet +
            '/rs/studies/' +
            this.studyuid +
            '/series/' +
            this.seriesuid +
            '/instances/' +
            this.objectuid +
            '/metadata';
        let $this = this;
        this.$http.get(url)
            // .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
            .subscribe((response) => {
                let attrs = response[0];
                console.log('attrs', attrs);
                console.log('this1', $this);
                console.log('this2', this);
                // $this.test("", attrs, $this.rows2);
                $this.attrs2rows('', attrs, $this.rows2);
                console.log('after attrs2call', $this.rows2);
                $this.cfpLoadingBar.complete();
            }, (err) => {
                // vex.dialog.alert("Error loading Attributes!");
                $this.httpErrorHandler.handleError(err);
                $this.cfpLoadingBar.complete();
            });
    };

    attrs2rows(level, attrs, rows) {
        let keys = Object.keys(attrs);
        keys.sort();
        let $this = this;
        keys.forEach(function (tag) {
            let el = attrs[tag];
            rows.push({ level: level, tag: tag, el: el });
            if (el.vr === 'SQ') {
                let itemLevel = level + '>';
                _.forEach(el.Value, function (item, index) {
                    rows.push({ level: itemLevel, item: index });
                    $this.attrs2rows(itemLevel, item, rows);
                });
            }
        });
    };
}
