/*
 * Copyright 2026 Carl Stainton
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.instanto.rules;

import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTestRunner;

/**
 * Characterises TeaVM 0.15's broken suppressed-exception support.
 *
 * <p>{@code TThrowable} declares {@code suppressed} with a field initialiser, but its constructors
 * are split: the source constructors carry {@code @Rename("fakeInit")} and are never invoked,
 * while ordinary methods carry {@code @Rename("<init>")} and become the real constructors. Java
 * compiles field initialisers into constructors, so the array is initialised in the discarded
 * half and every throwable is left with a null {@code suppressed}. {@code suppressionEnabled} is
 * set in the surviving half, so {@code addSuppressed} passes its guard and then dereferences null.
 *
 * <p>Fixed upstream by konsoletyper/teavm#1252, which initialises the array in each replacement
 * constructor, but not released in 0.15.0. When these assertions start failing, the workaround in
 * {@code ApplicationRule} can be removed.
 *
 * <p>{@code sarto-async} carries a compiler plugin that initialises the array lazily instead. It
 * patches {@code addSuppressed} only, so {@code getSuppressed} on an untouched throwable still
 * fails; rule support avoids both rather than depending on it.
 */
@RunWith(TeaVMTestRunner.class)
@SkipJVM
public class SuppressedDeviationTeaVmTest {

    @Test
    public void getSuppressedStillThrowsOnThisTeaVm() {
        RuntimeException exception = new RuntimeException("primary");
        assertThrows("TeaVM now initialises suppressed - the workaround can be removed",
                NullPointerException.class, exception::getSuppressed);
    }

    @Test
    public void addSuppressedStillThrowsOnThisTeaVm() {
        RuntimeException primary = new RuntimeException("primary");
        RuntimeException secondary = new RuntimeException("secondary");
        assertThrows("TeaVM now initialises suppressed - the workaround can be removed",
                NullPointerException.class, () -> primary.addSuppressed(secondary));
    }
}
