import { Injectable } from '@angular/core';
import {WindowRefService} from "../../helpers/window-ref.service";
import {Globalvar} from "../../constants/globalvar";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {j4care} from "../../helpers/j4care.service";
import {SelectDropdown} from "../../interfaces";

@Injectable()
export class Hl7ApplicationsService {

  constructor(
      private $http:J4careHttpService
  ) { }

  getHl7ApplicationsList = (filters) => this.$http.get(`${Globalvar.HL7_LIST_LINK}${j4care.param(filters)}`);

    getFiltersSchema(){
        return j4care.prepareFlatFilterObject([
            {
                tag:"input",
                type:"text",
                filterKey:"dicomDeviceName",
                description:"Device name",
                placeholder:"Device name"
            },{
                tag:"input",
                type:"text",
                filterKey:"hl7ApplicationName",
                description:"hl7 Application Name",
                placeholder:"hl7 Application Name"
            },{
                tag:"input",
                type:"text",
                filterKey:"dicomApplicationCluster",
                description:"Application Cluster",
                placeholder:"Application Cluster"
            },
            {
                tag: "button",
                id: "submit",
                text: "SUBMIT",
                description: "Query Devices"
            }
        ],2)
    }
}
