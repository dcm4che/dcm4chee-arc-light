import {Directive, ElementRef, Input, OnInit} from '@angular/core';
import {J4careHttpService} from "../j4care-http.service";
import * as _ from 'lodash-es';

@Directive({
    selector: '[svg]',
    standalone: true
})
export class SvgDirective implements OnInit{
    @Input() svg: string;
    @Input() svgWidth: string;
    @Input() svgHeight: string;
    @Input() svgPath: string;
     parser = new DOMParser();
    private static cache = new Map<string, string>();

  constructor(
      private el: ElementRef,
      private $http:J4careHttpService
  ) {

  }

    async ngOnInit(): Promise<void> {
        if (this.svg && this.svg.trim().substring(0, 4) === "<svg" && this.svg.trim().substring(this.svg.trim().length - 6) === "</svg>"){
            this.createSvg(this.svg);
        }
        if(this.svgPath){
            await this.loadSvgFromPath();
        }
    }
    private async loadSvgFromPath() {

        let svgText: string;

        if (SvgDirective.cache.has(this.svgPath)) {
            svgText = SvgDirective.cache.get(this.svgPath)!;
        } else {
            svgText = await this.$http
                .get(this.svgPath, { responseType: 'text' })
                .toPromise();
            if (svgText && _.has(svgText, 'body') && _.has(svgText, 'status') && svgText["status"] === 200) {
                svgText = svgText["body"];
            }
            SvgDirective.cache.set(this.svgPath, svgText);
        }

        if (this.isValidSvg(svgText)) {
            this.createSvg(svgText);
        }
    }
    private isValidSvg(svg: string): boolean {
        const s = svg.trim();
        return s.startsWith('<svg') && s.endsWith('</svg>');
    }

    private createSvg(svgText?: string): void {
      let img = this.parser.parseFromString(svgText, 'image/svg+xml');
      if(this.svgWidth){
        img.documentElement.setAttribute("width",this.svgWidth);
      }
      if(this.svgHeight){
          img.documentElement.setAttribute("height",this.svgHeight);
      }
      this.el.nativeElement.appendChild(img.documentElement);
  }

}
