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

package io.github.sophonhq.sophon.test.saga;

import io.github.sophonhq.sophon.commandhandling.CommandMessage;
import io.github.sophonhq.sophon.test.SophonAssertionError;
import io.github.sophonhq.sophon.test.matchers.FieldFilter;
import io.github.sophonhq.sophon.test.matchers.Matchers;
import io.github.sophonhq.sophon.test.utils.RecordingCommandBus;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static io.github.sophonhq.sophon.test.saga.DescriptionUtils.describe;

/**
 * Helper class for validation of dispatched commands.
 *
 * @author Allard Buijze
 * @since axon-1.1
 */
public class CommandValidator {

    private final RecordingCommandBus commandBus;
    private final FieldFilter fieldFilter;

    /**
     * Creates a validator which monitors the given {@code commandBus}.
     *
     * @param commandBus  the command bus to monitor
     * @param fieldFilter the filter describing the Fields to include in a comparison
     */
    public CommandValidator(RecordingCommandBus commandBus, FieldFilter fieldFilter) {
        this.commandBus = commandBus;
        this.fieldFilter = fieldFilter;
    }

    /**
     * Starts recording commands on the command bus.
     */
    public void startRecording() {
        commandBus.clearCommands();
    }

    /**
     * Assert that the given commands have been dispatched in the exact sequence provided.
     *
     * @param expected The commands expected to have been published on the bus
     */
    public void assertDispatchedEqualTo(Object... expected) {
        List<CommandMessage<?>> actual = commandBus.getDispatchedCommands();
        if (actual.size() != expected.length) {
            throw new SophonAssertionError(
                    "Got wrong number of commands dispatched.\n"
                            + "Expected <" + expected.length + ">,\n but got <" + actual.size() + ">."
            );
        }
        Iterator<CommandMessage<?>> actualIterator = actual.iterator();
        Iterator<Object> expectedIterator = Arrays.asList(expected).iterator();

        int counter = 0;
        while (actualIterator.hasNext()) {
            CommandMessage<?> actualItem = actualIterator.next();
            Object expectedItem = expectedIterator.next();
            if (expectedItem instanceof CommandMessage) {
                CommandMessage<?> expectedMessage = (CommandMessage<?>) expectedItem;
                if (!expectedMessage.getPayloadType().equals(actualItem.getPayloadType())) {
                    throw new SophonAssertionError(
                            "Unexpected payload type of command at position " + counter + " (0-based).\n"
                                    + "Expected <" + expectedMessage.getPayloadType() + ">,\n"
                                    + " but got <" + actualItem.getPayloadType() + ">."
                    );
                }
                assertCommandEquality(counter, expectedMessage.getPayload(), actualItem.getPayload());
                if (!expectedMessage.getMetaData().equals(actualItem.getMetaData())) {
                    throw new SophonAssertionError(
                            "Unexpected Meta Data of command at position " + counter + " (0-based).\n"
                                    + "Expected <" + expectedMessage.getMetaData() + ">,\n"
                                    + " but got <" + actualItem.getMetaData() + ">."
                    );
                }
            } else {
                assertCommandEquality(counter, expectedItem, actualItem.getPayload());
            }
            counter++;
        }
    }

    /**
     * Assert that commands matching the given {@code matcher} has been dispatched on the command bus.
     *
     * @param matcher The matcher validating the actual commands
     */
    public void assertDispatchedMatching(Matcher<?> matcher) {
        if (!matcher.matches(commandBus.getDispatchedCommands())) {
            Description expectedDescription = new StringDescription();
            Description actualDescription = new StringDescription();
            matcher.describeTo(expectedDescription);
            describe(commandBus.getDispatchedCommands(), actualDescription);
            throw new SophonAssertionError(
                    "Incorrect dispatched command.\n"
                            + "Expected <" + expectedDescription + ">,\n but got <" + actualDescription + ">."
            );
        }
    }

    private void assertCommandEquality(int commandIndex, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            if (!expected.getClass().equals(actual.getClass())) {
                throw new SophonAssertionError(
                        "Wrong command type at index " + commandIndex + " (0-based).\n"
                                + "Expected <" + expected.getClass().getSimpleName() + ">,\n"
                                + " but got <" + actual.getClass().getSimpleName() + ">."
                );
            }
            Matcher<Object> matcher = Matchers.deepEquals(expected, fieldFilter);
            if (!matcher.matches(actual)) {
                throw new SophonAssertionError(
                        "Unexpected command at index " + commandIndex + " (0-based).\n"
                                + "Expected <" + expected + ">,\n but got <" + actual + ">."
                );
            }
        }
    }
}
