import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'personName'
})
export class PersonNamePipe implements PipeTransform {

  transform(attrs: any, tags?: any): any {
      function valueOf(attrs, tag) {
          try {
              return attrs[tag].Value[0].Alphabetic;
          } catch (e) {
              return false;
          }
      }

      function subsequentNonEmptyPNComponent(val) {
          return val != '' ? " " + val : '';
      }

      function nonEmptyPNComponent(first, second) {
          return first != ''
                    ? second != ''
                        ? first + " " + second
                        : first
                    : second != ''
                        ? second : '';
      }

      function valueOfItem(attrs, seqTag, tag) {
          try {
              let item = attrs[seqTag].Value[0];
              return valueOf(item, tag);
          } catch (e) {
              return false;
          }
      }

      function formatName(attrs, tagsVal) {
          let finalPNVal;
          let tags = tagsVal.split(".");
          let personName = tags.length == 1 ? valueOf(attrs, tags[0]) : valueOfItem(attrs, tags[0], tags[1]);
          if (personName === false)
              return '';

          let pnComponents = personName.split("^");
          switch (pnComponents.length) {
              case 5 :
                  finalPNVal = nonEmptyPNComponent(pnComponents[3], pnComponents[1])
                      + subsequentNonEmptyPNComponent(pnComponents[2])
                      + subsequentNonEmptyPNComponent(pnComponents[0])
                      + (pnComponents[4] != '' ? ", " + pnComponents[4] : '');
                  break;
              case 4 :
                  finalPNVal = nonEmptyPNComponent(pnComponents[3], pnComponents[1])
                      + subsequentNonEmptyPNComponent(pnComponents[2])
                      + subsequentNonEmptyPNComponent(pnComponents[0]);
                  break;
              case 3:
                  finalPNVal = nonEmptyPNComponent(pnComponents[1], pnComponents[2])
                      + subsequentNonEmptyPNComponent(pnComponents[0]);
                  break;
              case 2 :
                  finalPNVal = nonEmptyPNComponent(pnComponents[1], pnComponents[0]);
                  break;
              case 1 :
                  finalPNVal = pnComponents[0];
                  break;
              default :
                  finalPNVal = '';
          }

          return finalPNVal;
      }

      return formatName(attrs, tags);
  }

}
