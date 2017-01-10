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

package org.dcm4chee.arc.query.impl;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.hibernate.HibernateQuery;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.hibernate.StatelessSession;

import java.util.Arrays;
import java.util.Iterator;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
abstract class AbstractQuery implements Query {

    protected final QueryContext context;
    protected final StatelessSession session;
    private HibernateQuery<Tuple> query;
    private Iterator<Tuple> results;
    private long offset;
    private long limit;
    private int rejected;
    private int matches;

    public AbstractQuery(QueryContext context, StatelessSession session) {
        this.context = context;
        this.session = session;
    }

    public void initQuery() {
        query = newHibernateQuery();
    }

    protected abstract HibernateQuery<Tuple> newHibernateQuery();

    protected abstract Attributes toAttributes(Tuple results);

    private void checkQuery() {
        if (query == null)
            throw new IllegalStateException("query not initalized");
    }

    @Override
    public void executeQuery() {
        checkQuery();
        rejected = 0;
        matches = 0;
        results = offset > 0 ? query.fetch().iterator() : query.iterate();
    }

    @Override
    public long count() {
        checkQuery();
        return query.fetchCount();
    }

    @Override
    public void limit(long limit) {
        checkQuery();
        query.limit(limit);
        this.limit = limit;
    }

    @Override
    public void offset(long offset) {
        checkQuery();
        query.offset(offset);
        this.offset = offset;
    }

    @Override
    public void orderBy(OrderSpecifier<?>... orderSpecifiers) {
        checkQuery();
        query.orderBy(orderSpecifiers);
    }

    @Override
    public boolean hasMoreMatches() throws DicomServiceException {
        boolean hasNext = results.hasNext();
        if (hasNext || rejected == 0 || limit != matches)
            return hasNext;

        offset(offset + matches);
        limit(rejected);
        executeQuery();
        return results.hasNext();
    }

    @Override
    public Attributes nextMatch() {
        Attributes attrs = toAttributes(results.next());
        matches++;
        if (attrs == null)
            rejected++;
        return attrs;
    }

    @Override
    public Attributes adjust(Attributes match) {
        if (match == null)
            return null;

        Attributes returnKeys = context.getReturnKeys();
        if (returnKeys == null)
            return match;

        Attributes filtered = new Attributes(returnKeys.size());
        filtered.addSelected(match, returnKeys);
        return filtered;
    }

    @Override
    public void close() {
        session.close();
        context.close();
    }

    static String[] splitAndAppend(String s, String append) {
        String[] ss = StringUtils.split(s, '\\');
        if (append != null) {
            String[] src = ss;
            ss = new String[src.length+1];
            System.arraycopy(src, 0, ss, 0, src.length);
            ss[src.length] = append;
        }
        return ss;
    }
}
