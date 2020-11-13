/*
 * *** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2015
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.entity;

import org.dcm4che3.soundex.FuzzyStr;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import static org.dcm4che3.data.PersonName.Component;
import static org.dcm4che3.data.PersonName.Group;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
@Entity
@Table(name = "person_name", indexes = {
    @Index(columnList = "alphabetic_name"),
    @Index(columnList = "ideographic_name"),
    @Index(columnList = "phonetic_name"),
    @Index(columnList = "family_name"),
    @Index(columnList = "given_name"),
    @Index(columnList = "middle_name"),
    @Index(columnList = "i_family_name"),
    @Index(columnList = "i_given_name"),
    @Index(columnList = "i_middle_name"),
    @Index(columnList = "p_family_name"),
    @Index(columnList = "p_given_name"),
    @Index(columnList = "p_middle_name")
})
public class PersonName {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Basic(optional=false)
    @Column(name = "alphabetic_name")
    private String alphabeticName;

    @Basic(optional=false)
    @Column(name = "ideographic_name")
    private String ideographicName;

    @Basic(optional=false)
    @Column(name = "phonetic_name")
    private String phoneticName;

    @Column(name = "family_name")
    private String familyName;

    @Column(name = "given_name")
    private String givenName;

    @Column(name = "middle_name")
    private String middleName;

    @Column(name = "name_prefix")
    private String namePrefix;

    @Column(name = "name_suffix")
    private String nameSuffix;

    @Column(name = "i_family_name")
    private String ideographicFamilyName;

    @Column(name = "i_given_name")
    private String ideographicGivenName;

    @Column(name = "i_middle_name")
    private String ideographicMiddleName;

    @Column(name = "i_name_prefix")
    private String ideographicNamePrefix;

    @Column(name = "i_name_suffix")
    private String ideographicNameSuffix;

    @Column(name = "p_family_name")
    private String phoneticFamilyName;

    @Column(name = "p_given_name")
    private String phoneticGivenName;

    @Column(name = "p_middle_name")
    private String phoneticMiddleName;

    @Column(name = "p_name_prefix")
    private String phoneticNamePrefix;

    @Column(name = "p_name_suffix")
    private String phoneticNameSuffix;

    @OneToMany(mappedBy = "personName", cascade = CascadeType.ALL, orphanRemoval = true)
    private Collection<SoundexCode> soundexCodes;

    public PersonName() {
    }
    
    public PersonName(org.dcm4che3.data.PersonName pn, FuzzyStr fuzzyStr) {
        fromDicom(pn, fuzzyStr);
    }

    private void fromDicom(org.dcm4che3.data.PersonName pn, FuzzyStr fuzzyStr) {
        alphabeticName = pn.toString(Group.Alphabetic, false);
        ideographicName = pn.toString(Group.Ideographic, false);
        phoneticName = pn.toString(Group.Phonetic, false);
        familyName = pn.get(Group.Alphabetic, Component.FamilyName);
        givenName = pn.get(Group.Alphabetic, Component.GivenName);
        middleName = pn.get(Group.Alphabetic, Component.MiddleName);
        namePrefix = pn.get(Group.Alphabetic, Component.NamePrefix);
        nameSuffix = pn.get(Group.Alphabetic, Component.NameSuffix);
        ideographicFamilyName = pn.get(Group.Ideographic, Component.FamilyName);
        ideographicGivenName = pn.get(Group.Ideographic, Component.GivenName);
        ideographicMiddleName = pn.get(Group.Ideographic, Component.MiddleName);
        ideographicNamePrefix = pn.get(Group.Ideographic, Component.NamePrefix);
        ideographicNameSuffix = pn.get(Group.Ideographic, Component.NameSuffix);
        phoneticFamilyName = pn.get(Group.Phonetic, Component.FamilyName);
        phoneticGivenName = pn.get(Group.Phonetic, Component.GivenName);
        phoneticMiddleName = pn.get(Group.Phonetic, Component.MiddleName);
        phoneticNamePrefix = pn.get(Group.Phonetic, Component.NamePrefix);
        phoneticNameSuffix = pn.get(Group.Phonetic, Component.NameSuffix);
        createOrUpdateSoundexCodes(familyName, givenName, middleName, fuzzyStr);
    }

    private void createOrUpdateSoundexCodes(String familyName,
            String givenName, String middleName, FuzzyStr fuzzyStr) {
        
        if (soundexCodes == null)
            soundexCodes = new ArrayList<SoundexCode>();
        else
            for (Iterator<SoundexCode> iterator = soundexCodes.iterator(); 
                    iterator.hasNext();) {
                iterator.next();
                iterator.remove();
            }

        addSoundexCodesTo(Component.FamilyName, familyName, fuzzyStr, soundexCodes);
        addSoundexCodesTo(Component.GivenName, givenName, fuzzyStr, soundexCodes);
        addSoundexCodesTo(Component.MiddleName, middleName, fuzzyStr, soundexCodes);
    }

    private void addSoundexCodesTo(Component component, String name,
            FuzzyStr fuzzyStr, Collection<SoundexCode> codes) {
        if (name == null)
            return;

        Iterator<String> parts = SoundexCode.tokenizePersonNameComponent(name);
        for (int i = 0; parts.hasNext(); i++) {
            SoundexCode soundexCode = new SoundexCode(component, i,
                    fuzzyStr.toFuzzy(parts.next()));
            soundexCode.setPersonName(this);
            codes.add(soundexCode);
        }
    }

    public org.dcm4che3.data.PersonName toPersonName() {
        org.dcm4che3.data.PersonName pn = new org.dcm4che3.data.PersonName();
        pn.set(Group.Alphabetic, alphabeticName);
        pn.set(Group.Ideographic, ideographicName);
        pn.set(Group.Phonetic, phoneticName);
        return pn;
    }

    public static PersonName valueOf(String s, FuzzyStr fuzzyStr,
            PersonName prev) {
        if (s == null)
            return null;

        org.dcm4che3.data.PersonName pn = new org.dcm4che3.data.PersonName(s,
                true);
        if (pn.isEmpty())
            return null;

        if (prev != null) {
            if (!pn.equals(prev.toPersonName()))
                prev.fromDicom(pn, fuzzyStr); //update values
            return prev;
        } else
            return new PersonName(pn, fuzzyStr); //create new
    }
    
    @Override
    public String toString() {
        return toPersonName().toString();
    }

    public long getPk() {
        return pk;
    }

    public String getFamilyName() {
        return familyName;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getNamePrefix() {
        return namePrefix;
    }

    public void setNamePrefix(String namePrefix) {
        this.namePrefix = namePrefix;
    }

    public String getNameSuffix() {
        return nameSuffix;
    }

    public void setNameSuffix(String nameSuffix) {
        this.nameSuffix = nameSuffix;
    }

    public String getIdeographicFamilyName() {
        return ideographicFamilyName;
    }

    public void setIdeographicFamilyName(String ideographicFamilyName) {
        this.ideographicFamilyName = ideographicFamilyName;
    }

    public String getIdeographicGivenName() {
        return ideographicGivenName;
    }

    public void setIdeographicGivenName(String ideographicGivenName) {
        this.ideographicGivenName = ideographicGivenName;
    }

    public String getIdeographicMiddleName() {
        return ideographicMiddleName;
    }

    public void setIdeographicMiddleName(String ideographicMiddleName) {
        this.ideographicMiddleName = ideographicMiddleName;
    }

    public String getIdeographicNamePrefix() {
        return ideographicNamePrefix;
    }

    public void setIdeographicNamePrefix(String ideographicNamePrefix) {
        this.ideographicNamePrefix = ideographicNamePrefix;
    }

    public String getIdeographicNameSuffix() {
        return ideographicNameSuffix;
    }

    public void setIdeographicNameSuffix(String ideographicNameSuffix) {
        this.ideographicNameSuffix = ideographicNameSuffix;
    }

    public String getPhoneticFamilyName() {
        return phoneticFamilyName;
    }

    public void setPhoneticFamilyName(String phoneticFamilyName) {
        this.phoneticFamilyName = phoneticFamilyName;
    }

    public String getPhoneticGivenName() {
        return phoneticGivenName;
    }

    public void setPhoneticGivenName(String phoneticGivenName) {
        this.phoneticGivenName = phoneticGivenName;
    }

    public String getPhoneticMiddleName() {
        return phoneticMiddleName;
    }

    public void setPhoneticMiddleName(String phoneticMiddleName) {
        this.phoneticMiddleName = phoneticMiddleName;
    }

    public String getPhoneticNamePrefix() {
        return phoneticNamePrefix;
    }

    public void setPhoneticNamePrefix(String phoneticNamePrefix) {
        this.phoneticNamePrefix = phoneticNamePrefix;
    }

    public String getPhoneticNameSuffix() {
        return phoneticNameSuffix;
    }

    public void setPhoneticNameSuffix(String phoneticNameSuffix) {
        this.phoneticNameSuffix = phoneticNameSuffix;
    }
}
