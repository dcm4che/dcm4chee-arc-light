import { Injectable } from '@angular/core';
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {j4care} from "../../helpers/j4care.service";
import {FilterSchema, MetricsDescriptors, SelectDropdown} from "../../interfaces";

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
          text:"Bin",
          description:"Data bin size in minutes",
          placeholder:"Bin"
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

  ]
}
