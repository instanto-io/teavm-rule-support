/*
 * Copyright 2026 Carl Stainton
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.instanto.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.junit.runner.RunWith;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTestRunner;

/**
 * Characterises TeaVM's void-reflection deviation and proves rule support normalises it.
 *
 * <p>TeaVM 0.15 returns JavaScript {@code undefined} from {@code Method.invoke} on a void method,
 * where the JDK specifies {@code null}. The cause is in {@code $rt_callMethod}, which passes the
 * result through {@code valueToObject}; {@code void} is the only primitive created without a
 * conversion, so it falls back to the identity default in {@code $rt_newClassMetadata}.
 */
@RunWith(TeaVMTestRunner.class)
@SkipJVM
public class VoidInvokeDiagnosticTeaVmTest {

    /**
     * Asserts what a MethodRule sees when it invokes a void test method reflectively.
     *
     * <p>Only {@link VoidInvokeDiagnosticTeaVmTest#probeVoidMethod()} is invoked, so the extra
     * call re-enters an empty body. Probing whichever test happened to be running would re-enter
     * that test's own assertions.
     */
    static final class InvokingRule implements MethodRule {
        static final String PROBE = "probeVoidMethod";

        @Override
        public Statement apply(Statement base, FrameworkMethod method, Object target) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    if (PROBE.equals(method.getName())) {
                        assertNull("rule support must normalise TeaVM's undefined to null",
                                method.invokeExplosively(target));
                    }
                    base.evaluate();
                }
            };
        }
    }

    @Rule
    public InvokingRule invokingRule = new InvokingRule();

    @Test
    public void rawReflectionStillReturnsUndefinedOnThisTeaVm() throws Exception {
        Method method =
                VoidInvokeDiagnosticTeaVmTest.class.getDeclaredMethod("probeVoidMethod");
        assertEquals(void.class, method.getReturnType());
        assertTrue("Void.TYPE is void.class", Void.TYPE == method.getReturnType());

        Object result = method.invoke(this);

        // Documents the deviation. When TeaVM fixes it this assertion flips and we can drop
        // the normalisation in TestEntryPoint.
        assertFalse("TeaVM now returns null for void invoke - normalisation can be removed",
                result == null);
    }

    /** The rule asserts the normalisation around this method; the body stays empty. */
    @Test
    public void probeVoidMethod() {
    }
}
