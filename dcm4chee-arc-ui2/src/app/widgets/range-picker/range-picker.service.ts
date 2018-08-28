import { Injectable } from '@angular/core';
import {j4care} from "../../helpers/j4care.service";

@Injectable()
export class RangePickerService {

    modeMap = {};
    modeMapReversed = {};
    constructor() { }

    getRangeFromKey(mode){
      let model;
      let firstDate = new Date();
      let secondDate = new Date();
      let quarterRangeMonth;
      let todayDay;
      switch (mode){
          case 'today':
              firstDate.setDate(firstDate.getDate());
              model = j4care.convertDateToString(firstDate);
              break;
          case 'yesterday':
              firstDate.setDate(firstDate.getDate()-1);
              model = j4care.convertDateToString(firstDate);
              break;
          case 'this_week':
              todayDay = firstDate.getDay();
              if(todayDay === 0){
                  todayDay = 7;
              }
              firstDate.setDate(firstDate.getDate()-(todayDay-1));
              if(this.eqDate(firstDate, secondDate))
                  model = j4care.convertDateToString(firstDate);
              else
                  model = `${j4care.convertDateToString(firstDate)}-${j4care.convertDateToString(secondDate)}`;
              break;
          case 'last_week':
              todayDay = firstDate.getDay();
              if(todayDay === 0){
                  todayDay = 7;
              }
              firstDate.setDate(firstDate.getDate()-(todayDay-1));
              firstDate.setDate(firstDate.getDate()-7);
              secondDate.setDate(firstDate.getDate()+6);
              if(this.eqDate(firstDate, secondDate))
                  model = j4care.convertDateToString(firstDate);
              else
                  model = `${j4care.convertDateToString(firstDate)}-${j4care.convertDateToString(secondDate)}`;
              break;
          case 'this_month':
              // firstDate.setMonth(firstDate.getMonth()-1);
              firstDate.setDate(1);
              model = `${j4care.convertDateToString(firstDate)}-${j4care.convertDateToString(secondDate)}`;
              break;
          case 'last_month':
              firstDate.setMonth(firstDate.getMonth()-1);
              firstDate.setDate(1);
              secondDate.setDate(1);
              secondDate.setDate(secondDate.getDate()-1);
              model = `${j4care.convertDateToString(firstDate)}-${j4care.convertDateToString(secondDate)}`;
              break;
          case 'this_quarter':
              quarterRangeMonth = this.getQuarterRange(this.getQuarterIndex(firstDate.getMonth()));
              model = `${j4care.convertDateToString(this.getStartOfMonth(quarterRangeMonth.start))}-${j4care.convertDateToString(this.getEndOfMonth(quarterRangeMonth.end))}`;
              break;
          case 'last_quarter':
              quarterRangeMonth = this.getQuarterRange(this.getQuarterIndex(firstDate.getMonth())-1);
              model = `${j4care.convertDateToString(this.getStartOfMonth(quarterRangeMonth.start))}-${j4care.convertDateToString(this.getEndOfMonth(quarterRangeMonth.end))}`;
              break;
          case 'this_year':
              firstDate.setDate(1);
              firstDate.setMonth(0);
              model = `${j4care.convertDateToString(firstDate)}-${j4care.convertDateToString(secondDate)}`;
              break;
          case 'last_year':
              firstDate.setFullYear(firstDate.getFullYear()-1);
              firstDate.setDate(1);
              firstDate.setMonth(0);
              firstDate.setDate(firstDate.getDate()-1);
              secondDate.setDate(1);
              secondDate.setMonth(0);
              secondDate.setDate(secondDate.getDate()-1);
              model = `${j4care.convertDateToString(firstDate)}-${j4care.convertDateToString(secondDate)}`;
              break;
      }
      return model || mode;
    }

    getModeFromRange(range){
        if(Object.keys(this.modeMap).length === 0 && Object.keys(this.modeMapReversed).length === 0){
            [
                'today',
                'yesterday',
                'this_week',
                'this_month',
                'last_week',
                'last_month',
                'this_quarter',
                'last_quarter',
                'this_year',
                'last_year'
            ].forEach(m=>{
                this.modeMap[m] = this.getRangeFromKey(m);
                this.modeMapReversed[this.getRangeFromKey(m)] = m;
            })
        }
        if(this.modeMapReversed[range]){
            return this.modeMapReversed[range];
        }
        return '';

    }
    getQuarterIndex(month){
      return parseInt((month/3).toString());
    }
    getQuarterRange(quarterIndex){
      let quarterStart = ((quarterIndex*3)+1);
      let quarterEnd = quarterStart + 2;
      return {
          start:quarterStart,
          end:quarterEnd
      }
    }

    getStartOfMonth(month){
      let newDate = new Date();
      newDate.setMonth(month-1);
      newDate.setDate(1);
      return newDate;
    }
    getEndOfMonth(month){
      let newDate = new Date();
      newDate.setMonth(month);
      newDate.setDate(1);
      newDate.setDate(newDate.getDate()-1);
      return newDate;
    }
    eqDate(date1,date2){
      return (date1.getFullYear() === date2.getFullYear() && date1.getMonth() === date2.getMonth() && date1.getDate() === date2.getDate());
    }
}
