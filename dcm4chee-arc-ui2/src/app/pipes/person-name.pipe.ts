import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'personName'
})
export class PersonNamePipe implements PipeTransform {

  transform(attrs: any, tags?: any): any {
      console.log("Person Name passed tags are.........", tags);
      function valueOf(attrs, tag) {
          try {
              console.log("Person Name passed tags to valueOf are.........", tags);
              return attrs[tag].Value[0].Alphabetic;
          } catch (e) {
              return "false";
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
              console.log("Person Name passed tags to valueOfItem are.........", seqTag, tag);
              let item = attrs[seqTag].Value[0];
              return valueOf(item, tag);
          } catch (e) {
              return "false";
          }
      }

      function formatName(attrs, tagsVal) {
          let finalPNVal = '';
          let tags = tagsVal.split(".");
          let personName = tags.length == 1 ? valueOf(attrs, tags[0]) : valueOfItem(attrs, tags[0], tags[1]);
          if (personName == "false")
              return finalPNVal;

          let pnComponents = personName.split("^");
          if (pnComponents.length == 1)
              finalPNVal = pnComponents[0];

          if (pnComponents.length == 2) //given_name family_name
              finalPNVal = nonEmptyPNComponent(pnComponents[1], pnComponents[0]);

          if (pnComponents.length == 3) //given_name middle_name family_name
              finalPNVal = nonEmptyPNComponent(pnComponents[1], pnComponents[2])
                            + subsequentNonEmptyPNComponent(pnComponents[0]);

          if (pnComponents.length == 4) //name_prefix given_name middle_name family_name
              finalPNVal = nonEmptyPNComponent(pnComponents[3], pnComponents[1])
                            + subsequentNonEmptyPNComponent(pnComponents[2])
                            + subsequentNonEmptyPNComponent(pnComponents[0]);

          if (pnComponents.length == 5) //name_prefix given_name middle_name family_name, name_suffix
              finalPNVal = nonEmptyPNComponent(pnComponents[3], pnComponents[1])
                              + subsequentNonEmptyPNComponent(pnComponents[2])
                              + subsequentNonEmptyPNComponent(pnComponents[0])
                              + (pnComponents[4] != '' ? ", " + pnComponents[4] : '');

          return finalPNVal;
      }

      return formatName(attrs, tags);
  }

}
