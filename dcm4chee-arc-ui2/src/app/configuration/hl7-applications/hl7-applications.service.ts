import { Injectable } from '@angular/core';
import {WindowRefService} from "../../helpers/window-ref.service";
import {Globalvar} from "../../constants/globalvar";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {j4care} from "../../helpers/j4care.service";
import {SelectDropdown} from "../../interfaces";
import { loadTranslations } from '@angular/localize';
import {AppService} from "../../app.service";

@Injectable()
export class Hl7ApplicationsService {

  constructor(
      private $http:J4careHttpService,
      private appService:AppService
  ) { }

  getHl7ApplicationsList = (filters) => this.$http.get(`${j4care.addLastSlash(this.appService.baseUrl)}${Globalvar.HL7_LIST_LINK}${j4care.param(filters)}`);

    getFiltersSchema(){
        return j4care.prepareFlatFilterObject([
            {
                tag:"input",
                type:"text",
                filterKey:"dicomDeviceName",
                description:$localize `:@@device_name:Device Name`,
                placeholder:$localize `:@@device_name:Device Name`
            },{
                tag:"input",
                type:"text",
                filterKey:"hl7ApplicationName",
                description:$localize `:@@hl7-applications.hl7_application_name:hl7 Application Name`,
                placeholder:$localize `:@@hl7-applications.hl7_application_name:hl7 Application Name`
            },{
                tag:"input",
                type:"text",
                filterKey:"dicomApplicationCluster",
                description:$localize `:@@application_cluster:Application Cluster`,
                placeholder:$localize `:@@application_cluster:Application Cluster`
            },
            {
                tag: "button",
                id: "submit",
                text: $localize `:@@SUBMIT:SUBMIT`,
                description: $localize `:@@query_devices:Query Devices`
            }
        ],2)
    }
}
