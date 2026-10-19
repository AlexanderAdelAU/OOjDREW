// OO jDREW - An Object Oriented extension of the Java Deductive Reasoning Engine for the Web
// Original callable interface: AlexanderAdelAU, adapted from RPSO.
//
// Licensed under the GNU Lesser General Public License, version 2.1 or later.
// This contribution retains the original OO jDREW engine's licensing.
package org.ruleml.oojdrew.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.log4j.Logger;
import org.ruleml.oojdrew.Config;
import org.ruleml.oojdrew.Configuration;
import org.ruleml.oojdrew.TopDown.BackwardReasoner;
import org.ruleml.oojdrew.parsing.RuleMLParser;
import org.ruleml.oojdrew.util.DefiniteClause;
import org.ruleml.oojdrew.util.SymbolTable;

/**
 * Callable, headless interface to OO jDREW's top-down RuleML reasoner.
 *
 * <p>The original RPSO interface and lazy solution iteration are preserved.
 * {@link #queryRuleML(String)} is an optional eager convenience method.
 * The shared engine lock protects callers that use this facade; direct
 * lower-level OO jDREW calls are not protected by it.</p>
 *
 * @author AlexanderAdelAU
 */
public class OOjdrewAPI {
	private static final Logger logger = Logger.getLogger(OOjdrewAPI.class);
	private Iterator<?> solit;
	private static final int MAXROWS = 16;
	private static final int MAXCOLS = 2;

	Configuration config = new Config(OOjdrewAPI.class);

	// Create the parser
	RuleMLParser rmlParser = new RuleMLParser(config);

	// Create the reasoning engine
	BackwardReasoner backwardReasoner = new BackwardReasoner();
	public Object rowData[][] = new Object[MAXROWS][MAXCOLS];
	public boolean hasNext = false;
	public int rowsReturned = 0;

	// OO jDREW stores its symbol table in JVM-global static fields. Operations
	// through this facade are serialized to prevent conflicting symbol resets
	// across API instances. Older rule bases and lazy solution iterators are
	// reconstructed after a facade-driven reset when required.
	private static final Object ENGINE_LOCK = new Object();
	private static final int SYMBOL_RESET_THRESHOLD = 5000;
	private static long symbolEpoch = 0;
	private String parsedKnowledgeBase = null;
	private long parsedEpoch = -1;
	private String lastQuery = null;
	private long queryEpoch = -1;
	private int solutionsReturned = 0;

	// public void parseResult(boolean x, Object[][] result, int rowsReturned) {
	// TODO Auto-generated method stub

	// }
	/** Legacy no-op GUI entry point; a headless API does not start a UI. */
	public void run() {
	}

	public String getRuleBaset(String ruleFileName) {
		String ruleBase;
		try {

			// right before you try to read from the file
			File appBase = new File(""); // current directory
		// Handle relative paths (starting with ..) and absolute paths correctly
		String path;
		if (ruleFileName.startsWith("..") || ruleFileName.startsWith("/") || 
		    ruleFileName.startsWith("\\") || (ruleFileName.length() > 1 && ruleFileName.charAt(1) == ':')) {
			// Relative path starting with .. or absolute path - use as-is
			path = ruleFileName;
		} else {
			// Relative path without .. - concatenate with canonical path
			path = appBase.getCanonicalPath() + File.separator + ruleFileName;
		}
		System.out.println("DEBUG: getRuleBaset attempting to read from: " + path);

			FileReader fileReader = new FileReader(path);

			BufferedReader bufferedReader = new BufferedReader(fileReader);

			StringBuilder stringBuilder = new StringBuilder();
			String lineSeparator = System.getProperty("line.separator");
			String currentLine;

			while ((currentLine = bufferedReader.readLine()) != null) {
				stringBuilder.append(currentLine);
				stringBuilder.append(lineSeparator);
			}

			ruleBase = stringBuilder.toString();
			bufferedReader.close();
		} catch (IOException e) {
			defaultExceptionHandler(e);
			return null;
		}
		return ruleBase;
	}

	public void parseKnowledgeBase(String RuleBase, boolean loadFromFile) {
		String knowledgeBase = loadFromFile ? getRuleBaset(RuleBase) : RuleBase;
		synchronized (ENGINE_LOCK) {
			// Parsing mints new generated symbols, so the shared table grows with every parse.
			// Reset it only when large; instances parsed before the reset re-parse lazily.
			if (SymbolTable.symbols.size() > SYMBOL_RESET_THRESHOLD) {
				SymbolTable.reset();
				symbolEpoch++;
			}
			backwardReasoner.clearClauses();
			// clearClauses() empties only the clause table; jDREW also indexes every loaded clause
			// in oids and never clears it, so without this each parse kept a copy of the rule base
			// and a long-running host got slower with every token it served.
			backwardReasoner.oids.clear();
			parsedKnowledgeBase = knowledgeBase;
			parsedEpoch = symbolEpoch;
			if (knowledgeBase == null || knowledgeBase.isEmpty()) {
				return;
			}
			parseRuleMLKnowledeBase(knowledgeBase);
		}
	}

