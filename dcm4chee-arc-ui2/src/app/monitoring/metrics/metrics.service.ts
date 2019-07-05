import { Injectable } from '@angular/core';
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {j4care} from "../../helpers/j4care.service";
import {FilterSchema, MetricsDescriptors, SelectDropdown} from "../../interfaces";
import {TableSchemaElement} from "../../models/dicom-table-schema-element";

@Injectable()
export class MetricsService {
    url = {
        METRICS_DESCRIPTORS: `../metrics`,
        METRICS: (name) => `../metrics/${name}`
    };

    constructor(
        private $http:J4careHttpService
    ) { }

    getMetricsDescriptors = ()=>this.$http.get(this.url.METRICS_DESCRIPTORS);

    getMetrics = (name, params)=> {
        return this.$http.get(`${this.url.METRICS(name)}${j4care.param(params)}`);
    };

    getFilterSchema = (nameDescriptors:any):FilterSchema => [
        {
          tag:"select",
          type:"text",
          filterKey:"name",
          options:nameDescriptors,
          text:"Name",
          description:"Metrics Name",
          placeholder:"Metrics Name"
        },
        {
          tag:"input",
          type:"number",
          filterKey:"bin",
          description:"Data bin size in minutes",
          placeholder:"Bin (min)"
        },
        {
          tag:"input",
          type:"number",
          filterKey:"limit",
          text:"Limit",
          description:"Maximal number of returned data entries",
          placeholder:"Limit"
        },
        {
          tag:"button",
          text:"SUBMIT",
          description:"Get Metrics"
        }

    ];

    getTableSchema(){
        return [
            new TableSchemaElement({
                type:"value",
                title:"Time",
                header:"Time",
                widthWeight:1,
                pathToValue:"time"
            }),
            new TableSchemaElement({
                type:"value",
                title:"Count",
                header:"Count",
                widthWeight:1,
                pathToValue:"count"
            }),
            new TableSchemaElement({
                type:"value",
                title:"Min[MB/s]",
                header:"Min[MB/s]",
                widthWeight:1,
                pathToValue:"min"
            }),
            new TableSchemaElement({
                type:"value",
                header:"Avg[MB/s]",
                title:"Avg[MB/s]",
                widthWeight:1,
                pathToValue:"avg"
            }),
            new TableSchemaElement({
                type:"value",
                header:"Max[MB/s]",
                title:"Max[MB/s]",
                widthWeight:1,
                pathToValue:"max"
            })
        ]
    }
}
