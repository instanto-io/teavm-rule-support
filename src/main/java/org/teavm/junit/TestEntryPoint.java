/*
 * Copyright 2026 Carl Stainton
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.teavm.junit;

import java.util.ArrayList;
import java.util.List;
import org.junit.runner.Description;
import org.junit.runners.model.FrameworkMethod;
import org.teavm.jso.core.JSObjects;
import org.junit.runners.model.Statement;

final class TestEntryPoint {
    private static Object testCase;

    private TestEntryPoint() {
    }

    public static void run(String name) throws Throwable {
        List<Launcher> launchers = new ArrayList<>();
        testCase = createTestCase();
        launchers(name, launchers);
        for (Launcher launcher : launchers) {
            applyRules(new LaunchStatement(launcher), name).evaluate();
        }
    }

    private static native Object createTestCase();

    private static native void before();

    private static native void launchers(String name, List<Launcher> result) throws Throwable;

    private static native void after();

    /**
     * Wraps the statement in the test case's rules, and returns it unchanged when there are none.
     */
    private static native Statement applyRules(Statement base, String name);

    /**
     * Builds the {@code FrameworkMethod} a {@code MethodRule} is given.
     *
     * <p>{@code name} arrives qualified — as {@code pkg.Cls.test} or a TeaVM method reference such
     * as {@code pkg.Cls::test()V} depending on the launch path — so the
     * simple name is taken from it and resolved against the running test instance. Test methods are
     * made reflectable by the module's {@code ReflectionPolicy}; inherited ones are found by
     * walking the superclass chain.
     */
    static FrameworkMethod frameworkMethod(Object target, String name) {
        String simpleName = simpleMethodName(name);
        for (Class<?> cls = target.getClass(); cls != null; cls = cls.getSuperclass()) {
            try {
                return normalisingFrameworkMethod(cls.getDeclaredMethod(simpleName));
            } catch (NoSuchMethodException notHere) {
                continue;
            }
        }
        throw new IllegalStateException("Could not resolve test method " + simpleName + " on "
                + target.getClass().getName()
                + ". A MethodRule needs that method to be reflectable.");
    }

    /**
     * Wraps a method so a void reflective call yields {@code null}, as the JDK specifies.
     *
     * <p>TeaVM 0.15 returns JavaScript {@code undefined} instead: {@code $rt_callMethod} passes the
     * result through {@code valueToObject}, and {@code void} is the one primitive created without a
     * conversion, so it falls back to the identity default. An {@code undefined} is neither
     * {@code null} to a Java comparison nor safe to dereference, so it is normalised here before
     * any rule can observe it.
     */
    private static FrameworkMethod normalisingFrameworkMethod(java.lang.reflect.Method method) {
        return new FrameworkMethod(method) {
            @Override
            public Object invokeExplosively(Object target, Object... params) throws Throwable {
                Object result = super.invokeExplosively(target, params);
                return JSObjects.isUndefined(result) ? null : result;
            }
        };
    }

    /**
     * Reduces the launcher's method identifier to a plain method name.
     *
     * <p>It arrives qualified — as {@code pkg.Cls.test} or a TeaVM method reference such as
     * {@code pkg.Cls::test()V} depending on the launch path. Rules see this through
     * {@code Description.getMethodName()} and {@code FrameworkMethod.getName()}, both of which
     * report the simple name on the JVM, so it is normalised once here.
     */
    static String simpleMethodName(String name) {
        String simpleName = name == null ? "" : name;
        int separator = simpleName.indexOf("::");
        if (separator >= 0) {
            simpleName = simpleName.substring(separator + 2);
        }
        int parenthesis = simpleName.indexOf('(');
        if (parenthesis >= 0) {
            simpleName = simpleName.substring(0, parenthesis);
        }
        int lastDot = simpleName.lastIndexOf('.');
        if (lastDot >= 0) {
            simpleName = simpleName.substring(lastDot + 1);
        }
        return simpleName;
    }

    static Description describe(String className, String name) {
        String simpleName = simpleMethodName(name);
        return Description.createTestDescription(className,
                simpleName.isEmpty() ? className : simpleName);
    }

    public static void main(String[] args) throws Throwable {
        run(args.length == 1 ? args[0] : null);
    }

    interface Launcher {
        void launch(Object testCase) throws Throwable;
    }

    private static final class LaunchStatement extends Statement {
        private final Launcher launcher;

        LaunchStatement(Launcher launcher) {
            this.launcher = launcher;
        }

        @Override
        public void evaluate() throws Throwable {
            before();
            Throwable failure = null;
            try {
                launcher.launch(testCase);
            } catch (Throwable e) {
                failure = e;
            }
            try {
                after();
            } catch (Throwable e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
