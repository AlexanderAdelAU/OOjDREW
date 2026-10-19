// OO jDREW - An Object Oriented extension of the Java Deductive Reasoning Engine for the Web
// Copyright (C) 2026 OO jDREW contributors
// Licensed under the GNU Lesser General Public License, version 2.1 or later.
package org.ruleml.oojdrew.tests;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import junit.framework.TestCase;
import org.ruleml.oojdrew.api.OOjdrewAPI;

/** Functional and concurrent usage checks for the headless facade. */
public class OOjdrewAPITest extends TestCase {
    private static final int FACTS = 20;
    private static final String QUERY =
            "<Query><Atom><Rel>activeService</Rel>" +
            "<Var>s</Var><Var>o</Var><Var>c</Var><Var>p</Var>" +
            "</Atom></Query>";

    public void testQueryReturnsIndependentResults() {
        OOjdrewAPI api = new OOjdrewAPI();
        api.loadRuleML(ruleBase("First"));
        List<Map<String, String>> first = api.queryRuleML(QUERY);
        assertEquals(FACTS, first.size());
        assertFalse(first.get(0).isEmpty());

        api.loadRuleML(ruleBase("Second"));
        assertEquals(FACTS, api.queryRuleML(QUERY).size());
        assertEquals(FACTS, first.size()); // detached from reloading
        try {
            first.clear();
            fail("Query results should be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    public void testRepeatedLoadsDoNotAccumulateResults() {
        OOjdrewAPI api = new OOjdrewAPI();
        for (int i = 0; i < 15; i++) {
            api.loadRuleML(ruleBase("Repeated"));
            assertEquals("load " + i, FACTS, api.queryRuleML(QUERY).size());
        }
    }

    public void testTwoInstancesCanLoadAndQueryConcurrently() throws Exception {
        final OOjdrewAPI queried = new OOjdrewAPI();
        final OOjdrewAPI reloaded = new OOjdrewAPI();
        queried.loadRuleML(ruleBase("Queried"));
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread writer = new Thread(new Runnable() {
            public void run() {
                try {
                    for (int i = 0; i < 35; i++) {
                        reloaded.loadRuleML(ruleBase("Reloaded"));
                        assertEquals(FACTS, reloaded.queryRuleML(QUERY).size());
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "oojdrew-reloader");
        writer.start();
        try {
            for (int i = 0; i < 35; i++) {
                assertEquals("query " + i, FACTS,
                        queried.queryRuleML(QUERY).size());
            }
        } finally {
            writer.join();
        }
        if (failure.get() != null) {
            AssertionError error = new AssertionError("Background API use failed");
            error.initCause(failure.get());
            throw error;
        }
    }

    private static String ruleBase(String prefix) {
        StringBuilder kb = new StringBuilder(
                "<Assert><Rulebase mapClosure=\"universal\">");
        for (int i = 0; i < FACTS; i++) {
            kb.append("<Atom><Rel>activeService</Rel><Ind>")
              .append(prefix).append("_Place").append(i)
              .append("</Ind><Ind>").append(prefix)
              .append("_Operation</Ind><Ind>ip0</Ind><Ind>")
              .append(5000 + i).append("</Ind></Atom>");
        }
        return kb.append("</Rulebase></Assert>").toString();
    }
}
