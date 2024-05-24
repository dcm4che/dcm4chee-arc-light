import { Pipe, PipeTransform } from '@angular/core';
import * as _ from 'lodash-es';
import {PATIENT_NAME_PARTS} from "../models/patient-dicom";

@Pipe({
  name: 'personName'
})
export class PersonNamePipe implements PipeTransform {

    transform(nameObject: any, format?: any): any {
        if((!format || format === "") && nameObject && nameObject.Alphabetic){
            return nameObject.Alphabetic;
        }
        format = format || `{NAME-PREFIX} {GIVEN-NAME} {MIDDLE-NAME} {FAMILY-NAME}, {NAME-SUFFIX}`;
        const nameKeys = PATIENT_NAME_PARTS;
        try {
            const regex = {
              names: /({FAMILY-NAME})|({GIVEN-NAME})|({MIDDLE-NAME})|({NAME-PREFIX})|({NAME-SUFFIX})|({I_FAMILY-NAME})|({I_GIVEN-NAME})|({I_MIDDLE-NAME})|({I_NAME-PREFIX})|({I_NAME-SUFFIX})|({P_FAMILY-NAME})|({P_GIVEN-NAME})|({P_MIDDLE-NAME})|({P_NAME-PREFIX})|({P_NAME-SUFFIX})/g,
              //startingEndingSpaces: /^(\s*[\(\[\,.|\s]]*\s*[\)\[\],.|\s]*\s*)|(\s*[\(\[\,|]]*\s*[\)\[\],.|]*\s*)$/gm,
              startingEndingSpaces: /^(\s*[\[,.|\s]]*\s*[\[\],.|\s]*\s*)|(\s*[\[,| ]]*\s*[\[\],.|]*\s*)$/gm,
              parenthesesWithSpaces: /([({\[]\s*[)}\]])/gm,
              multipleSpaces: /(^\s|\s$|\s{2,})/gm
            };

            if (typeof nameObject === "string") {
                if(nameObject.startsWith("==") && format.indexOf("P_") > -1){
                    nameObject = {
                        Phonetic: nameObject.replace(/=/g,"")
                    }
                }else if(nameObject.startsWith("=") && format.indexOf("I_") > -1){
                    nameObject = {
                        Ideographic: nameObject.replace(/=/g,"")
                    }
                }else{
                    nameObject = {
                      Alphabetic: nameObject.replace(/=/g,"")
                    }
                }
            }
            if (format && typeof format === "string" && (_.hasIn(nameObject, "Alphabetic") || _.hasIn(nameObject, "Ideographic") || _.hasIn(nameObject, "Phonetic"))) {
              let names = extractNameElements(nameObject);
              let formattedName = format.replace(regex.names, (...args) => {
                  let i = 0;
                  while (i <= 14) {
                      if (args[i + 1]) {
                          return names[nameKeys[i]] || "";
                      }
                      i++;
                  }
                  return "";
              });
              formattedName = formattedName.replace(regex.parenthesesWithSpaces, (...args) => ""); // Remove the starting or ending empty spaces or signs that should not be used if there is no name string
            formattedName = formattedName.replace(regex.startingEndingSpaces, (...args) => ""); // Remove the starting or ending empty spaces or signs that should not be used if there is no name string
            formattedName = formattedName.replace(regex.multipleSpaces, (...args) => " ");      // Replace empty spaces if there are more then one in a row with only one empty space
              return formattedName;
            }
        }catch (e){
          if (_.hasIn(nameObject, "Alphabetic")) {
              return _.get(nameObject, "Alphabetic");
          }
          return "";
        }
        function extractNameElements(tempNameObject) {
            let result = {};
            [
                "Alphabetic",
                "Ideographic",
                "Phonetic"
            ].forEach((key, keyIndex) => {
                if (_.hasIn(tempNameObject, key) && typeof _.get(tempNameObject, key) === "string") {
                    tempNameObject[key].split("^").forEach((el, i) => {
                        result[nameKeys[i + (5 * keyIndex)]] = el;
                    });

                }
            });
            return result;
        }
    }
}
