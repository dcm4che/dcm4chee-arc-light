import { Component, OnInit } from '@angular/core';
import {WebAppsListService} from "./web-apps-list.service";
import { DicomNetworkConnection, FilterSchema, SelectDropdown} from "../../interfaces";
import {j4care} from "../../helpers/j4care.service";
import {Device} from "../../models/device";
import {Aet} from "../../models/aet";
import {LoadingBarService} from "@ngx-loading-bar/core";
import {HttpErrorHandler} from "../../helpers/http-error-handler";
import * as _ from "lodash";
import {Observable} from "rxjs/Observable";

@Component({
  selector: 'app-web-apps-list',
  templateUrl: './web-apps-list.component.html',
  styleUrls: ['./web-apps-list.component.scss']
})
export class WebAppsListComponent implements OnInit {

    filterObject = {};
    filterSchema:FilterSchema;
    tableConfig;
    webApps;
    filterHeight = 2;

    serviceClasses:SelectDropdown<string>;
    devices:SelectDropdown<Device>;
    aes:Aet[];
    constructor(
        private service:WebAppsListService,
        private cfpLoadingBar:LoadingBarService,
        private httpErrorHandler:HttpErrorHandler
    ){}

    ngOnInit() {
        this.init();
    }

    submit(e){
        this.cfpLoadingBar.start();
        this.service.getWebApps(this.filterObject).subscribe(webApps=>{
            this.webApps = webApps.map(webApp=>{
                webApp["url"] = webApp.dicomNetworkConnection.map((networkConnection:DicomNetworkConnection)=>{
                    return `${j4care.getUrlFromDicomNetworkConnection(networkConnection)}${j4care.meyGetString(webApp,"dcmWebServicePath")}`;
                });
                return webApp;
            });
            this.cfpLoadingBar.complete();
        },err=>{
            this.httpErrorHandler.handleError(err);
            this.cfpLoadingBar.complete();
        })
    }

    init(){
        Observable.forkJoin(
            this.service.getServiceClasses(),
            this.service.getDevices(),
            this.service.getAes()
        ).subscribe(res=>{
            this.serviceClasses    = <any>res[0];
            this.devices           = <any>res[1];
            this.aes               = <any>res[2];

            this.setFilterSchema();
            this.setTableConfig();
        },err=>{
            this.httpErrorHandler.handleError(err);
        });
    }

    setTableConfig(){
        this.tableConfig = {
            table:j4care.calculateWidthOfTable(this.service.getTableSchema()),
            filter:this.filterObject,
            calculate:false
        };
    }

    setFilterSchema(){
        this.filterSchema = j4care.prepareFlatFilterObject(
          this.service.getFilterSchema(
              this.devices,
              this.aes,
              this.serviceClasses
          ),
          this.filterHeight
        );
    }

}
