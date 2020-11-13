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
    @Index(columnList = "phonetic_name")
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
        createOrUpdateSoundexCodes(
                pn.get(Group.Alphabetic, Component.FamilyName),
                pn.get(Group.Alphabetic, Component.GivenName),
                pn.get(Group.Alphabetic, Component.MiddleName),
                fuzzyStr);
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

    public String getAlphabeticName() {
        return alphabeticName;
    }

    public void setAlphabeticName(String alphabeticName) {
        this.alphabeticName = alphabeticName;
    }

    public String getIdeographicName() {
        return ideographicName;
    }

    public void setIdeographicName(String ideographicName) {
        this.ideographicName = ideographicName;
    }

    public String getPhoneticName() {
        return phoneticName;
    }

    public void setPhoneticName(String phoneticName) {
        this.phoneticName = phoneticName;
    }
}
