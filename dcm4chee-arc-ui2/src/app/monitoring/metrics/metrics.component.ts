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
    selectedMetricsDescriptors:MetricsDescriptors;
    metrics;
    constructor(
        private service:MetricsService,
        private httpErrorHandler:HttpErrorHandler,
        private cfpLoadingBar:LoadingBarService,
        private appService:AppService
    ) { }

    ngOnInit() {
        this.getMetricsDescriptors();
    }

    onFormChange(e){
        console.log("e",e);
        console.log("filterSchema",this.filterSchema);
        console.log("metricsDescriptors",this.metricsDescriptors);
        this.selectWrightMetricsDescriptor();
        this.setFilterSchema();
        this.setTableConfig();
    }
    selectWrightMetricsDescriptor(){
        if(_.hasIn(this.filterObject,"name") && this.filterObject["name"] != ""){
            if(!(this.selectedMetricsDescriptors && this.selectedMetricsDescriptors.dcmMetricsName === this.filterObject['name'])){
                this.metricsDescriptors.forEach((descriptor:MetricsDescriptors)=>{
                    if(descriptor.dcmMetricsName === this.filterObject["name"]){
                        this.selectedMetricsDescriptors = descriptor;
                    }
                })
            }
        }else{
            this.selectedMetricsDescriptors = undefined;
        }
    }
    getMetrics(e){
        if(_.hasIn(this.filterObject,"name") && this.filterObject["name"] != ""){

            let validation = this.validFilter();
            if(validation.valid){
                this.cfpLoadingBar.start();

                let params = _.clone(this.filterObject);
                let name = params["name"];
                delete params["name"];

                this.service.getMetrics(name, params).subscribe(metrics=>{
                    let bin:number = _.parseInt(this.filterObject["bin"].toString()) || 1;
                    let currentServerTime = new Date(this.appService.serverTime);
                    this.metrics = metrics.map( (metric,i)=>{
                        if(i != 0){
                            currentServerTime.setMinutes(currentServerTime.getMinutes() - bin);
                        }
                        if(!_.isEmpty(metric)){
                            return {
                                time:j4care.formatDate(currentServerTime,"HH:mm"),
                                avg: j4care.round(metric["avg"],2),
                                count: metric["count"],
                                max: j4care.round(metric["max"],2),
                                min: j4care.round(metric["min"],2)
                            }
                        }else{
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
                this.appService.showError(validation.msg);
            }
        }else{
            this.appService.showError("Metrics Name is missing");
        }
    }
    validFilter(){
        let validation = {
            valid:true,
            msg:""
        };
        if(!_.hasIn(this.filterObject,"bin") || _.parseInt(this.filterObject["bin"].toString()) < 1 || _.parseInt(this.filterObject["bin"].toString()) > _.parseInt(this.selectedMetricsDescriptors.dcmMetricsRetentionPeriod)){
            validation = {
                valid:false,
                msg:"Bin value is not valid!"
            };
        }
        if(_.hasIn(this.filterObject,"limit") && this.filterObject["limit"] && _.parseInt(this.filterObject["limit"].toString()) < 1){
            validation = {
                valid:false,
                msg:"Limit value is not valid!"
            };
        }
        return validation;
    }

    setTableConfig(){
        this.tableConfig = {
            table:j4care.calculateWidthOfTable(this.service.getTableSchema(this.selectedMetricsDescriptors ? this.selectedMetricsDescriptors.dcmUnit :'')),
            filter:this.filterObject,
            calculate:false
        };
    }
    setFilterSchema(){
        this.filterSchema = j4care.prepareFlatFilterObject(
            this.service.getFilterSchema(
                this.metricsDescriptors.map((metricsDescriptor:MetricsDescriptors)=>{
                    return new SelectDropdown(metricsDescriptor.dcmMetricsName, metricsDescriptor.dicomDescription, metricsDescriptor.dicomDescription,undefined, undefined,metricsDescriptor);
                }),
                [1,5,15,this.selectedMetricsDescriptors ? this.selectedMetricsDescriptors.dcmMetricsRetentionPeriod : 60].map(nr=>{
                    return new SelectDropdown(nr,nr.toString(),nr.toString())
                })
            ),
            1
        );
    }

    getMetricsDescriptors(){
        this.service.getMetricsDescriptors().subscribe((res:MetricsDescriptors[])=>{
            this.metricsDescriptors = res;
            if(!res || res.length === 0){
                this.appService.showError("No Metrics Descriptors were found, please configure Metrics Descriptors first");
            }else{
                this.selectedMetricsDescriptors = res[0];
            }
            this.setTableConfig();
            this.setFilterSchema();
        },err=>{
            this.httpErrorHandler.handleError(err);
        })
    }
}
