import './polyfills.ts';

import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';
import { enableProdMode } from '@angular/core';
import { environment } from './environments/environment';

import { loadTranslations } from '@angular/localize';
// import { getTranslations, ParsedTranslationBundle } from '@locl/core';
import {LocalLanguageObject} from "./app/interfaces";


const currentSavedLanguage = <LocalLanguageObject> JSON.parse(localStorage.getItem('current_language'));


if (environment.production) {
  enableProdMode();
}

/*if(currentSavedLanguage && currentSavedLanguage.language && currentSavedLanguage.language.code && currentSavedLanguage.language.code != "en"){
    getTranslations(`./assets/locale/${currentSavedLanguage.language.code}.json`).then(
        (data: ParsedTranslationBundle) => {
            loadTranslations(data.translations as any);
            import('./app/app.module').then(module => {
                platformBrowserDynamic()
                    .bootstrapModule(module.AppModule)
                    .catch(err => console.error(err));
            }).catch(err=>console.error("first",err));
        }
    );
} else {*/
    import('./app/app.module').then(module => {
        platformBrowserDynamic().bootstrapModule(module.AppModule);
    }).catch(err=>console.error(err));
// }

