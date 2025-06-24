import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import * as _ from 'lodash-es';
import {j4care} from '../j4care.service';
import {TableSchemaElement} from '../../models/dicom-table-schema-element';
import {PermissionService} from '../permissions/permission.service';
import {TableAction} from '../dicom-studies-table/dicom-studies-table.interfaces';
import {DatePipe, NgClass, NgStyle} from '@angular/common';
import {AppModule} from '../../app.module';
import {TooltipDirective} from '../tooltip/tooltip.directive';
import {AttributeListComponent} from '../attribute-list/attribute-list.component';
import {StackedProgressComponent} from '../stacked-progress/stacked-progress.component';

@Component({
    selector: 'table-generator',
    templateUrl: './table-generator.component.html',
    styleUrls: ['./table-generator.component.scss'],
    imports: [
        NgStyle,
        NgClass,
        TooltipDirective,
        AttributeListComponent,
        DatePipe,
        StackedProgressComponent
    ],
    standalone: true
})
export class TableGeneratorComponent implements OnInit {

    private _config;
    private _models;
    @Input() stringifyDetailAttributes;
    @Output() tableMouseEnter = new EventEmitter();
    @Output() tableMouseLeave = new EventEmitter();
    _ = _;
    Object = Object;
    constructor(
        public permissionService: PermissionService
    ) {
    }
    ngOnInit() {
        if (!this._config || !_.hasIn(this._config, 'search')) {
            this._config = this._config || {};
            this._config.search = '';
        }
        if (!_.hasIn(this._config, 'calculate') || this._config.calculate) {
            this._config.table = j4care.calculateWidthOfTable(this._config.table);
        }
        this._config.table = this.checkSchemaPermission(this._config.table);
    }
    tMousEnter() {
        this.tableMouseEnter.emit();
    }
    tMousLeave() {
        this.tableMouseLeave.emit();
    }
    onProgressClicked(table_element, model) {
        if (table_element.onClick) {
            table_element.onClick(model)
        }
    }
    selectOnClick(str) {
        const el = document.createElement('textarea');
        el.value = str;
        document.body.appendChild(el);
        el.select();
        document.execCommand('copy');
        document.body.removeChild(el);
    }

    get models() {
        return this._models;
    }
    @Input()
    set models(value) {
        this._models = value;
        if (this.stringifyDetailAttributes) {
            this._models.map(model => {
                model.tableGeneratorDetailAttributes = Object.assign({}, model);
                j4care.stringifyArrayOrObject(model.tableGeneratorDetailAttributes, []);
                return model;
            });
        }
    }

    checkSchemaPermission(schema: TableSchemaElement[]): TableSchemaElement[] {
        // Object.keys(schema).forEach(levelKey => {
            schema.forEach((element: TableSchemaElement) => {
                if (element && element.type) {
                    if (element.type === 'actions' || element.type === 'actions-menu' || element.type === 'buttons') {
                        let key = 'actions';
                        if (_.hasIn(element, 'menu') && element.menu) {
                            key = 'menu.actions';
                        }
                        if (_.get(element, key) && (<any[]>_.get(element, key)).length > 0) {
                            let result = (<any[]>_.get(element, key)).filter((menu: TableAction) => {
                                if (menu.permission) {
                                    return this.permissionService.checkVisibility(menu.permission);
                                }
                                return true
                            });
                            _.set(element, key, result);
                        }
                        if (_.hasIn(element, 'headerActions')) {
                            key = 'headerActions';
                            let result = (<any[]>_.get(element, key)).filter((menu: TableAction) => {
                                if (menu.permission) {
                                    return this.permissionService.checkVisibility(menu.permission);
                                }
                                return true
                            });
                            _.set(element, key, result);
                        }
                    }
                } else {
                    return false;
                }
            })
        // });
        console.log('schema', schema);
        return schema;
    }


    get config() {
        return this._config;
    }

    @Input()
    set config(value) {
        console.log('in set config', value);
        if (_.hasIn(value, 'table')) {
            value.table.forEach(t => {
                console.log('t', t);
                if (t.modifyData && (!t.hook || t.hook === '')) {
                    t['hook'] = t.modifyData;
                }
                if (t.header && (!t.title || t.title === '')) {
                    t['title'] = t.header;
                }
                if (t.type && t.type === 'model') {
                    t.type = 'value';
                }
                if (t.key && (!t.pathToValue || t.pathToValue === '')) {
                    t.pathToValue = t.key;
                }
            })
        }
        console.log('in set config', value);
        this._config = value;
    }
}
