import {Directive, ElementRef, OnDestroy} from '@angular/core';
import {Input, HostListener} from '@angular/core';
import * as _ from 'lodash';
import {AppService} from "../../app.service";

@Directive({
    selector: '[tooltip]'
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
        return { top: rect.top + scrollTop, left: rect.left + scrollLeft }
    }
    @HostListener('mouseleave') onMouseLeave() {
        if (this.placeholderSet){
            this.hideTooltip();
        }
    }
    @HostListener('onmouseup') onMouseUp() {
        window.prompt('Copy to clipboard: Ctrl+C, Enter', this.tooltip);
    }
    createPlaceholder(){
        if(this.tooltip && this.tooltip.length > 1){
            this.div =  document.createElement('div');
            this.i = document.createElement('i');
            this.i.className = "glyphicon glyphicon-duplicate";
            this.spanText = document.createElement('span');
            // this.i.setAttribute("title","Copy text to clipboard");
            this.i.title = "Copy text to clipboard";
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
            if (_.includes(this.tooltip, '<br>')){
                let textArray = this.tooltip.split('<br>');
                _.forEach(textArray, (m, i) => {
                    div2.appendChild(br);
                    this.spanText.appendChild(document.createTextNode(m));
                    div2.appendChild(this.spanText);
                    // div2.appendChild(document.createTextNode(m));
                });
            }else{
                text = document.createTextNode(this.tooltip);
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
            el.value = this.tooltip;
            document.body.appendChild(el);
            el.select();
            document.execCommand('copy');
            document.body.removeChild(el);
            this.mainservice.showMsg('Text copied successfully in the clipboard');
        }
    }
    setPositionOfDiv(){
        let position = this.offset(this.el.nativeElement);
        this.div.style.position = "absolute";
        this.div.style.left = (position.left + 15*1)+'px';
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
