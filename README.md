# TeaVM JUnit rule support

`teavm-rule-support` adds JUnit 4 `@Rule` support to tests compiled and run
with TeaVM's `TeaVMTestRunner`.

Add the JAR to the classpath used to compile your TeaVM tests and the support is
installed automatically. Tests continue to use normal JUnit code. There is no
custom runner, base class, registration call, or generated source to add.

```java
@RunWith(TeaVMTestRunner.class)
public class BrowserTest {
    @Rule
    public final TestRule resource = new BrowserResourceRule();

    @Test
    public void usesTheResource() {
        // The rule is active here.
    }
}
```

## What you get

When the JAR is present on TeaVM's test compiler classpath:

- instance fields and no-argument instance methods annotated with `@Rule` are
  applied to each test;
- both `TestRule` and `MethodRule` are supported;
- inherited rules and JUnit 4.13 rule ordering, including
  `@Rule(order = ...)`, are preserved;
- a `TestRule` receives a `Description` containing the test class and method;
- a `MethodRule` receives a real `FrameworkMethod`, including the test
  method's annotations; and
- incorrect classpath ordering is reported during TeaVM compilation instead of
  allowing tests to pass while silently ignoring their rules.

Rules wrap TeaVM's existing per-test lifecycle:

```text
rule setup
  @Before
    @Test
  @After
rule teardown
```

Rule failures are reported as test failures. If the test body and `@After` both
fail, the `@After` failure is attached to the test failure as a suppressed
exception. The setup and teardown behavior of an individual rule remains
defined by the `Statement` returned by that rule, as it is on the JVM.

Tests without `@Rule` members behave as before. Their generated rule hook
returns the original test statement unchanged.

Without this module, `TeaVMTestRunner` still runs its normal `@Before`,
`@Test`, and `@After` methods, but JUnit `@Rule` members are not applied.

## Rule kinds

Both JUnit rule interfaces are supported, declared either as a field or as a
no-argument method.

### TestRule

A `TestRule` receives the statement and a `Description` naming the test class
and method, which suits a rule that sets something up and tears it down:

```java
@Rule
public final TestRule resource = new ResourceRule();
```

### MethodRule

A `MethodRule` receives the statement, the `FrameworkMethod` and the test
instance. Use it when the rule needs the method itself rather than a
description of it: to read an annotation that carries behaviour, to reflect
over the method, or to evaluate the body more than once.

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Retry {
    int times();
}

static final class RetryRule implements MethodRule {
    @Override
    public Statement apply(Statement base, FrameworkMethod method, Object target) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Retry retry = method.getAnnotation(Retry.class);
                int required = retry == null ? 1 : retry.times();
                for (int attempt = 0; attempt < required; attempt++) {
                    base.evaluate();
                }
            }
        };
    }
}
```

```java
@Rule
public final RetryRule retry = new RetryRule();

@Test
@Retry(times = 3)
public void theBodyRunsThreeTimes() {
    // Evaluated once per attempt.
}
```

The `FrameworkMethod` is a real one, so `method.getName()` and
`method.getAnnotation(...)` answer as they do on the JVM. That relies on the
test method being reflectable, which the bundled policy arranges; see
[Supported scope](#supported-scope) for where that selection ends.

A `Description` cannot do this. It exposes the annotations, but a rule that
needs the method itself needs the `FrameworkMethod`.

### Rules declared as methods

A no-argument method annotated `@Rule` is applied like a field, and returns
either kind:

```java
@Rule
public TestRule resource() {
    return new ResourceRule();
}
```

### Ordering

Rules on one class are ordered by `@Rule(order = ...)`. JUnit treats a higher
value as inner, so the lower value wraps the other, setting up first and tearing
down last:

```java
@Rule(order = 0)
public final TestRule resource = new ResourceRule();

@Rule(order = 1)
public final RetryRule retry = new RetryRule();
```

Inherited rules are applied too, and a type implementing both interfaces is
applied once as a `TestRule`, matching JUnit 4.13.

## Use it with TeaVM tests

Add this library to the TeaVM test classpath before `teavm-junit`. Keep using
`TeaVMTestRunner` and ordinary JUnit `@Rule` members; no registration call is
needed. If TeaVM selects the original test entry point first, compilation
reports the classpath-order error rather than silently skipping rules.

## What TeaVM discovers automatically

The JAR registers a `TeaVMPlugin` and a reflection policy through Java service
provider files. TeaVM discovers both from the compiler classpath.

During compilation the module:

1. adds a rule hook to TeaVM's generated test entry point;
2. generates direct field reads and method calls for every rule, so rule
   discovery does not require run-time reflection;
3. retains the test-method reflection metadata needed by `MethodRule`; and
4. makes JUnit's `Description` usable with TeaVM.

JUnit's `Description` normally creates a `ConcurrentLinkedQueue` for its
children. That implementation cannot be compiled by the targeted TeaVM
classlib. The plugin rewrites only this construction to use a small,
TeaVM-compatible collection. It does not replace `ConcurrentLinkedQueue`
elsewhere in the application and does not alter TeaVM's class library globally.

The replacement uses snapshot iterators. If a TeaVM continuation resumes after
another continuation has added a description, an existing iteration remains
valid and does not throw `ConcurrentModificationException`. This is narrowly
scoped support for JUnit descriptions; it is not a general concurrent queue and
does not add threads or blocking behavior to TeaVM.

## Supported scope

This module supports JUnit 4 instance `@Rule` members used with
`TeaVMTestRunner`. It does not add `@ClassRule` support, provide general JUnit
runner extensibility, or change normal JVM test execution.

Rule member types must be assignable to `TestRule` or `MethodRule` at compile
time. Rule methods must take no arguments. A type implementing both interfaces
is applied once as a `TestRule`, matching JUnit 4.13.

`MethodRule` needs the target test method to be reflectable. The bundled policy
covers JUnit `@Test` methods on `@RunWith` classes. If a custom test
arrangement falls outside that selection, rule execution fails explicitly when
the method cannot be resolved.

## Compatibility

The current implementation targets TeaVM 0.15.0 and JUnit 4.13.2. It relies on
TeaVM test-runner internals and the shape of JUnit's `Description` constructor,
so upgrades to either dependency should be tested before adoption. A known
`Description` incompatibility fails during TeaVM compilation rather than later
in the browser.
