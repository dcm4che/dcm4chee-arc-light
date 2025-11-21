import {Directive, ElementRef, Input, OnInit} from '@angular/core';

@Directive({
    selector: '[svg]',
    standalone: true
})
export class SvgDirective implements OnInit{
    @Input() svg: string;
    @Input() svgWidth: string;
    @Input() svgHeight: string;
     parser = new DOMParser();

  constructor(
      private el: ElementRef,
  ) {

  }

    ngOnInit(): void {
        if (this.svg && this.svg.trim().substring(0, 4) === "<svg" && this.svg.trim().substring(this.svg.trim().length - 6) === "</svg>"){
            this.createSvg();
        }
    }
  private createSvg(){
      let img = this.parser.parseFromString(this.svg, 'image/svg+xml');
      if(this.svgWidth){
        img.documentElement.setAttribute("width",this.svgWidth);
      }
      if(this.svgHeight){
          img.documentElement.setAttribute("height",this.svgHeight);
      }
      this.el.nativeElement.appendChild(img.documentElement);
  }

}
