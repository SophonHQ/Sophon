/*
 * Copyright (c) 2010-2022. Axon Framework
 * Modifications Copyright (c) 2026. SophonHQ
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sophonhq.sophon.common.caching;

import org.junit.jupiter.api.*;
import org.sophonhq.sophon.common.Registration;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;


/**
 * Tests for cache implementation that keeps values in the cache until the garbage collector has removed them
 * {@link WeakReferenceCache}.
 *
 * @author Steven van Beelen
 * @author Henrique Sena
 */
class WeakReferenceCacheTest {

    private WeakReferenceCache testSubject;
    private Cache.EntryListener mockListener;
    private Registration registration;

    @BeforeEach
    void setUp() {
        mockListener = mock(Cache.EntryListener.class);
        testSubject = new WeakReferenceCache();
        registration = testSubject.registerCacheEntryListener(mockListener);
    }

    @Test
    void itemPurgedWhenNoLongerReferenced() throws Exception {
        // Mockito holds a reference to all parameters, preventing GC
        registration.cancel();
        final Set<String> expiredEntries = new CopyOnWriteArraySet<>();
        testSubject.registerCacheEntryListener(new Cache.EntryListenerAdapter() {
            @Override
            public void onEntryExpired(Object key) {
                expiredEntries.add(key.toString());
            }
        });

        Object value = new Object();
        testSubject.put("test1", value);
        assertThatObject(testSubject.get("test1")).isSameAs(value);

        // dereference
        value = null;
        // hope for a true GC
        System.gc();

        for (int i = 0; i < 5 && testSubject.containsKey("test1"); i++) {
            // try again
            System.gc();
            Thread.sleep(100);
        }

        assertThatObject(testSubject.get("test1")).isNull();

        // the reference is gone, but it may take a 'while' for the reference to be queued
        for (int i = 0; i < 5 && !expiredEntries.contains("test1"); i++) {
            testSubject.get("test1");
            Thread.sleep(100);
        }

        assertThat(expiredEntries).containsExactly("test1");
    }

    @Test
    void entryListenerNotifiedOfCreationUpdateAndDeletion() {
        Object value = new Object();
        Object value2 = new Object();
        testSubject.put("test1", value);
        verify(mockListener).onEntryCreated("test1", value);

        testSubject.put("test1", value2);
        verify(mockListener).onEntryUpdated("test1", value2);

        testSubject.get("test1");
        verify(mockListener).onEntryRead("test1", value2);

        testSubject.remove("test1");
        verify(mockListener).onEntryRemoved("test1");

        assertThatObject(testSubject.get("test1")).isNull();
        verifyNoMoreInteractions(mockListener);
    }

    @Test
    void computeIfAbsentDoesNotOverwriteValueStoredWhileSupplierRuns() {
        Object concurrentValue = new Object();

        Object result = testSubject.computeIfAbsent("test1", () -> {
            testSubject.put("test1", concurrentValue);
            return new Object();
        });

        assertThat(result).isSameAs(concurrentValue);
        assertThatObject(testSubject.get("test1")).isSameAs(concurrentValue);
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenKeyIsNullOnGet() {
        assertThatThrownBy(() -> testSubject.get(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenKeyIsNullOnContainsKey() {
        assertThatThrownBy(() -> testSubject.containsKey(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeAllRemovesAllEntries() {
        testSubject.put("one", new Object());
        testSubject.put("two", new Object());
        testSubject.put("three", new Object());
        testSubject.put("four", new Object());

        assertThat(testSubject.containsKey("one")).isTrue();
        assertThat(testSubject.containsKey("two")).isTrue();
        assertThat(testSubject.containsKey("three")).isTrue();
        assertThat(testSubject.containsKey("four")).isTrue();

        testSubject.removeAll();

        assertThat(testSubject.containsKey("one")).isFalse();
        assertThat(testSubject.containsKey("two")).isFalse();
        assertThat(testSubject.containsKey("three")).isFalse();
        assertThat(testSubject.containsKey("four")).isFalse();
    }

    @Test
    void computeIfPresentDoesNotUpdateNonExistingEntry() {
        AtomicBoolean invoked = new AtomicBoolean(false);

        testSubject.computeIfPresent("some-key", v -> {
            invoked.set(true);
            return v;
        });

        assertThat(invoked.get()).isFalse();
    }

    @Test
    void computeIfPresentUpdatesExistingEntry() {
        String testKey = "some-key";
        testSubject.put(testKey, new Object());

        AtomicBoolean invoked = new AtomicBoolean(false);

        testSubject.computeIfPresent(testKey, v -> {
            invoked.set(true);
            return v;
        });

        assertThat(invoked.get()).isTrue();
    }
}
