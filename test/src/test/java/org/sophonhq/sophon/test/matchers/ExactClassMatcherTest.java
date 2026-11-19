/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.sophonhq.sophon.test.matchers;

import org.hamcrest.Description;
import org.hamcrest.StringDescription;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.common.SophonConfigurationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test class validating the {@link ExactClassMatcher}.
 *
 * @author Yoann CAPLAIN
 */
public class ExactClassMatcherTest {

    @Test
    void matchesReturnsFalseForNoneMatchingTypes() {
        Description description = new StringDescription();

        ExactClassMatcher<SomeBaseEvent> testSubject = new ExactClassMatcher<>(SomeBaseEvent.class);

        boolean result = testSubject.matches(new SomeEvent());

        assertThat(result).isFalse();
        testSubject.describeTo(description);
        assertThat(description.toString()).contains(" does not match with the actual type.");
    }

    @Test
    void matchesReturnsFalseForSubclass() {
        assertThat(new ExactClassMatcher<>(SomeBaseEvent.class).matches(new SomeSubclassEvent())).isFalse();
    }

    @Test
    void matchesForSameClass() {
        assertThat(new ExactClassMatcher<>(SomeBaseEvent.class).matches(new SomeBaseEvent())).isTrue();
    }

    @Test
    void matchesIsNullSafe() {
        assertThat(new ExactClassMatcher<>(String.class).matches(null)).isFalse();
        assertThatThrownBy(() -> new ExactClassMatcher<>(null).matches("foo"))
                .isInstanceOf(SophonConfigurationException.class);
    }

    private static class SomeBaseEvent {

    }

    private static class SomeSubclassEvent extends SomeBaseEvent {

    }

    private static class SomeEvent {

    }
}
