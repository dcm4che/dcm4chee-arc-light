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

import jakarta.persistence.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
@Entity
@Table(name = "content_item", indexes = {
        @Index(columnList = "rel_type"),
        @Index(columnList = "text_value")
})
public class ContentItem {

    public static final int MAX_TEXT_LENGTH = 64;

    protected ContentItem() {}

    public ContentItem(String relationshipType, CodeEntity conceptName,
                       String textValue) {
        this.relationshipType = relationshipType;
        this.conceptName = conceptName;
        this.textValue = textValue;
    }

    public ContentItem(String relationshipType, CodeEntity conceptName,
                       CodeEntity conceptCode) {
        this.relationshipType = relationshipType;
        this.conceptName = conceptName;
        this.conceptCode = conceptCode;
    }

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Basic(optional = false)
    @Column(name = "rel_type")
    private String relationshipType;
    
    @Column(name = "text_value")
    private String textValue;
    
    @ManyToOne(optional = false)
    @JoinColumn(name = "name_fk")
    private CodeEntity conceptName;
    
    @ManyToOne
    @JoinColumn(name = "code_fk")
    private CodeEntity conceptCode;

    public long getPk() {
        return pk;
    }

    public String getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(String relationshipType) {
        this.relationshipType = relationshipType;
    }

    public String getTextValue() {
        return textValue;
    }

    public void setTextValue(String textValue) {
        this.textValue = textValue;
    }

    public CodeEntity getConceptName() {
        return conceptName;
    }

    public void setConceptName(CodeEntity conceptName) {
        this.conceptName = conceptName;
    }

    public CodeEntity getConceptCode() {
        return conceptCode;
    }

    public void setConceptCode(CodeEntity conceptCode) {
        this.conceptCode = conceptCode;
    }

}
