import { Component, OnInit } from '@angular/core';
import {DiffMonitorService} from "./diff-monitor.service";
import {AppService} from "../../app.service";
import {ActivatedRoute} from "@angular/router";
import * as _ from 'lodash';
import {LoadingBarService} from "@ngx-loading-bar/core";
import {AeListService} from "../../ae-list/ae-list.service";
import {Observable} from "rxjs/Observable";
import {j4care} from "../../helpers/j4care.service";

@Component({
  selector: 'diff-monitor',
  templateUrl: './diff-monitor.component.html',
  styleUrls: ['./diff-monitor.component.scss']
})
export class DiffMonitorComponent implements OnInit {

    filterObject = {};
    filterSchema;
    urlParam;
    aes;
    aets;
    devices;
    batchGrouped = false;
    diffs = [];
    constructor(
    private service:DiffMonitorService,
    private mainservice:AppService,
    private route: ActivatedRoute,
    public cfpLoadingBar: LoadingBarService,
    public aeListService:AeListService,
    ) { }

    ngOnInit(){
      this.initCheck(10);
    }
    initCheck(retries){
      let $this = this;
      if(_.hasIn(this.mainservice,"global.authentication") || (_.hasIn(this.mainservice,"global.notSecure") && this.mainservice.global.notSecure)){

          this.route.queryParams.subscribe(params => {
              console.log("params",params);
              this.urlParam = Object.assign({},params);
              this.init();
          });
      }else{
          if (retries){
              setTimeout(()=>{
                  $this.initCheck(retries-1);
              },20);
          }else{
              this.init();
          }
      }
    }

    init(){
      this.filterObject = {
          limit:20
      };
      Observable.forkJoin(
          this.aeListService.getAes(),
          this.aeListService.getAets(),
          this.service.getDevices()
      ).subscribe((response)=>{
          this.aes = (<any[]>j4care.extendAetObjectWithAlias(response[0])).map(ae => {
              return {
                  value:ae.dicomAETitle,
                  text:ae.dicomAETitle
              }
          });
          this.aets = (<any[]>j4care.extendAetObjectWithAlias(response[1])).map(ae => {
              return {
                  value:ae.dicomAETitle,
                  text:ae.dicomAETitle
              }
          });
          this.devices = (<any[]>response[2])
              .filter(dev => dev.hasArcDevExt)
              .map(device => {
                  return {
                      value:device.dicomDeviceName,
                      text:device.dicomDeviceName
                  }
              });
          this.initSchema();
      });
    }
    onSubmit(e){

    }

    onFormChange(e){

    }

    downloadCsv(){

    }
    initSchema(){
      this.filterSchema = j4care.prepareFlatFilterObject(this.service.getFormSchema(this.aes, this.aets,"Size",this.devices),3);
    }
    ngOnDestroy(){
    }
}
