import { Component, OnInit } from '@angular/core';
import {MetricsService} from "./metrics.service";
import {HttpErrorHandler} from "../../helpers/http-error-handler";
import {LoadingBarService} from "@ngx-loading-bar/core";
import * as _ from "lodash";
import {AppService} from "../../app.service";
import {FilterSchema, MetricsDescriptors, SelectDropdown} from "../../interfaces";
import {j4care} from "../../helpers/j4care.service";
import {environment} from "../../../environments/environment";

@Component({
  selector: 'app-metrics',
  templateUrl: './metrics.component.html',
  styleUrls: ['./metrics.component.scss']
})
export class MetricsComponent implements OnInit {

    metricsDescriptors;
    filterObject = {
        bin:1
    };
    filterSchema:FilterSchema;
    tableConfig;
    metrics;
    constructor(
        private service:MetricsService,
        private httpErrorHandler:HttpErrorHandler,
        private cfpLoadingBar:LoadingBarService,
        private appService:AppService
    ) { }

    ngOnInit() {
        this.getMetricsDescriptors();
        this.tableConfig = {
            table:j4care.calculateWidthOfTable(this.service.getTableSchema()),
            filter:this.filterObject,
            calculate:false
        };
    }

    getMetrics(e){
        if(_.hasIn(this.filterObject,"name") && this.filterObject["name"] != ""){
            this.cfpLoadingBar.start();

            let params = _.clone(this.filterObject);
            let name = params["name"];
            delete params["name"];

            this.service.getMetrics(name, params).subscribe(metrics=>{
                let bin = this.filterObject["bin"] || 1;
                let currentServerTime = new Date(this.appService.serverTime);
                this.metrics = metrics.map( (metric,i)=>{
                    if(!_.isEmpty(metric)){
                        return {
                            time:j4care.formatDate(new Date(currentServerTime.setMinutes(currentServerTime.getMinutes()+bin)),"HH:mm"),
                            avg: metric["avg"],
                            count: metric["count"],
                            max: metric["max"],
                            min: metric["min"]
                        }
                    }else{
                        currentServerTime.setMinutes(currentServerTime.getMinutes()+bin);
                        return {}
                    }
                }).reduce((accumulator, current) => {
                    const length = accumulator.length;
                    if (length === 0 || _.isEmpty(accumulator[length - 1]) != _.isEmpty(current) || !_.isEmpty(current)) {
                        accumulator.push(current);
                    }
                    return accumulator;
                }, []);
                this.cfpLoadingBar.complete();
                if((!this.metrics || this.metrics.length === 0) || (this.metrics.length === 1 && _.isEmpty(this.metrics[0]))){
                    this.appService.showMsg("No data found!");
                }
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
