import {Globalvar} from "../constants/globalvar";
import {LanguageObject} from "../interfaces";

export class LanguageSwitcher {
    private _currentSelectedLanguage:LanguageObject;
    private _languageList:LanguageObject[];
    private _open:boolean = false;
    constructor(defaultLanguageList:string[], selectedLanguageShortCode:string){
        selectedLanguageShortCode = selectedLanguageShortCode || 'en';
        if(defaultLanguageList && defaultLanguageList.length > 0){
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