	private void parseRuleMLKnowledeBase(String knowledgeBase) {
		rmlParser.clear();

		try {
			rmlParser.parseRuleMLString(knowledgeBase);
		} catch (Exception e) {
			defaultExceptionHandler(e);
			return;
		}

		backwardReasoner.loadClauses(rmlParser.iterator());
	}

	private void defaultExceptionHandler(Exception e) {
		if (logger != null) {
			logger.error(e.getMessage());
		} else {
			System.err.println("OOjDREW ERROR: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void issueRuleMLQuery(String query) {
		synchronized (ENGINE_LOCK) {
			lastQuery = query;
			runQuery(query);
		}
	}

	/** Runs a query with ENGINE_LOCK held, re-parsing this rule base first if a reset invalidated it. */
	private void runQuery(String query) {
		if (parsedKnowledgeBase != null && parsedEpoch != symbolEpoch) {
			// The shared symbol table was reset after this rule base was parsed; its
			// clauses refer to symbols that no longer exist, so intern them again first.
			backwardReasoner.clearClauses();
			// clearClauses() empties only the clause table; jDREW also indexes every loaded clause
			// in oids and never clears it, so without this each parse kept a copy of the rule base
			// and a long-running host got slower with every token it served.
			backwardReasoner.oids.clear();
			parsedEpoch = symbolEpoch;
			if (!parsedKnowledgeBase.isEmpty()) {
				parseRuleMLKnowledeBase(parsedKnowledgeBase);
			}
		}
		queryEpoch = symbolEpoch;
		rmlParser.clear();
		try {
			DefiniteClause dc = rmlParser.parseRuleMLQuery(query);
			processQuery(dc);
		} catch (Exception e) {
			solit = null;
			hasNext = false;
			rowsReturned = 0;
			defaultExceptionHandler(e);
		}
	}

	private void processQuery(DefiniteClause dc) {
		// Reinitialise the Array
		Object[][] rowdata = new Object[MAXROWS][MAXCOLS];
		System.arraycopy(rowdata, 0, rowData, 0, MAXROWS);
		rowsReturned = 0;
		hasNext = false;
		solutionsReturned = 0;

		backwardReasoner = new BackwardReasoner(backwardReasoner.clauses, backwardReasoner.oids);
		solit = backwardReasoner.iterativeDepthFirstSolutionIterator(dc);

		if (solit.hasNext()) {
			showSolution((BackwardReasoner.GoalList) solit.next());
			hasNext = solit.hasNext();
		}
	}

	private void showSolution(BackwardReasoner.GoalList gl) {
		Hashtable varbind = gl.varBindings;
		int i = 0;
		Enumeration e = varbind.keys();
		while (e.hasMoreElements()) {
			Object k = e.nextElement();
			Object val = varbind.get(k);
			String ks = (String) k;
			rowData[i][0] = ks;
			rowData[i][1] = val;
			i++;
		}
		rowsReturned = i;
		solutionsReturned++;
	}

	public void nextSolution() {
		synchronized (ENGINE_LOCK) {
			if (solit == null) {
				throw new NoSuchElementException("No open OOjDREW query");
			}
			if (queryEpoch != symbolEpoch && lastQuery != null) {
				// A reset invalidated the open iterator: re-run the query and skip the
				// solutions this caller has already seen (the search order is deterministic).
				int seen = solutionsReturned;
				runQuery(lastQuery);
				while (solutionsReturned < seen && solit != null && solit.hasNext()) {
					showSolution((BackwardReasoner.GoalList) solit.next());
				}
				if (solit == null) {
					throw new NoSuchElementException("OOjDREW query could not be re-run");
				}
			}
			showSolution((BackwardReasoner.GoalList) solit.next());
			hasNext = solit.hasNext();
		}
	}

	/**
	 * Convenience API: evaluate a query and return every solution as an immutable
	 * list of variable-binding maps. This does not replace issueRuleMLQuery(),
	 * hasNext or nextSolution(): existing callers can continue to fetch answers
	 * lazily. This method consumes the current query cursor.
	 *
	 * <p>All results are computed under the same lock as the legacy operations;
	 * a large or nonterminating query may consume memory and block other callers.</p>
	 */
	public List<Map<String, String>> queryRuleML(String query) {
		synchronized (ENGINE_LOCK) {
			issueRuleMLQuery(query);
			List<Map<String, String>> results = new java.util.ArrayList<Map<String, String>>();
			if (solutionsReturned > 0) {
				results.add(snapshotBindings());
				while (hasNext) {
					nextSolution();
					results.add(snapshotBindings());
				}
			}
			return Collections.unmodifiableList(results);
		}
	}

	/** Snapshot public legacy row data without sharing its mutable arrays. */
	private Map<String, String> snapshotBindings() {
		Map<String, String> bindings = new LinkedHashMap<String, String>();
		for (int i = 0; i < rowsReturned; i++) {
			bindings.put(String.valueOf(rowData[i][0]),
					String.valueOf(rowData[i][1]));
		}
		return Collections.unmodifiableMap(bindings);
	}
}
