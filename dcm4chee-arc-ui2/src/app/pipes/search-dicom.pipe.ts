import { Pipe, PipeTransform } from '@angular/core';
import * as _ from 'lodash';
import {StudyDicom} from "../models/study-dicom";

@Pipe({
  name: 'searchDicom'
})
export class SearchDicomPipe implements PipeTransform {

  transform(value: any, args?: any): any {
      if (args === '' || !args){
          return value;
      }else{
          if (value){
            return value.filter((obj) => {

                let studies = "";
                if(_.hasIn(obj,"studies[0].attrs")){
                    (<StudyDicom[]> _.get(obj,"studies")).forEach((study:StudyDicom)=>{
                        studies += JSON.stringify(study.attrs).toLowerCase();
                    })
                }else{
                    if(_.hasIn(obj,"attrs[0]")){
                        studies = JSON.stringify(obj.attrs).toLowerCase();
                    }
                }

                return studies.indexOf(args.toLowerCase()) !== -1;
            });
          }
      }
  }

}
