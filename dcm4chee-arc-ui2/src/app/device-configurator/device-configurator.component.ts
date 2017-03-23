import { Component, OnInit } from '@angular/core';
import {ActivatedRoute, Router, Params} from "@angular/router";
import {DropdownList} from "../helpers/form/dropdown-list";
import {InputText} from "../helpers/form/input-text";
import {FormElement} from "../helpers/form/form-element";
import {ArrayElement} from "../helpers/form/array-element";
import {ArrayObject} from "../helpers/form/array-object";
import {RadioButtons} from "../helpers/form/radio-buttons";
import {Checkbox} from "../helpers/form/checkboxes";

@Component({
  selector: 'app-device-configurator',
  templateUrl: './device-configurator.component.html',
  styleUrls: ['./device-configurator.component.css']
})
export class DeviceConfiguratorComponent implements OnInit {
    formObj:FormElement<any>[];
    constructor(private route: ActivatedRoute, private router: Router) { }

    ngOnInit() {
        this.route.params
            // .switchMap((params: Params) => this.service.getHero(+params['id']))
            .subscribe((res) => {
                console.log("res",res);
            });
        this.formObj = [
            new DropdownList({
                key: 'brave',
                label: 'Bravery Rating',
                options: [
                    {key: 'solid',  value: 'Solid'},
                    {key: 'great',  value: 'Great'},
                    {key: 'good',   value: 'Good'},
                    {key: 'unproven', value: 'Unproven'}
                ],
                order: 3
            }),
            new InputText({
                key: 'firstName',
                label: 'First name',
                description:'Testdescriptionfirstname',
                required: true,
                order: 1
            }),
            new InputText({
                key: 'emailAddress',
                label: 'Email',
                type: 'email',
                order: 2
            }),
            new Checkbox({
                key: 'testcheckbox2',
                label: 'Testcheckbox2',
                options: [
                    {key: '1Test1',  value: '1test1', active:false},
                    {key: '1Test2',  value: '1test2', active:true},
                    {key: '1Test3',  value: '1test3', active:true},
                ]
            }),
            new ArrayElement({
                key: 'arrsingleelement',
                label: 'TestArray',
                type: 'text',
                value: []
            }),
            new RadioButtons({
                key: 'testradio',
                label: 'Testradi label',
                value:'installed',
                options: [
                    {key: 'True',  value: true},
                    {key: 'False',  value: false},
                ]
            }),
            new Checkbox({
                key: 'testcheckbox',
                label: 'Testcheckbox',
                options: [
                    {key: 'Test1',  value: 'test1', active:true},
                    {key: 'Test2',  value: 'test2', active:false},
                    {key: 'Test3',  value: 'test3', active:true},
                ]
            }),
            new ArrayElement({
                key: 'arrsingleelement2',
                label: 'TestArray2',
                type: 'number',
                value: []
            }),
            new ArrayObject({
                key: 'arraytest',
                label: 'Array test',
                order:4,
                options: [{
                    element:[
                        new InputText({
                            key:'testkey',
                            label:'Test label',
                            description:"TestDescription",
                            type:'text'
                        }),
                        new InputText({
                            key:'testke2y',
                            label:'Test label2',
                            type:'text',
                            value:"testval"
                        })
                        ,
                        new ArrayElement({
                            key: 'arrsingleelement3',
                            label: 'TestArray2',
                            type: 'number',
                            value: [1]
                        })
                        ,
                        new DropdownList({
                            key: 'brave2',
                            label: 'Bravery Rating2',
                            value:'great',
                            options: [
                                {key: 'solid',  value: 'Solid'},
                                {key: 'great',  value: 'Great'},
                                {key: 'good',   value: 'Good'},
                                {key: 'unproven', value: 'Unproven'}
                            ]
                        }),
                        new ArrayObject({
                            key: 'arraytestsub',
                            label: 'Array test',
                            order:4,
                            options: [{
                                element:[
                                    new InputText({
                                        key:'testkeysub',
                                        label:'Test labelsub',
                                        type:'text'
                                    }),
                                    new InputText({
                                        key:'testke2ysub',
                                        label:'Test label2sub',
                                        type:'text',
                                        value:"testval"
                                    })
                                    ,
                                    new DropdownList({
                                        key: 'brave2',
                                        label: 'Bravery Rating2',
                                        value:'great',
                                        options: [
                                            {key: 'solid',  value: 'Solid'},
                                            {key: 'great',  value: 'Great'},
                                            {key: 'good',   value: 'Good'},
                                            {key: 'unproven', value: 'Unproven'}
                                        ]
                                    })
                                ]
                            }]
                        })
                    ]
                }
                    // ,{
                    //     element:[
                    //         new InputText({
                    //             key:'testkey',
                    //             label:'Test label',
                    //             type:'text'
                    //         }),
                    //         new InputText({
                    //             key:'testke2y',
                    //             label:'Test label2',
                    //             type:'text'
                    //         })
                    //         ,
                    //         new DropdownList({
                    //             key: 'brave2',
                    //             label: 'Bravery Rating2',
                    //             options: [
                    //                 {key: 'solid',  value: 'Solid'},
                    //                 {key: 'great',  value: 'Great'},
                    //                 {key: 'good',   value: 'Good'},
                    //                 {key: 'unproven', value: 'Unproven'}
                    //             ]
                    //         })
                    //     ]
                    // }
                ]
            })
        ]

}
}
