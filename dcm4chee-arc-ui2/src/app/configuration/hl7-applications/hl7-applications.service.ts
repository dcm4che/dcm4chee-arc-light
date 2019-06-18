import { Injectable } from '@angular/core';
import {WindowRefService} from "../../helpers/window-ref.service";
import {Globalvar} from "../../constants/globalvar";
import {Http} from "@angular/http";
import {J4careHttpService} from "../../helpers/j4care-http.service";

@Injectable()
export class Hl7ApplicationsService {

  constructor(
      private $http:J4careHttpService
  ) { }

  getHl7ApplicationsList(filters){
      return this.$http.get(
          Globalvar.HL7_LIST_LINK + filters,
      ).map(res => {
          let resjson;
          try {
/*              let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/");
              if(pattern.exec(res.url)){
                  WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";
              }*/
              resjson = res;
          } catch (e) {
              resjson = {};
          }
          return resjson;
      });
  }
}
