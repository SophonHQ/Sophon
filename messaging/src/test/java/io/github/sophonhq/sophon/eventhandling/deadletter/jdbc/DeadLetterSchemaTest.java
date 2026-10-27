/*
 * Copyright (c) 2010-2023. Axon Framework
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

package io.github.sophonhq.sophon.eventhandling.deadletter.jdbc;

import io.github.sophonhq.sophon.common.SophonConfigurationException;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link DeadLetterSchema}.
 *
 * @author Steven van Beelen
 */
class DeadLetterSchemaTest {

    public static final String TEST_COLUMN_NAME = "some-name";

    @Test
    void buildWithDeadLetterTableReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .deadLetterTable(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.deadLetterTable());
    }

    @Test
    void buildWithNullDeadLetterTableThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.deadLetterTable(null));
    }

    @Test
    void buildWithEmptyDeadLetterTableThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.deadLetterTable(""));
    }

    @Test
    void buildWithDeadLetterIdentifierColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .deadLetterIdentifierColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.deadLetterIdentifierColumn());
    }

    @Test
    void buildWithNullDeadLetterIdentifierColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.deadLetterIdentifierColumn(null));
    }

    @Test
    void buildWithEmptyDeadLetterIdentifierColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.deadLetterIdentifierColumn(""));
    }

    @Test
    void buildWithProcessingGroupColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .processingGroupColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.processingGroupColumn());
    }

    @Test
    void buildWithNullProcessingGroupColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.processingGroupColumn(null));
    }

    @Test
    void buildWithEmptyProcessingGroupColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.processingGroupColumn(""));
    }

    @Test
    void buildWithSequenceIdentifierColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .sequenceIdentifierColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.sequenceIdentifierColumn());
    }

    @Test
    void buildWithNullSequenceIdentifierColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.sequenceIdentifierColumn(null));
    }

    @Test
    void buildWithEmptySequenceIdentifierColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.sequenceIdentifierColumn(""));
    }

    @Test
    void buildWithSequenceIndexColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .sequenceIndexColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.sequenceIndexColumn());
    }

    @Test
    void buildWithNullSequenceIndexColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.sequenceIndexColumn(null));
    }

    @Test
    void buildWithEmptySequenceIndexColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.sequenceIndexColumn(""));
    }

    @Test
    void buildWithMessageTypeColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .messageTypeColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.messageTypeColumn());
    }

    @Test
    void buildWithNullMessageTypeColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.messageTypeColumn(null));
    }

    @Test
    void buildWithEmptyMessageTypeColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.messageTypeColumn(""));
    }

    @Test
    void buildWithEventIdentifierColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .eventIdentifierColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.eventIdentifierColumn());
    }

    @Test
    void buildWithNullEventIdentifierColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.eventIdentifierColumn(null));
    }

    @Test
    void buildWithEmptyEventIdentifierColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.eventIdentifierColumn(""));
    }

    @Test
    void buildWithTimeStampColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .timestampColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.timestampColumn());
    }

    @Test
    void buildWithNullTimeStampColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.timestampColumn(null));
    }

    @Test
    void buildWithEmptyTimeStampColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.timestampColumn(""));
    }

    @Test
    void buildWithPayloadTypeColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .payloadTypeColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.payloadTypeColumn());
    }

    @Test
    void buildWithNullPayloadTypeColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.payloadTypeColumn(null));
    }

    @Test
    void buildWithEmptyPayloadTypeColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.payloadTypeColumn(""));
    }

    @Test
    void buildWithPayloadRevisionColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .payloadRevisionColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.payloadRevisionColumn());
    }

    @Test
    void buildWithNullPayloadRevisionColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.payloadRevisionColumn(null));
    }

    @Test
    void buildWithEmptyPayloadRevisionColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.payloadRevisionColumn(""));
    }

    @Test
    void buildWithPayloadColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .payloadColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.payloadColumn());
    }

    @Test
    void buildWithNullPayloadColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.payloadColumn(null));
    }

    @Test
    void buildWithEmptyPayloadColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.payloadColumn(""));
    }

    @Test
    void buildWithMetaDataColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .metaDataColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.metaDataColumn());
    }

    @Test
    void buildWithNullMetaDataColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.metaDataColumn(null));
    }

    @Test
    void buildWithEmptyMetaDataColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.metaDataColumn(""));
    }

    @Test
    void buildWithAggregateTypeColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .aggregateTypeColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.aggregateTypeColumn());
    }

    @Test
    void buildWithNullAggregateTypeColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.aggregateTypeColumn(null));
    }

    @Test
    void buildWithEmptyAggregateTypeColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.aggregateTypeColumn(""));
    }

    @Test
    void buildWithAggregateIdentifierColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .aggregateIdentifierColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.aggregateIdentifierColumn());
    }

    @Test
    void buildWithNullAggregateIdentifierColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.aggregateIdentifierColumn(null));
    }

    @Test
    void buildWithEmptyAggregateIdentifierColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.aggregateIdentifierColumn(""));
    }

    @Test
    void buildWithSequenceNumberColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .sequenceNumberColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.sequenceNumberColumn());
    }

    @Test
    void buildWithNullSequenceNumberColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.sequenceNumberColumn(null));
    }

    @Test
    void buildWithEmptySequenceNumberColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.sequenceNumberColumn(""));
    }

    @Test
    void buildWithTokenTypeColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .tokenTypeColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.tokenTypeColumn());
    }

    @Test
    void buildWithNullTokenTypeColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.tokenTypeColumn(null));
    }

    @Test
    void buildWithEmptyTokenTypeColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.tokenTypeColumn(""));
    }

    @Test
    void buildWithTokenColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .tokenColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.tokenColumn());
    }

    @Test
    void buildWithNullTokenColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.tokenColumn(null));
    }

    @Test
    void buildWithEmptyTokenColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.tokenColumn(""));
    }

    @Test
    void buildWithEnqueuedAtColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .enqueuedAtColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.enqueuedAtColumn());
    }

    @Test
    void buildWithNullEnqueuedAtColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.enqueuedAtColumn(null));
    }

    @Test
    void buildWithEmptyEnqueuedAtColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.enqueuedAtColumn(""));
    }

    @Test
    void buildWithLastTouchedColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .lastTouchedColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.lastTouchedColumn());
    }

    @Test
    void buildWithNullLastTouchedColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.lastTouchedColumn(null));
    }

    @Test
    void buildWithEmptyLastTouchedColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.lastTouchedColumn(""));
    }

    @Test
    void buildWithProcessingStartedColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .processingStartedColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.processingStartedColumn());
    }

    @Test
    void buildWithNullProcessingStartedColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.processingStartedColumn(null));
    }

    @Test
    void buildWithEmptyProcessingStartedColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.processingStartedColumn(""));
    }

    @Test
    void buildWithCauseTypeColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .causeTypeColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.causeTypeColumn());
    }

    @Test
    void buildWithNullCauseTypeColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.causeTypeColumn(null));
    }

    @Test
    void buildWithEmptyCauseTypeColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.causeTypeColumn(""));
    }

    @Test
    void buildWithCauseMessageColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .causeMessageColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.causeMessageColumn());
    }

    @Test
    void buildWithNullCauseMessageColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.causeMessageColumn(null));
    }

    @Test
    void buildWithEmptyCauseMessageColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.causeMessageColumn(""));
    }

    @Test
    void buildWithDiagnosticsColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .diagnosticsColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.diagnosticsColumn());
    }

    @Test
    void buildWithNullDiagnosticsColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.diagnosticsColumn(null));
    }

    @Test
    void buildWithEmptyDiagnosticsColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.diagnosticsColumn(""));
    }
}