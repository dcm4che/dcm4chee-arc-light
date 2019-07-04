import { Component, OnInit } from '@angular/core';
import {MetricsService} from "./metrics.service";
import {HttpErrorHandler} from "../../helpers/http-error-handler";
import {LoadingBarService} from "@ngx-loading-bar/core";
import * as _ from "lodash";
import {AppService} from "../../app.service";
import {FilterSchema, MetricsDescriptors, SelectDropdown} from "../../interfaces";
import {j4care} from "../../helpers/j4care.service";

@Component({
  selector: 'app-metrics',
  templateUrl: './metrics.component.html',
  styleUrls: ['./metrics.component.scss']
})
export class MetricsComponent implements OnInit {

    metricsDescriptors;
    filterObject = {};
    filterSchema:FilterSchema;
    constructor(
        private service:MetricsService,
        private httpErrorHandler:HttpErrorHandler,
        private cfpLoadingBar:LoadingBarService,
        private appService:AppService
    ) { }

    ngOnInit() {
        this.getMetricsDescriptors();
    }

    getMetrics(){
        if(_.hasIn(this.filterObject,"name") && this.filterObject["name"] != ""){
            this.cfpLoadingBar.start();

            let params = _.clone(this.filterObject);
            let name = params["name"];
            delete params["name"];

            this.service.getMetrics(name, params).subscribe(metrics=>{
                console.log("metrics",metrics);
                this.cfpLoadingBar.complete();
            },err=>{
                this.cfpLoadingBar.complete();
                this.httpErrorHandler.handleError(err);
            })
        }else{
            this.appService.showError("Metrics Name is missing");
        }
    }

    setFilterSchema(){
        this.filterSchema = j4care.prepareFlatFilterObject(
            this.service.getFilterSchema(this.metricsDescriptors.map((metricsDescriptor:MetricsDescriptors)=>{
                return new SelectDropdown(metricsDescriptor.dcmMetricsName, metricsDescriptor.dicomDescription, metricsDescriptor.dicomDescription,undefined, undefined,metricsDescriptor);
            })),
            1
        );
    }

    getMetricsDescriptors(){
        this.service.getMetricsDescriptors().subscribe((res:MetricsDescriptors)=>{
            this.metricsDescriptors = res;
            this.setFilterSchema();
        },err=>{
            this.httpErrorHandler.handleError(err);
        })
    }
}
