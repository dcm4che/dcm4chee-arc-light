import {Globalvar} from "../constants/globalvar";
import {LanguageObject} from "../interfaces";
import {j4care} from "../helpers/j4care.service";

export class LanguageSwitcher {
    private _currentSelectedLanguage:LanguageObject;
    private _languageList:LanguageObject[];
    private _open:boolean = false;
    constructor(){
        try {
                // const dcmLanguages = j4care.extractLanguageDateFromString(localStorage.getItem('dcmLanguages'));//TODO Its an array not a string so you can not use j4care.extractLanguageDateFromString directly
                const dcmDefaultLanguage = j4care.extractLanguageDateFromString(localStorage.getItem('dcmDefaultLanguage'));
                const language_code = localStorage.getItem('language_code');
                // if(dcmLanguages && dcmDefaultLanguage){
                    //TODO
                    let defaultLanguage = language_code || dcmDefaultLanguage.code;
            /*        if(defaultLanguageList && defaultLanguageList.length > 0){
                        this.languageList = defaultLanguageList.map(code=>{
                            return Globalvar.LANGUAGES.getLanguageObjectFromCode(code);
                        })
                    }
                    if(this.languageList.length > 1 && selectedLanguageShortCode){
                        let toRemoveIndex;
                        this.languageList.forEach((lang:LanguageObject,i)=>{
                            if(lang.code === selectedLanguageShortCode){
                                this.currentSelectedLanguage = lang;
                                toRemoveIndex = i;
                            }
                        });
                        this.languageList.splice(toRemoveIndex, 1)
                    }*/
                // }
        }catch (e) {
        }
    }

    get currentSelectedLanguage(): LanguageObject {
        return this._currentSelectedLanguage;
    }

    set currentSelectedLanguage(value: LanguageObject) {
        this._currentSelectedLanguage = value;
    }

    get languageList(): LanguageObject[] {
        return this._languageList;
    }

    set languageList(value: LanguageObject[]) {
        this._languageList = value;
    }

    get open(): boolean {
        return this._open;
    }

    set open(value: boolean) {
        this._open = value;
    }
}