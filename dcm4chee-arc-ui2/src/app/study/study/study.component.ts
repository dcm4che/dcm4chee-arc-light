import { Component, OnInit } from '@angular/core';
import {ActivatedRoute} from "@angular/router";
import {AccessLocation, FilterSchema, StudyTab} from "../../interfaces";
import {StudyService} from "./study.service";
import {Observable} from "rxjs/Observable";
import {j4care} from "../../helpers/j4care.service";
import {Aet} from "../../models/aet";
import {PermissionService} from "../../helpers/permissions/permission.service";
import {AppService} from "../../app.service";

@Component({
  selector: 'app-study',
  templateUrl: './study.component.html',
  styleUrls: ['./study.component.scss']
})
export class StudyComponent implements OnInit {

  tab:StudyTab;

  applicationEntities = {
      aes:{
          external:[],
          internal:[]
      },
      aets:{
          external:[],
          internal:[]
      }
  };
  filterSchema:FilterSchema;
  accessLocation:AccessLocation = "internal";
  constructor(
      private route:ActivatedRoute,
      private service:StudyService,
      private permissionService:PermissionService,
      private appService:AppService
  ) { }

  ngOnInit() {
      console.log("aet",this.applicationEntities);
      this.route.queryParams.subscribe(params => {
          this.tab = params.tab;
          this.getApplicationEntities();
      });
  }


  initSchema(){
    this.filterSchema  = this.service.getFilterSchema(this.tab, this.applicationEntities.aes[this.accessLocation],false);
  }
  getApplicationEntities(){
      Observable.forkJoin(
          this.service.getAes().map(aes=> aes.map(aet=> new Aet(aet))),
          this.service.getAets().map(aets=> aets.map(aet => new Aet(aet))),
      )
      .retry(2)
      .subscribe((res)=>{
          [0,1].forEach(i=>{
              res[i] = j4care.extendAetObjectWithAlias(res[i]);
              ["external","internal"].forEach(location=>{
                  this.applicationEntities.aes[location] = this.permissionService.filterAetDependingOnUiConfig(res[i],location);
                  this.applicationEntities.aets[location] = this.permissionService.filterAetDependingOnUiConfig(res[i],location);
              })
          });
          this.initSchema();
      },(err)=>{
          this.appService.showError("Error getting AETs!");
          j4care.log("error getting aets in Study page",err);
      });
  }
}
