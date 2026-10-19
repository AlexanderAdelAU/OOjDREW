# Using OO jDREW as a headless Java rule engine

The `org.ruleml.oojdrew.api.OOjdrewAPI` class offers an embeddable interface to
OO jDREW's existing top-down RuleML reasoner. It does not launch a UI, and
applications do not need to implement GUI callbacks to query a knowledge base.

## Example

```java
import java.util.List;
import java.util.Map;
import org.ruleml.oojdrew.api.OOjdrewAPI;

OOjdrewAPI engine = new OOjdrewAPI();
engine.loadRuleML("<Assert><Rulebase mapClosure=\"universal\">"
    + "<Atom><Rel>colour</Rel><Ind>blue</Ind></Atom>"
    + "</Rulebase></Assert>");
List<Map<String, String>> answers = engine.queryRuleML(
    "<Query><Atom><Rel>colour</Rel><Var>x</Var></Atom></Query>");
System.out.println(answers);
```

`loadRuleML` takes a RuleML document **string**, not a filename. Read a file
into a string before passing it to the API. `queryRuleML` returns immutable
snapshots of the variable bindings for every solution; an empty list means no
solutions. Parsing and query failures raise `IllegalArgumentException` with a
cause. Querying before a knowledge base is loaded raises `IllegalStateException`.

## Concurrency and lifecycle

Each API instance owns its last successfully loaded knowledge base. All
operations on instances of *this API* are serialized under a JVM-wide lock,
because OO jDREW's `SymbolTable` uses global static state. A symbol-table reset
invalidates stored clauses; the API automatically rebuilds the affected instance
before its next query. Replacing the knowledge base creates a fresh
`BackwardReasoner`, so OID and clause indexes do not accumulate across loads.

**Scope limitation:** Direct calls to OO jDREW's `SymbolTable`, `TopDownApp`,
`BackwardReasoner`, or parsers do not participate in this API's lock and are
not made thread safe by this facade. A future upstream engine-level change
would be necessary for that guarantee.

The current query method materializes all solutions under the lock. Large or
non-terminating queries can use excessive memory or block other API callers.
A bounded or streaming API would be an appropriate separate enhancement.

## Build and test

OO jDREW uses Maven and existing JUnit 3 tests. After applying the patch in a
fork of `OOjDREW/OOjDREW`, run its build in a compatible Java/Maven environment.
The upstream `pom.xml` uses the `java-version` property, so the appropriate
value may need to be supplied, e.g. `mvn -Djava-version=1.8 test`.

This contribution was adapted from the callable OO jDREW wrapper developed in
[Rule-Propagated Service Orchestration](https://github.com/AlexanderAdelAU/Rule-Propagated-Service-Orchestration)
without incorporating RPSO services, tokens, or workflow rules.

## History

- Upstream discussion: https://github.com/OOjDREW/OOjDREW/issues/17
- Original use case: https://github.com/AlexanderAdelAU/Rule-Propagated-Service-Orchestration
- Related upstream concurrency discussion: https://github.com/OOjDREW/OOjDREW/issues/9
