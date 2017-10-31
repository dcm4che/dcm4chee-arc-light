import { Component, OnInit } from '@angular/core';
import {AppService} from "../../app.service";
import {SlimLoadingBarService} from "ng2-slim-loading-bar";
import * as _ from 'lodash';
import {AeListService} from "../../ae-list/ae-list.service";
import {Observable} from "rxjs/Observable";
import {ExternalRetrieveService} from "./external-retrieve.service";
import {HttpErrorHandler} from "../../helpers/http-error-handler";

@Component({
  selector: 'external-retrieve',
  templateUrl: './external-retrieve.component.html'
})
export class ExternalRetrieveComponent implements OnInit {
    before;
    localAET;
    remoteAET;
    destinationAET;
    filterSchema;
    filterObject;
    externalRetrieveEntries;
    _ = _;
  constructor(
      public cfpLoadingBar: SlimLoadingBarService,
      public mainservice: AppService,
      public aeListService:AeListService,
      public service:ExternalRetrieveService,
      public httpErrorHandler:HttpErrorHandler,
  ) { }

    ngOnInit(){
        this.initCheck(10);
    }
    initCheck(retries){
        let $this = this;
        if(_.hasIn(this.mainservice,"global.authentication")){
            this.init();
        }else{
            if (retries){
                setTimeout(()=>{
                    $this.initCheck(retries-1);
                },20);
            }else{
                this.init();
            }
        }
    }
    init(){
        this.filterObject = {
            limit:20
        };
        Observable.forkJoin(
            this.aeListService.getAes(),
            this.aeListService.getAets()
        ).subscribe((response)=>{
            this.remoteAET = this.destinationAET = (<any[]>response[0]).map(ae => {
                return {
                    value:ae.dicomAETitle,
                    text:ae.dicomAETitle
                }
            });
            this.localAET = (<any[]>response[1]).map(ae => {
                return {
                    value:ae.dicomAETitle,
                    text:ae.dicomAETitle
                }
            });
            this.initSchema();
        });
    }

    initSchema(){
        this.filterSchema = this.service.getFilterSchema(this.localAET,this.destinationAET,this.remoteAET);

    }
    onSubmit(e){
        this.service.getExternalRetrieveEntries(e).subscribe(
            res =>  {
                this.externalRetrieveEntries = res;
            },
            err => {
                this.httpErrorHandler.handleError(err);
            });
    }
}
