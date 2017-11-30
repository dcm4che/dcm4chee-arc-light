import {Component, HostListener, Input, OnInit} from '@angular/core';
import * as _ from 'lodash';

@Component({
  selector: 'dicom-list',
  templateUrl: './dicom-flat-list.component.html'
})
export class DicomFlatListComponent implements OnInit {

    @Input() dicomObject;
    _ = _;
    width;
    table = [
        {
            title:"Patient's Name",
            code:"00100010",
            description:"Patient's Name",
            widthWeight:1,
            calculatedWidth:"20%"
        },{
            title:"Patient ID",
            code:"00100020",
            description:"Patient ID",
            widthWeight:1,
            calculatedWidth:"20%",
            cssClass:"hideMobile"
        },{
            title:"Birth Date",
            code:"00100030",
            description:"Patient's Birth Date",
            widthWeight:1,
            calculatedWidth:"20%",
            cssClass:"hideOn1000px"
        },{
            title:"Sex",
            code:"00100040",
            description:"Patient's Sex",
            widthWeight:1,
            calculatedWidth:"20%",
            cssClass:"hideOn800px"
        },{
            title:"Issuer of PID",
            code:"00100021",
            description:"Issuer of Patient ID",
            widthWeight:1,
            calculatedWidth:"20%",
            cssClass:"hideOn1100px"
        },
        {
            title:"Study ID",
            code:"00200010",
            description:"Study ID",
            widthWeight:1,
            calculatedWidth:"20%",
            cssClass:"hideOn800px"
        },{
            title:"Acc. Nr.",
            code:"00080050",
            description:"Accession Number",
            widthWeight:1,
            calculatedWidth:"20%",
            cssClass:"hideOn1400px"
        },
        {
            title:"Modality",
            code:"00080061",
            description:"Modalities in Study",
            widthWeight:0.6,
            calculatedWidth:"20%",
            cssClass:"hideMobile"
        },
        {
            title:"#S",
            code:"00201206",
            description:"Number of Study Related Series",
            widthWeight:0.2,
            calculatedWidth:"20%"
        },
        {
            title:"#I",
            code:"00201208",
            description:"Number of Study Related Instances",
            widthWeight:0.2,
            calculatedWidth:"20%"
        }
      ];
    constructor() { }

    @HostListener('window:resize', ['$event'])
    onResize(event) {
       console.log("res",event.target.innerWidth);
       const values = [
            12.5,
            12.5,
            12.5,
            12.5,
            12.5,
            12.5,
            12.5,
            7.5,
            2.5,
            2.5
        ];
       let sum = 0;
       values.forEach(k=>{
           sum += k;
       });
       console.log("sum",sum);
       if(!this.width){
        this.width = event.target.innerWidth;
       }else{
        if(Math.abs(this.width - event.target.innerWidth) > 10){
            this.calculateWidthOfTable();
        }
       }
    }

    ngOnInit() {
        this.calculateWidthOfTable();
    }
    calculateWidthOfTable(){
        let summ = 0;
        _.forEach(this.table,(m,i)=>{
            summ += m['widthWeight'];
        });
        _.forEach(this.table,(m,i)=>{
            m['calculatedWidth'] =  ((m['widthWeight'] * 100)/summ)+"%";
        });
    };

}
