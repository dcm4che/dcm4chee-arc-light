import {Directive, ElementRef, OnInit, Renderer2} from '@angular/core';
import {Input} from '@angular/core';
import {Globalvar} from '../constants/globalvar';
import * as _ from 'lodash';

@Directive({
  selector: '[placeholderchanger]'
})
export class PlaceholderchangerDirective implements OnInit{

    @Input('placeholderchanger') inputAttribut: any;

    constructor(private el: ElementRef, private renderer: Renderer2) {}

    ngOnInit() {
        if(this.inputAttribut.code == '00100020' && this.inputAttribut.externalInternalAetMode == "external"){
            this.renderer.setAttribute(this.el.nativeElement, 'placeholder', this.inputAttribut.name);
        }else{
            if ((this.inputAttribut.code in Globalvar.IODPLACEHOLDERS) && _.hasIn(Globalvar.IODPLACEHOLDERS[this.inputAttribut.code], this.inputAttribut.mode)){
                if (Globalvar.IODPLACEHOLDERS[this.inputAttribut.code][this.inputAttribut.mode].action === 'replace' && this.el.nativeElement.tagName === 'INPUT'){
                    this.renderer.setAttribute(this.el.nativeElement, 'placeholder', Globalvar.IODPLACEHOLDERS[this.inputAttribut.code][this.inputAttribut.mode].placeholder);
                    this.renderer.setAttribute(this.el.nativeElement, 'title', Globalvar.IODPLACEHOLDERS[this.inputAttribut.code][this.inputAttribut.mode].placeholder);
                }
                if (Globalvar.IODPLACEHOLDERS[this.inputAttribut.code][this.inputAttribut.mode].action === 'disable'){
                    this.disableElement();
                }
            }else{
                if (this.inputAttribut.iod && !_.hasIn(this.inputAttribut.iod, this.inputAttribut.code)){
                    this.disableElement();
                }
                this.renderer.setAttribute(this.el.nativeElement, 'placeholder', this.inputAttribut.name);
            }
        }

    }
    disableElement(){
        if (this.el.nativeElement.tagName === 'INPUT'){
            this.renderer.setProperty(this.el.nativeElement, 'disabled', true);
        }
        if (this.el.nativeElement.tagName === 'DIV'){
            this.el.nativeElement.style.display = 'none';
        }
    }
}
