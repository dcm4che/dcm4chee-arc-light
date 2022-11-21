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
 * Portions created by the Initial Developer are Copyright (C) 2013
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
import org.dcm4che3.data.Code;
import org.dcm4che3.util.StringUtils;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */

@NamedQuery(
    name=CodeEntity.FIND_BY_CODE_VALUE_WITH_SCHEME_VERSION,
    query="select entity from CodeEntity entity " +
            "where entity.codeValue = ?1 " +
            "and entity.codingSchemeDesignator = ?2 " +
            "and entity.codingSchemeVersion = ?3")
@Entity
@Table(name = "code", uniqueConstraints =
    @UniqueConstraint(columnNames = { "code_value", "code_designator", "code_version" }))
public class CodeEntity {

    public static final String FIND_BY_CODE_VALUE_WITH_SCHEME_VERSION =
            "CodeEntity.findByCodeValueWithSchemeVersion";

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Basic(optional = false)
    @Column(name = "code_value")
    private String codeValue;

    @Basic(optional = false)
    @Column(name = "code_designator")
    private String codingSchemeDesignator;

    @Basic(optional = false)
    @Column(name = "code_version")
    private String codingSchemeVersion;

    @Basic(optional = false)
    @Column(name = "code_meaning")
    private String codeMeaning;

    protected CodeEntity() {} // for JPA

    public CodeEntity(Code code) {
        codeValue = code.getCodeValue();
        codingSchemeDesignator = code.getCodingSchemeDesignator();
        codingSchemeVersion = StringUtils.maskNull(code.getCodingSchemeVersion(), "*");
        codeMeaning = code.getCodeMeaning();
    }

    public long getPk() {
        return pk;
    }

    public String getCodeValue() {
        return codeValue;
    }

    public String getCodingSchemeDesignator() {
        return codingSchemeDesignator;
    }

    public String getCodingSchemeVersion() {
        return StringUtils.nullify(codingSchemeVersion, "*");
    }

    public String getCodeMeaning() {
        return codeMeaning;
    }

    public Code getCode() {
        return new Code(codeValue, codingSchemeDesignator, StringUtils.nullify(codingSchemeVersion, "*"), codeMeaning);
    }

    @Override
    public String toString() {
        return getCode().toString();
    }
}
