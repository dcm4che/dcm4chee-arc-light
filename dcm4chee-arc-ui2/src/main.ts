import './polyfills.ts';

import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';
import { enableProdMode } from '@angular/core';
import { environment } from './environments/environment';
import { AppModule } from './app/';

import { loadTranslations } from '@angular/localize';
import { getTranslations, ParsedTranslationBundle } from '@locl/core';


let languageCode = localStorage.getItem('language_code');

if (environment.production) {
  enableProdMode();
}

if(languageCode && languageCode != "en"){
    getTranslations(`./assets/locale/${languageCode}.json`).then(
        (data: ParsedTranslationBundle) => {
            loadTranslations(data.translations as any);
            import('./app/app.module').then(module => {
                platformBrowserDynamic()
                    .bootstrapModule(module.AppModule)
                    .catch(err => console.error(err));
            }).catch(err=>console.error("first",err));
        }
    );
}else{
    platformBrowserDynamic().bootstrapModule(AppModule);
}

