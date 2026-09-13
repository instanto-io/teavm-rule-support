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

## Add the dependency

The project must already use JUnit 4 and TeaVM's JUnit runner. Add this module as
a test dependency and declare it before `teavm-junit`:

```xml
<dependencies>
  <dependency>
    <groupId>io.instanto</groupId>
    <artifactId>teavm-rule-support</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>test</scope>
  </dependency>

  <dependency>
    <groupId>org.teavm</groupId>
    <artifactId>teavm-junit</artifactId>
    <version>0.15.0</version>
    <scope>test</scope>
  </dependency>
</dependencies>
```

No further setup is required. The support can also arrive transitively from
another test dependency, provided the JAR is present on the classpath TeaVM uses
for test compilation.

Classpath order matters because TeaVM's runner does not currently expose a rule
extension point. This module supplies an extended `TestEntryPoint` and compiler
transformers under the class names expected by `TeaVMTestRunner`. The module's
compiler plugin checks which `TestEntryPoint` was selected. If `teavm-junit`
wins, compilation fails with a message explaining how to correct the dependency
order.

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

## Building and testing

The test suite compiles its fixtures with TeaVM and runs them in Chrome:

```bash
mvn test
```
