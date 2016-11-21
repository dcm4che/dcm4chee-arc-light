import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'orderBy'
})
export class OrderByPipe implements PipeTransform {

  transform(value: any, args?: any, args2?:boolean): any {
      if(value){
          if(typeof args2 !== 'undefined'){
              if(args2 === true){
                  value.sort((a: any, b: any) => {
                      if(typeof a[args] === "object"){
                          let astring = "";
                          let bstring = "";
                          for(let oa in a[args]){
                              if(typeof oa === "string"){
                                  astring = oa;
                              }
                          }
                          for(let ob in b[args]){
                              if(typeof ob === "string"){
                                  bstring = ob;
                              }
                          }
                          if (astring < bstring) {
                              return -1;
                          } else if (astring > bstring) {
                              return 1;
                          } else {
                              return 0;
                          }
                      }else{
                          if (a[args] < b[args]) {
                              return -1;
                          } else if (a[args] > b[args]) {
                              return 1;
                          } else {
                              return 0;
                          }
                      }
                  });
              }else{
                  value.sort((a: any, b: any) => {
                      if(typeof a[args] === "object"){
                          let astring = "";
                          let bstring = "";
                          for(let oa in a[args]){
                              if(typeof oa === "string"){
                                  astring = oa;
                              }
                          }
                          for(let ob in b[args]){
                              if(typeof ob === "string"){
                                  bstring = ob;
                              }
                          }
                          if (astring > bstring) {
                              return -1;
                          } else if (astring < bstring) {
                              return 1;
                          } else {
                              return 0;
                          }
                      }else{
                          if (a[args] > b[args]) {
                              return -1;
                          } else if (a[args] < b[args]) {
                              return 1;
                          } else {
                              return 0;
                          }
                      }
                  });
              }
          }else{
                value.sort((a: any, b: any) => {
                  if (a[args] < b[args]) {
                      return -1;
                  } else if (a[args] > b[args]) {
                      return 1;
                  } else {
                      return 0;
                  }
                });
          }
      }
    return value;
  }

}
