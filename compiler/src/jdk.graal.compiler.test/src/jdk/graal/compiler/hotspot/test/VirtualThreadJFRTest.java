/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.graal.compiler.hotspot.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.test.SubprocessTest;
import jdk.graal.compiler.options.OptionValues;
import org.junit.Test;

import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;
import jdk.vm.ci.code.InstalledCode;

public class VirtualThreadJFRTest extends SubprocessTest {
    private static final int VIRTUAL_THREAD_COUNT = 100_000;
    private static final int STARTER_THREADS = 10;

    @Name("test.Tester")
    private static class TestEvent extends Event {
    }

    public static void testSnippet() {
        try (Recording r = new Recording()) {
            r.start();

            ThreadFactory factory = Thread.ofVirtual().factory();
            CompletableFuture<?>[] c = new CompletableFuture<?>[STARTER_THREADS];
            for (int j = 0; j < STARTER_THREADS; j++) {
                c[j] = CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < VIRTUAL_THREAD_COUNT / STARTER_THREADS; i++) {
                        try {
                            Thread vt = factory.newThread(VirtualThreadJFRTest::emitEvent);
                            vt.start();
                            vt.join();
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }
                    }
                });
            }
            for (int j = 0; j < STARTER_THREADS; j++) {
                c[j].get();
            }

            r.stop();
            Path p = Files.createTempFile("test", ".jfr");
            r.dump(p);
            long size = Files.size(p);

            assertTrue(size < 100_000_000L, "Size of recording looks suspiciously large");
            List<RecordedEvent> events = RecordingFile.readAllEvents(p);
            assertTrue(events.size() == VIRTUAL_THREAD_COUNT, "Expected %d events, but was %d", VIRTUAL_THREAD_COUNT, events.size());
            for (RecordedEvent e : events) {
                RecordedThread t = e.getThread();
                assertTrue(t != null);
                assertTrue(t.isVirtual());
                assertTrue(t.getOSName() == null);
                assertTrue(t.getOSThreadId() == -1L);
                // vthreads default name is the empty string
                assertTrue("".equals(t.getJavaName()));
                assertTrue(t.getJavaThreadId() > 0L);
                assertTrue("VirtualThreads".equals(t.getThreadGroup().getName()));
            }
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    private static void emitEvent() {
        TestEvent t = new TestEvent();
        t.commit();
    }

    public void testJFR() {
        try {
            // resolve class, profile exceptions
            testSnippet();
            Class<?> clazz = Class.forName("java.lang.VirtualThread");
            InstalledCode code = getCode(getResolvedJavaMethod(clazz, "mount"), null, true, true,
                            new OptionValues(getInitialOptions(), GraalOptions.RemoveNeverExecutedCode, false));
            assertTrue(code.isValid());
            testSnippet();
            assertTrue(code.isValid());
            code.invalidate();
        } catch (ClassNotFoundException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testInSubprocess() throws InterruptedException, IOException {
        launchSubprocess(this::testJFR);
    }

}
