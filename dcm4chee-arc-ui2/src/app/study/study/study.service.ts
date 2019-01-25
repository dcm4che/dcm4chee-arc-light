import { Injectable } from '@angular/core';
import {FilterSchema, StudyTab} from "../../interfaces";
import {Globalvar} from "../../constants/globalvar";
import {Aet} from "../../models/aet";
import {AeListService} from "../../configuration/ae-list/ae-list.service";
import {j4care} from "../../helpers/j4care.service";

@Injectable()
export class StudyService {

    constructor(
      private aeListService:AeListService
    ) { }

    getFilterSchema(tab:StudyTab, aets:Aet[], quantityText:{count:string,size:string}, hidden:boolean){
        let schema:FilterSchema;
        let lineLength:number = 3;
        switch(tab){
            case "patient":
                schema = Globalvar.PATIENT_FILTER_SCHEMA(aets,hidden);
                lineLength = hidden ? 1:2;
                break;
            case "mwl":
                break;
            case "diff":
                schema = [

                ];
                break;
            default:
                schema = Globalvar.STUDY_FILTER_SCHEMA(aets,hidden);
                lineLength = hidden ? 2:3;
        }
        if(!hidden){
            schema.push({
                    tag: "button",
                    id: "count",
                    text: quantityText.count,
                    description: "QUERIE ONLY THE COUNT"
                },{
                    tag: "button",
                    id: "size",
                    text: quantityText.size,
                    description: "QUERIE ONLY THE SIZE"
                },
                {
                    tag: "button",
                    id: "submit",
                    text: "SUBMIT",
                    description: "Query Studies"
                });
        }
        return {
            lineLength:lineLength,
            schema:j4care.prepareFlatFilterObject(schema,lineLength)
        }
    }

    getAets = ()=> this.aeListService.getAets();

    getAes = ()=> this.aeListService.getAes();
}
