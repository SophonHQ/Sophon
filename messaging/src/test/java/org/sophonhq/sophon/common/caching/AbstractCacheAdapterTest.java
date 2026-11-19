package org.sophonhq.sophon.common.caching;

import org.junit.jupiter.api.*;
import org.sophonhq.sophon.common.Registration;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatObject;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

/**
 * Test class validating {@link AbstractCacheAdapter} implementations.
 *
 * @author Gerard Klijs
 */
public abstract class AbstractCacheAdapterTest {

    private TestSubjectWrapper testSubjectWrapper;
    @SuppressWarnings("rawtypes")
    private AbstractCacheAdapter testSubject;
    private org.sophonhq.sophon.common.caching.Cache.EntryListener mockListener;
    private Registration registration;

    abstract TestSubjectWrapper getTestSubjectWrapper();

    @BeforeEach
    void setUp() {
        testSubjectWrapper = getTestSubjectWrapper();
        testSubject = testSubjectWrapper.testSubject;
        mockListener = mock(org.sophonhq.sophon.common.caching.Cache.EntryListener.class);
        registration = testSubject.registerCacheEntryListener(mockListener);
    }

    @AfterEach
    void tearDown() {
        reset(mockListener);
        testSubjectWrapper.closeFunction.run();
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

    @Test
    void putIfAbsentWorksCorrectly() {
        Object value = new Object();
        Object value2 = new Object();

        testSubject.putIfAbsent("test1", value);
        assertThatObject(testSubject.get("test1")).isEqualTo(value);

        testSubject.putIfAbsent("test1", value2);
        assertThatObject(testSubject.get("test1")).isEqualTo(value);
    }

    @Test
    void computeIfAbsentDoesNotOverwriteValueStoredWhileSupplierRuns() {
        Object concurrentValue = new Object();

        Object result = testSubject.computeIfAbsent("test1", () -> {
            testSubject.put("test1", concurrentValue);
            return new Object();
        });

        assertThat(result).isEqualTo(concurrentValue);
        assertThatObject(testSubject.get("test1")).isEqualTo(concurrentValue);
    }


    @Test
    void entryListenerNotifiedOfCreationUpdateAndDeletion() {
        Object value = new Object();
        Object value2 = new Object();
        testSubject.put("test1", value);
        await().atMost(Duration.ofMillis(300L)).untilAsserted(
                () -> verify(mockListener).onEntryCreated("test1", value));

        testSubject.put("test1", value2);
        await().atMost(Duration.ofMillis(300L)).untilAsserted(
                () -> verify(mockListener).onEntryUpdated("test1", value2));

        testSubject.remove("test1");
        await().atMost(Duration.ofMillis(300L)).untilAsserted(
                () -> verify(mockListener).onEntryRemoved("test1"));

        assertThatObject(testSubject.get("test1")).isNull();
        verifyNoMoreInteractions(mockListener);
    }

    @Test
    void entryListenerNotifiedOfExpired() {
        Object value = new Object();

        testSubject.put("test1", value);
        await().atMost(Duration.ofMillis(300L)).untilAsserted(
                () -> verify(mockListener).onEntryCreated("test1", value));
        await().atMost(Duration.ofSeconds(1L)).untilAsserted(() -> {
            testSubject.get("test1");
            verify(mockListener).onEntryExpired("test1");
        });
        verifyNoMoreInteractions(mockListener);
    }

    @Test
    void entryListenerCanBeDeregistered() {
        Object value = new Object();
        Object value2 = new Object();
        testSubject.put("test1", value);
        await().atMost(Duration.ofMillis(300L)).untilAsserted(
                () -> verify(mockListener).onEntryCreated("test1", value));
        registration.cancel();

        testSubject.put("test2", value2);
        verifyNoMoreInteractions(mockListener);
    }

    static class TestSubjectWrapper {

        @SuppressWarnings("rawtypes")
        private final AbstractCacheAdapter testSubject;
        private final Runnable closeFunction;

        @SuppressWarnings("rawtypes")
        TestSubjectWrapper(AbstractCacheAdapter testSubject, Runnable closeFunction) {
            this.testSubject = testSubject;
            this.closeFunction = closeFunction;
        }
    }
}
