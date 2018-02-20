import { Component } from '@angular/core';
import {MatDialogRef} from '@angular/material';
import * as _ from 'lodash';
import {StudiesService} from '../../../studies/studies.service';

@Component({
    selector: 'app-copy-move-objects',
    templateUrl: './copy-move-objects.component.html',
    styles: [`

    `]
})
export class CopyMoveObjectsComponent {
    private _clipboard;
    private _rjnotes;
    private _target;
    private _selected;
    private _saveLabel;
    private _title;
    private _reject;
    _ = _;
    constructor(public dialogRef: MatDialogRef<CopyMoveObjectsComponent>, public service: StudiesService) {
        console.log('in construct copymovecomponent');
    }

    get title() {
        return this._title;
    }

    set title(value) {
        this._title = value;
    }

    get saveLabel() {
        return this._saveLabel;
    }

    set saveLabel(value) {
        this._saveLabel = value;
    }

    get selected() {
        return this._selected;
    }

    set selected(value) {
        this._selected = value;
    }

    get target() {
        return this._target;
    }

    set target(value) {
        this._target = value;
    }
    get clipboard() {
        return this._clipboard;
    }
    set clipboard(value) {
        this._clipboard = value;
    }
    get rjnotes() {
        return this._rjnotes;
    }

    set rjnotes(value) {
        this._rjnotes = value;
    }
    getKeys(obj){
        if (_.isArray(obj)){
            return obj;
        }else{
            return Object.keys(obj);
        }
    }
    removeClipboardElement(modus, keys){
        this.service.removeClipboardElement(modus, keys, this.clipboard);
    }

    get reject() {
        return this._reject;
    }

    set reject(value) {
        this._reject = value;
    }
}
