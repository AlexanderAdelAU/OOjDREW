// OO jDREW - An Object Oriented extension of the Java Deductive Reasoning Engine for the Web
// Copyright (C) 2026 OO jDREW contributors
//
// This library is free software; you can redistribute it and/or modify it under
// the terms of the GNU Lesser General Public License as published by the Free
// Software Foundation; either version 2.1, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License.
package org.ruleml.oojdrew.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.ruleml.oojdrew.Config;
import org.ruleml.oojdrew.Configuration;
import org.ruleml.oojdrew.TopDown.BackwardReasoner;
import org.ruleml.oojdrew.parsing.RuleMLParser;
import org.ruleml.oojdrew.util.DefiniteClause;
import org.ruleml.oojdrew.util.SymbolTable;

/**
 * A GUI-independent, embeddable OO jDREW top-down RuleML reasoner.
 *
 * <p>Each instance owns a knowledge base. The underlying OO jDREW library has
 * JVM-global symbol tables, so operations through instances of this facade are
 * serialized. This does NOT make direct uses of OO jDREW's lower-level classes
 * thread safe. Query results are detached immutable snapshots.</p>
 *
 * <p>This is a focused first contribution derived from the callable engine
 * integration proven in Rule-Propagated Service Orchestration (RPSO).</p>
 *
 * <p>Original callable OO jDREW interface developed by AlexanderAdelAU
 * for the RPSO project; adapted here as a standalone API. OO jDREW's
 * underlying reasoning engine retains its original authorship.</p>
 *
 * @author AlexanderAdelAU
 */
public final class OOjdrewAPI {
    private static final Object ENGINE_LOCK = new Object();
    private static final int SYMBOL_RESET_THRESHOLD = 5000;
    private static long symbolEpoch;

    private final RuleMLParser parser;
    private BackwardReasoner reasoner;
    private String loadedRuleML;
    private long loadedEpoch = -1;

    public OOjdrewAPI() {
        Configuration config = new Config(OOjdrewAPI.class);
        parser = new RuleMLParser(config);
        synchronized (ENGINE_LOCK) {
            reasoner = new BackwardReasoner();
        }
    }

    /**
     * Replace this instance's knowledge base with a RuleML assertion document.
     * On a parse error, the previously successfully loaded knowledge base is kept.
     *
     * @throws IllegalArgumentException if the RuleML cannot be parsed
     */
    public void loadRuleML(String ruleML) {
        if (ruleML == null) {
            throw new IllegalArgumentException("ruleML must not be null");
        }
        synchronized (ENGINE_LOCK) {
            if (SymbolTable.symbols.size() > SYMBOL_RESET_THRESHOLD) {
                SymbolTable.reset();
                symbolEpoch++;
            }
            BackwardReasoner replacement = parseKnowledgeBase(ruleML);
            reasoner = replacement;
            loadedRuleML = ruleML;
            loadedEpoch = symbolEpoch;
        }
    }

    /**
     * Evaluate a RuleML Query against this instance's loaded knowledge base.
     * Every result maps query variable names to their OO jDREW string values.
     *
     * <p>Results are fully evaluated under the shared engine lock so callers
     * can use them after it is released. For queries with very many answers,
     * the returned collection can consume substantial memory.</p>
     *
     * @throws IllegalStateException if no knowledge base has been loaded
     * @throws IllegalArgumentException if the query cannot be parsed
     */
    public List<Map<String, String>> queryRuleML(String ruleMLQuery) {
        if (ruleMLQuery == null) {
            throw new IllegalArgumentException("ruleMLQuery must not be null");
        }
        synchronized (ENGINE_LOCK) {
            if (loadedRuleML == null) {
                throw new IllegalStateException("Load a RuleML knowledge base before querying");
            }
            if (loadedEpoch != symbolEpoch) {
                // A reset by another facade instance invalidates the symbols
                // referenced by our earlier clauses. Re-intern them on demand.
                reasoner = parseKnowledgeBase(loadedRuleML);
                loadedEpoch = symbolEpoch;
            }
            try {
                parser.clear();
                DefiniteClause clause = parser.parseRuleMLQuery(ruleMLQuery);
                BackwardReasoner queryReasoner =
                        new BackwardReasoner(reasoner.clauses, reasoner.oids);
                Iterator<?> solutions =
                        queryReasoner.iterativeDepthFirstSolutionIterator(clause);
                List<Map<String, String>> results =
                        new ArrayList<Map<String, String>>();
                while (solutions.hasNext()) {
                    BackwardReasoner.GoalList goal =
                            (BackwardReasoner.GoalList) solutions.next();
                    Map<String, String> bindings = new LinkedHashMap<String, String>();
                    Enumeration<?> variables = goal.varBindings.keys();
                    while (variables.hasMoreElements()) {
                        Object name = variables.nextElement();
                        bindings.put(String.valueOf(name),
                                String.valueOf(goal.varBindings.get(name)));
                    }
                    results.add(Collections.unmodifiableMap(bindings));
                }
                return Collections.unmodifiableList(results);
            } catch (Exception e) {
                throw new IllegalArgumentException("Unable to evaluate RuleML query", e);
            }
        }
    }

    /** Requires ENGINE_LOCK to be held by the caller. */
    private BackwardReasoner parseKnowledgeBase(String ruleML) {
        try {
            parser.clear();
            parser.parseRuleMLString(ruleML);
            BackwardReasoner replacement = new BackwardReasoner();
            replacement.loadClauses(parser.iterator());
            return replacement;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to load RuleML knowledge base", e);
        }
    }
}
