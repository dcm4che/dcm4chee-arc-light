import { Injectable } from '@angular/core';
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {j4care} from "../../helpers/j4care.service";
import {FilterSchema, MetricsDescriptors, SelectDropdown} from "../../interfaces";
import {TableSchemaElement} from "../../models/dicom-table-schema-element";
import {AppService} from "../../app.service";

@Injectable()
export class MetricsService {
    url = {
        METRICS_DESCRIPTORS: `${j4care.addLastSlash(this.appService.baseUrl)}metrics`,
        METRICS: (name) => `${j4care.addLastSlash(this.appService.baseUrl)}metrics/${name}`
    };

    constructor(
        private $http:J4careHttpService,
        private appService:AppService
    ) { }

    getMetricsDescriptors = ()=>this.$http.get(this.url.METRICS_DESCRIPTORS);

    getMetrics = (name, params)=> {
        return this.$http.get(`${this.url.METRICS(name)}${j4care.param(params)}`);
    };

    getFilterSchema = (nameDescriptors:any,binOptions):FilterSchema => [
        {
            tag:"html-select",
            type:"text",
            filterKey:"name",
            options:nameDescriptors,
            text:"Name",
            showSearchField:true,
            description:$localize `:@@metrics_name:Metrics Name`,
            placeholder:$localize `:@@metrics_name:Metrics Name`
        },
        {
            tag:"editable-select",
            type:"number",
            filterKey:"bin",
            min:1,
            max:60,
            options:binOptions,
            description:$localize `:@@data_bin_size_in_minutes:Data bin size in minutes`,
            placeholder:$localize `:@@bin_min:Bin (min)`
        },
        {
          tag:"input",
          type:"number",
          filterKey:"limit",
          min:1,
          text:$localize `:@@limit:Limit`,
          description:$localize `:@@maximal_number_of_returned_data_entries:Maximal number of returned data entries`,
          placeholder:$localize `:@@limit:Limit`
        },
        {
          tag:"button",
          text:$localize `:@@SUBMIT:SUBMIT`,
          description:$localize `:@@get_metrics:Get Metrics`
        }

    ];

    getTableSchema(unit:string){
        let unitString = "";
        if(unit){
            unitString = `[${unit}]`
        }
        return [
            new TableSchemaElement({
                type:"value",
                title:$localize `:@@time:Time`,
                header:$localize `:@@time:Time`,
                widthWeight:1,
                pathToValue:"time"
            }),
            new TableSchemaElement({
                type:"value",
                title:$localize `:@@count:Count`,
                header:$localize `:@@count:Count`,
                widthWeight:1,
                pathToValue:"count"
            }),
            new TableSchemaElement({
                type:"value",
                title:$localize `:@@min_unit:Min${unitString}:unit:`,
                header:$localize `:@@min_unit:Min${unitString}:unit:`,
                widthWeight:1,
                pathToValue:"min"
            }),
            new TableSchemaElement({
                type:"value",
                header:$localize `:@@avg_unit:Avg${unitString}`,
                title:$localize `:@@avg_unit:Avg${unitString}`,
                widthWeight:1,
                pathToValue:"avg"
            }),
            new TableSchemaElement({
                type:"value",
                header:$localize `:@@max_unit:Max${unitString}`,
                title:$localize `:@@max_unit:Max${unitString}`,
                widthWeight:1,
                pathToValue:"max"
            })
        ]
    }
}
