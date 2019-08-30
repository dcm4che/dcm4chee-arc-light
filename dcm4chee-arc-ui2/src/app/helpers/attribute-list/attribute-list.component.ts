import { Component, OnInit } from '@angular/core';
import {Input} from '@angular/core';
import {Http} from '@angular/http';
import * as _ from 'lodash';
import {AppService} from '../../app.service';
import {WindowRefService} from "../window-ref.service";
import {HttpErrorHandler} from "../http-error-handler";
import {J4careHttpService} from "../j4care-http.service";
import {LoadingBarService} from "@ngx-loading-bar/core";
import {StudyWebService} from "../../study/study/study-web-service.model";
import {StudyService} from "../../study/study/study.service";
declare var DCM4CHE: any;

@Component({
  selector: 'attribute-list',
  templateUrl: './attribute-list.component.html'
})
export class AttributeListComponent implements OnInit {

    @Input() studyuid;
    @Input() seriesuid;
    @Input() objectuid;
    @Input() aet;
    @Input() attrs;
    @Input() studyWebService:StudyWebService;

    rows = [];
    constructor(
        public $http:J4careHttpService,
        public cfpLoadingBar: LoadingBarService,
        public mainservice: AppService,
        public httpErrorHandler:HttpErrorHandler,
        private studyService:StudyService
    ) { }
    ngOnInit() {
        this.init();
    }
    init(){
        if(this.attrs){
            this.attrs2rows('', this.attrs, this.rows);
        }else{
            this.cfpLoadingBar.start();
            let url = "";
            if(this.aet){
                url = '../aets/' +
                    this.aet +
                    '/rs/studies/' +
                    this.studyuid +
                    '/series/' +
                    this.seriesuid +
                    '/instances/' +
                    this.objectuid +
                    '/metadata';
            }else{
                console.log("urlbase",this.studyService.getDicomURL("study", this.studyWebService.selectedWebService));
                url = `${this.studyService.getDicomURL("study", this.studyWebService.selectedWebService)}/${this.studyuid}/series/${this.seriesuid}/instances/${this.objectuid}/metadata`
            }
            this.$http.get(url)
                // .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
                .subscribe((response) => {
                    let attrs = response[0];
                    console.log('attrs', attrs);
                    console.log('this1', this);
                    console.log('this2', this);
                    // this.test("", attrs, this.rows2);
                    this.attrs2rows('', attrs, this.rows);
                    console.log('after attrs2call', this.rows);
                    this.cfpLoadingBar.complete();
                }, (err) => {
                    // vex.dialog.alert("Error loading Attributes!");
                    this.httpErrorHandler.handleError(err);
                    this.cfpLoadingBar.complete();
                });
        }
    };

    attrs2rows(level, attrs, rows) {
        function privateCreator(tag) {
            if ('02468ACE'.indexOf(tag.charAt(3)) < 0) {
                let block = tag.slice(4, 6);
                if (block !== '00') {
                    let el = attrs[tag.slice(0, 4) + '00' + block];
                    return el && el.Value && el.Value[0];
                }
            }
            return undefined;
        }
        let keys = Object.keys(attrs);
        keys.sort();
        keys.forEach((tag)=>{
            let el = attrs[tag];
            rows.push({ level: level, tag: tag, name: DCM4CHE.elementName.forTag(tag, privateCreator(tag)), el: el });
            // rows.push({ level: level, tag: tag, el: el });
            if (el.vr === 'SQ') {
                let itemLevel = level + '>';
                _.forEach(el.Value,  (item, index) => {
                    rows.push({ level: itemLevel, item: index });
                    this.attrs2rows(itemLevel, item, rows);
                });
            }
        });
    };
}
