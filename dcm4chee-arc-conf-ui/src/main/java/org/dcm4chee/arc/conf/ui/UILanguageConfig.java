package org.dcm4chee.arc.conf.ui;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class UILanguageConfig {
    private String name;
    private final Map<String,UILanguageProfile> languageProfile = new HashMap<>();
    private String[] languages = {};

    public UILanguageConfig(){}
    public UILanguageConfig(String name){
        this.setName(name);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, UILanguageProfile> getLanguageProfile() {
        return languageProfile;
    }

    public Collection<UILanguageProfile> getLanguageProfiles(){
        return this.languageProfile.values();
    }

    public String[] getLanguages() {
        return languages;
    }

    public void setLanguages(String[] languages) {
        this.languages = languages;
    }

    public void addLanguageProfile(UILanguageProfile languageProfile){
        this.languageProfile.put(languageProfile.getProfileName(),languageProfile);
    }
    public UILanguageProfile getLanguageProfile(String name){
        return this.languageProfile.get(name);
    }
}
