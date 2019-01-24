import { Injectable } from '@angular/core';
import {StudyTab} from "../../interfaces";
import {Globalvar} from "../../constants/globalvar";
import {Aet} from "../../models/aet";
import {AeListService} from "../../configuration/ae-list/ae-list.service";

@Injectable()
export class StudyService {

  constructor(
      private aeListService:AeListService
  ) { }

  getFilterSchema(tab:StudyTab, aets:Aet[], hidden:boolean){
    switch(tab){
        case "patient":
          break;
        case "mwl":
          break;
        case "diff":
          break;
        default:
          return Globalvar.STUDY_FILTER_SCHEMA(aets,hidden);
    }
  }

  getAets = ()=> this.aeListService.getAets();

  getAes = ()=> this.aeListService.getAes();
}
