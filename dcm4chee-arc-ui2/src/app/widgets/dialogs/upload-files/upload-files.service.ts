import { Injectable } from '@angular/core';
import {j4care} from "../../../helpers/j4care.service";
import * as _ from 'lodash-es';
import {Observable} from "rxjs";

@Injectable({
  providedIn: 'root'
})
export class UploadFilesService {

  constructor() { }
  fileTypeFromExt(fileTypeOrExt:string) {
    switch (fileTypeOrExt) {
      case "mtl":
        return "model/mtl";
      case "stl":
        return "model/stl";
      case "obj":
        return "model/obj";
      case "genozip":
        return "application/vnd.genozip";
      case "vcf.bz2":
      case "vcfbzip2":
      case "vcfbz2":
      case "application/prs.vcfbzip2":
        return "application/prs.vcfbzip2";
      case "boz":
      case "bz2":
      case "application/x-bzip2":
        return "application/x-bzip2";
      case "jph":
        return "image/jph";
      case "jhc":
        return "image/jphc";
      case "j2c":
      case "image/x-jp2-codestream":
        return "image/j2c";
      default:
        return fileTypeOrExt;
    }
  }

  fixFileSpecificEntries(file,object){
    if(_.hasIn(object,"00420011.BulkDataURI")){
      _.set(object,"00420011.BulkDataURI", `file/${file.name}`);
    }
    if(_.hasIn(object,"7FE00010.BulkDataURI")){
      _.set(object,"7FE00010.BulkDataURI", `file/${file.name}`);
    }
    if(_.hasIn(object,"00080018.Value[0]")){
      _.set(object,"00080018.Value[0]", j4care.generateOIDFromUUID());
    }
  }

  fileTypeOrExt(file:File):Observable<string>{
    return new Observable((observer)=>{
      try{
        let fileType = file.type;
        let fileExt = file.name.indexOf(".") > -1 ? file.name.substr(file.name.lastIndexOf(".") + 1) : undefined;
        if((fileType && fileType != "") ||  ( fileExt && fileExt != "")){
          observer.next(fileType.length == 0 ? fileExt : fileType);
          observer.complete();
        }else{
          let reader = new FileReader();
          let filePart = file.slice(128,132);
          reader.readAsArrayBuffer(filePart);
          reader.onload = function() {
            let array = new Uint8Array(<any>reader.result);
            let convertedString = "";
            array.forEach(b=>{
              convertedString = convertedString + String.fromCharCode(b)
            })
            if(convertedString==="DICM"){
              observer.next("application/dicom");
            }else{
              observer.next("NO_TYPE_FOUND");
            }
            observer.complete();
          };
        }
      }catch(e){
        observer.next("NO_TYPE_FOUND");
        observer.complete();
      }
    });
  }
}
