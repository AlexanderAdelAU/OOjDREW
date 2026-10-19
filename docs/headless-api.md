# OO jDREW callable Java API

OO jDREW provides a headless Java interface, `org.ruleml.oojdrew.api.OOjdrewAPI`,
for loading RuleML knowledge bases and querying the top-down reasoner without
starting a GUI.

**The original callable API is the primary interface.** The methods and
incremental query mechanism originated in
[AlexanderAdelAU's RPSO project](https://github.com/AlexanderAdelAU/Rule-Propagated-Service-Orchestration).
The optional `queryRuleML(String)` method retrieves all solutions at once.
It is an addition, **not a replacement** for the original API.

## Original interface — incremental solutions

```java
import org.ruleml.oojdrew.api.OOjdrewAPI;

OOjdrewAPI engine = new OOjdrewAPI();
String ruleBase = "<Assert><Rulebase mapClosure=\"universal\">"
    + "<Atom><Rel>colour</Rel><Ind>blue</Ind></Atom>"
    + "</Rulebase></Assert>";

engine.parseKnowledgeBase(ruleBase, false);
engine.issueRuleMLQuery(
    "<Query><Atom><Rel>colour</Rel><Var>x</Var></Atom></Query>");

// The initial answer, if present, is exposed in rowData.
if (engine.rowsReturned > 0) {
    do {
        for (int i = 0; i < engine.rowsReturned; i++) {
            System.out.println(engine.rowData[i][0]
                    + " = " + engine.rowData[i][1]);
        }
        if (!engine.hasNext) {
            break;
        }
        engine.nextSolution();
    } while (true);
}
```

Original public members are retained:

- `getRuleBaset(String)` reads a knowledge base from a file (historic method
  spelling retained for compatibility).
- `parseKnowledgeBase(String, boolean)` takes RuleML text when the second
  argument is `false`, or a file path when it is `true`.
- `issueRuleMLQuery(String)` evaluates the query and exposes its first answer
  through `rowData`, `rowsReturned` and `hasNext`.
- `nextSolution()` advances to the next available answer. It throws when
  there is no open query or no next solution.
- `run()` remains a no-op legacy GUI entry point.

The public `rowData` array has 16 variable-binding rows, as in the original
implementation. Parsing and query errors are logged through the existing
Log4j handler. Applications needing more variable bindings or explicit
exceptions require a separate API enhancement.

## Optional interface — all solutions

```java
import java.util.List;
import java.util.Map;

List<Map<String, String>> solutions = engine.queryRuleML(
    "<Query><Atom><Rel>colour</Rel><Var>x</Var></Atom></Query>");

for (Map<String, String> solution : solutions) {
    System.out.println(solution.get("?x"));
}
```

`queryRuleML` performs the same query but collects all answers into an
immutable list of immutable maps. It consumes the current query cursor.
The original incremental query methods are **still available** to callers
who do not want to materialize all results at once.

For a query with no variable bindings, a successful solution is represented
by an empty map; no matching solutions produces an empty list.

## Concurrency and lifetime

OO jDREW's `SymbolTable` is JVM-global. This facade uses a shared lock for
parsing, querying and advancing the solution iterator; callers that use the
facade are serialized. Rule bases and open iterators are re-established after
a facade-driven symbol-table reset.

**Limitation:** lower-level OO jDREW classes used directly do not participate
in this lock, so this facade cannot guarantee engine-wide thread safety.
Similarly, public result fields are mutable and should not be shared between
uncoordinated threads. The eager convenience method may use significant
memory or block other callers for large or nonterminating queries.

## Compatibility with RPSO

The callable method contract is preserved, but the upstream distribution uses
a different Java package (`org.ruleml.oojdrew.api` instead of RPSO's
`org.btsn.utils`). RPSO can retain its existing working wrapper without any
change. A future migration would involve a deliberate import or adapter and
library dependency update, tested independently; it is **not required** to
use this contribution in other Java projects.

## Build and tests

The upstream OO jDREW project has a Java/Maven build and JUnit 3 regression
tests. The fork's CI runs the existing and new tests with a JUnit 3-compatible
Surefire version. Neither this contribution nor the workflow changes RPSO's
Ant-only build process.

## Origins and acknowledgements

**Original callable API developer: [AlexanderAdelAU](https://github.com/AlexanderAdelAU).**

The API was originally extracted for
[Rule-Propagated Service Orchestration](https://github.com/AlexanderAdelAU/Rule-Propagated-Service-Orchestration),
then contributed as a reusable OO jDREW interface. The underlying OO jDREW
reasoning engine remains credited to its original authors, with its existing
LGPL licensing preserved.

- API proposal: https://github.com/OOjDREW/OOjDREW/issues/17
- Related thread-safety discussion: https://github.com/OOjDREW/OOjDREW/issues/9
