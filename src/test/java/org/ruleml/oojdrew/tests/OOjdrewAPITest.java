// OO jDREW - Object Oriented Java Deductive Reasoning Engine for the Web
// Test coverage for the callable interface originally developed by AlexanderAdelAU.
// Licensed under the GNU Lesser General Public License, version 2.1 or later.
package org.ruleml.oojdrew.tests;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import junit.framework.TestCase;
import org.ruleml.oojdrew.api.OOjdrewAPI;
import org.ruleml.oojdrew.util.SymbolTable;

/** Check the original lazy contract and the optional eager convenience method. */
public class OOjdrewAPITest extends TestCase {
    private static final int FACTS = 20;
    private static final String QUERY =
            "<Query><Atom><Rel>activeService</Rel>" +
            "<Var>s</Var><Var>o</Var><Var>c</Var><Var>p</Var>" +
            "</Atom></Query>";

    public void testOriginalIssueQueryAndNextSolution() {
        OOjdrewAPI api = new OOjdrewAPI();
        api.parseKnowledgeBase(ruleBase("Legacy"), false);
        api.issueRuleMLQuery(QUERY);
        assertTrue(api.rowsReturned > 0);
        assertNotNull(api.rowData[0][0]);
        assertEquals(FACTS, countCurrentAndRemainingSolutions(api));
        assertFalse(api.hasNext);
    }

    public void testOriginalRepeatedRulebaseLoadsDoNotAccumulateResults() {
        OOjdrewAPI api = new OOjdrewAPI();
        for (int i = 0; i < 15; i++) {
            api.parseKnowledgeBase(ruleBase("LegacyRepeated"), false);
            assertEquals("reparse " + i, FACTS, countSolutions(api));
        }
    }

    public void testOptionalEagerQueryReturnsDetachedImmutableResults() {
        OOjdrewAPI api = new OOjdrewAPI();
        api.parseKnowledgeBase(ruleBase("First"), false);
        List<Map<String, String>> first = api.queryRuleML(QUERY);
        assertEquals(FACTS, first.size());
        assertFalse(first.get(0).isEmpty());

        api.parseKnowledgeBase(ruleBase("Second"), false);
        assertEquals(FACTS, api.queryRuleML(QUERY).size());
        assertEquals(FACTS, first.size());
        try {
            first.clear();
            fail("The list of results must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
        try {
            first.get(0).clear();
            fail("Each solution must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    public void testOriginalApiConcurrentQueriesAndRepeatedParsing() throws Exception {
        final OOjdrewAPI queried = new OOjdrewAPI();
        final OOjdrewAPI reloaded = new OOjdrewAPI();
        queried.parseKnowledgeBase(ruleBase("Queried"), false);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread writer = new Thread(new Runnable() {
            public void run() {
                try {
                    for (int i = 0; i < 35; i++) {
                        reloaded.parseKnowledgeBase(ruleBase("Reloaded"), false);
                        assertEquals(FACTS, countSolutions(reloaded));
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "oojdrew-legacy-reloader");
        writer.start();
        try {
            for (int i = 0; i < 35; i++) {
                assertEquals("query " + i, FACTS, countSolutions(queried));
            }
        } finally {
            writer.join();
        }
        if (failure.get() != null) {
            AssertionError error = new AssertionError("Concurrent API use failed");
            error.initCause(failure.get());
            throw error;
        }
    }

    public void testOriginalLazyIteratorAfterSymbolTableReset() {
        OOjdrewAPI original = new OOjdrewAPI();
        original.parseKnowledgeBase(ruleBase("Survivor"), false);
        original.issueRuleMLQuery(QUERY);
        assertTrue(original.rowsReturned > 0);

        // Inflate the symbol table in this one test thread to force the next
        // parseKnowledgeBase call to perform its normal epoch reset.
        for (int i = 0; i < 5200; i++) {
            SymbolTable.internSymbol("headless-regression-symbol-" + i);
        }
        OOjdrewAPI resetter = new OOjdrewAPI();
        resetter.parseKnowledgeBase(ruleBase("Resetter"), false);
        assertEquals(FACTS, countSolutions(resetter));
        assertEquals("The original cursor must resume after a reset",
                FACTS, countCurrentAndRemainingSolutions(original));
    }

    public void testEagerQueryWithoutVariablesIsAValidEmptyBinding() {
        OOjdrewAPI api = new OOjdrewAPI();
        api.parseKnowledgeBase(ruleBase("Boolean"), false);
        String query = "<Query><Atom><Rel>activeService</Rel>"
                + "<Ind>Boolean_Place0</Ind><Ind>Boolean_Operation</Ind>"
                + "<Ind>ip0</Ind><Ind>5000</Ind></Atom></Query>";
        List<Map<String, String>> results = api.queryRuleML(query);
        assertEquals(1, results.size());
        assertTrue(results.get(0).isEmpty());
    }

    private static int countSolutions(OOjdrewAPI api) {
        api.issueRuleMLQuery(QUERY);
        return api.rowsReturned == 0 ? 0 : countCurrentAndRemainingSolutions(api);
    }

    private static int countCurrentAndRemainingSolutions(OOjdrewAPI api) {
        int result = 1;
        while (api.hasNext) {
            api.nextSolution();
            result++;
        }
        return result;
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
