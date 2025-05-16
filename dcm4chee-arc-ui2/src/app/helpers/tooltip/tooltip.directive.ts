import {Directive, ElementRef, OnDestroy} from '@angular/core';
import {Input, HostListener} from '@angular/core';
import * as _ from 'lodash-es';
import {AppService} from "../../app.service";

@Directive({
    selector: '[tooltip]',
    standalone: false
})
export class TooltipDirective implements OnDestroy{
    @Input() tooltip: string;
    placeholderSet = false;
    div;
    i;
    spanText;

    constructor(
        private el: ElementRef,
        private mainservice:AppService
        ) {
        if (this.tooltip){
            this.createPlaceholder();
        }
    }
    @HostListener('mouseenter') onMouseEnter() {
        if (!this.placeholderSet){
            this.createPlaceholder();
            this.showTooltip();
        }else{
            this.showTooltip();
        }
    }
    offset(el) {
        var rect = el.getBoundingClientRect(),
            scrollLeft = window.pageXOffset || document.documentElement.scrollLeft,
            scrollTop = window.pageYOffset || document.documentElement.scrollTop;
        return { top: rect.top + scrollTop, left: rect.left + scrollLeft , right: rect.right + scrollLeft}
    }
    @HostListener('mouseleave') onMouseLeave() {
        if (this.placeholderSet){
            this.hideTooltip();
        }
    }
    @HostListener('onmouseup') onMouseUp() {
        window.prompt('Copy to clipboard: Ctrl+C, Enter', this.tooltip);
    }
    textTransformed:string = "";
    textOriginal:string = "";
    createPlaceholder(){
        if(this.tooltip && ((this.tooltip.length && this.tooltip.length > 1) || (_.hasIn(this.tooltip,"original")))){

            if(this.tooltip.indexOf("original") > -1){
                if(typeof this.tooltip === "string"){
                    this.tooltip = JSON.parse(this.tooltip);
                }
                this.textOriginal = this.tooltip["original"];
                this.textTransformed = this.tooltip["transformed"] || "";
            }else{
                this.textTransformed = this.textOriginal = this.tooltip;
            }
            this.div =  document.createElement('div');
            this.i = document.createElement('i');
            this.i.className = "glyphicon glyphicon-duplicate";
            this.spanText = document.createElement('span');
            // this.i.setAttribute("title",$localize `:@@tooltip.copy_text_to_clipboard:Copy text to clipboard`);
            this.i.title = $localize `:@@tooltip.copy_text_to_clipboard:Copy text to clipboard`;
            this.div.className = 'tooltip_container';
            let div2 =  document.createElement('div');
            div2.className = 'dir-tooltip animated';
            let br = document.createElement('br');

            this.i.addEventListener('mouseup', () => {
                copyToClipboard();
            });
            this.i.addEventListener('dblclick', () => {
                copyToClipboard();
            });

            let text;
            if (_.includes(this.textTransformed, '<br>')){
                let textArray = this.textTransformed.split('<br>');
                _.forEach(textArray, (m, i) => {
                    div2.appendChild(br);
                    this.spanText.appendChild(document.createTextNode(m));
                    div2.appendChild(this.spanText);
                    // div2.appendChild(document.createTextNode(m));
                });
            }else{
                text = document.createTextNode(this.textTransformed);
                this.spanText.appendChild(text);
                div2.appendChild(this.spanText);
            }
            div2.appendChild(this.i);
            this.div.appendChild(div2);
            this.setPositionOfDiv();
;
            this.div.addEventListener('mouseleave',(e)=>{
                this.hideTooltip();
            });
            document.querySelector('body').appendChild(this.div);
            this.placeholderSet = true;
        }
        let copyToClipboard = ()=>{
            const el = document.createElement('textarea');
            el.value = this.textOriginal;
            document.body.appendChild(el);
            el.select();
            document.execCommand('copy');
            document.body.removeChild(el);
            this.mainservice.showMsg($localize `:@@tooltip.text_copied_successfully_in_the_clipboard:Text '${this.textOriginal}' copied successfully in the clipboard`);
        }
    }
    setPositionOfDiv(){
        let position = this.offset(this.el.nativeElement);
        this.div.style.position = "absolute";
        if(this.el.nativeElement.className && this.el.nativeElement.className.indexOf("big_field") > -1){
            this.div.style.left = (position.right - 15*1)+'px';
        }else{
            this.div.style.left = (position.left + 15*1)+'px';
        }
        this.div.style.top = (position.top+25*1) +'px';
        this.div.addEventListener('mouseenter',(e)=>{
            this.showTooltip();
        })
    }
    showTooltip(){
        if(this.placeholderSet){
            this.div.classList.add('openflag');
            this.div.classList.remove('closeflag');
            setTimeout(() => {
                if (this.div.classList.contains('openflag')){
                    this.setPositionOfDiv();
                    this.div.classList.add('show');
                    this.div.classList.remove('closeflag');
                    this.div.classList.remove('openflag');
                }
            }, 800);
        }
    };
    hideTooltip(){
        if(this.placeholderSet){
            this.div.classList.add('closeflag');
            this.div.classList.remove('openflag');
            setTimeout(() => {
                if (this.div && this.div.classList.contains('closeflag')){
                    this.div.classList.remove('closeflag');
                    this.div.classList.remove('show');
                }
            }, 1000);
        }
    }

    ngOnDestroy(): void {
        let nodes = document.querySelectorAll(".tooltip_container");
        for (let i = 0; i < nodes.length; i++) {
            nodes[i].remove();
        }
    }

}
