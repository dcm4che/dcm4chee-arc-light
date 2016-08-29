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
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
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

package org.dcm4chee.arc.validation.constraints;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
class ValidValueOfValidator implements ConstraintValidator<ValidValueOf, CharSequence> {
    private final static Logger log = LoggerFactory.getLogger(ValidValueOfValidator.class);
    private Constructor<?> init;
    private Method valueOf;

    @Override
    public void initialize(ValidValueOf constraint) {
        Class<?> type = constraint.type();
        String methodName = constraint.methodName();
        Class<?> paramType = constraint.methodParameterType();
        if (methodName.isEmpty()) {
            try {
                init = type.getConstructor(paramType);
                return;
            } catch (NoSuchMethodException e) {
                methodName = "valueOf";
            }
        }
        try {
            valueOf = type.getMethod(methodName, paramType);
        } catch (NoSuchMethodException e) {
            log.warn("Failed to initialize validator: ", e);
        }
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null)
            return true;
        try {
            if (init != null)
                init.newInstance(value);
            else if (valueOf != null)
                valueOf.invoke(null, value);
            return true;
        } catch (InvocationTargetException e) {
            return false;
        } catch (Exception e) {
            log.warn("Unexpected Exception: ", e);
            return true;
        }
    }
}
