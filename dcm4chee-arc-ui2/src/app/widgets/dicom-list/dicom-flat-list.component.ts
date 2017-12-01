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
    moreStudies = {
        limit: 30,
        start: 0,
        loaderActive: false
    };
    @HostListener('window:resize', ['$event'])
    onResize(event) {
       if(!this.width){
        this.width = event.target.innerWidth;
       }else{
        if(Math.abs(this.width - event.target.innerWidth) > 10){
            this.calculateWidthOfTable();
        }
       }
    }
    @HostListener('window:scroll', ['$event'])
    loadMoreStudiesOnScroll(event) {
        this.loadMoreCheck();
        this.resetMoreCheck();
    }
    loadMoreCheck(){
        let hT = ($('.load_more').offset()) ? $('.load_more').offset().top : 0,
        hH = $('.load_more').outerHeight(),
        wH = $(window).height(),
        wS = window.pageYOffset;
        //ws
        if (wS > (hT + hH - wH)){
            this.loadMoreStudies();
        }
    }
    resetMoreCheck(){
        let hT = ($('.load_more_start').offset()) ? $('.load_more_start').offset().top : 0,
            hH = $('.load_more_start').outerHeight(),
            // wH = $(window).height(),
            wS = window.pageYOffset;
        console.log("reset (hT + hH - wH)",(hT + hH));
        console.log("reset wS",wS);
        if ((hT + hH) > wS ){
            this.resetMore();
        }
    }
    resetMore(){
        this.moreStudies =  {
            limit: 30,
            start: 0,
            loaderActive: false
        };
    }
    loadMoreStudies(){
        this.moreStudies.loaderActive = true;
        this.moreStudies.limit += 20;
        this.moreStudies.loaderActive = false;
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
