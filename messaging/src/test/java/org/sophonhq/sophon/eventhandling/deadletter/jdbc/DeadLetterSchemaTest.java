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

package org.sophonhq.sophon.eventhandling.deadletter.jdbc;

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.common.SophonConfigurationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        assertThat(result.deadLetterTable()).isEqualTo(TEST_COLUMN_NAME);
    }

    @Test
    void buildWithNullDeadLetterTableThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.deadLetterTable(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptyDeadLetterTableThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.deadLetterTable("")).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithDeadLetterIdentifierColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .deadLetterIdentifierColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertThat(result.deadLetterIdentifierColumn()).isEqualTo(TEST_COLUMN_NAME);
    }

    @Test
    void buildWithNullDeadLetterIdentifierColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.deadLetterIdentifierColumn(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptyDeadLetterIdentifierColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.deadLetterIdentifierColumn(""))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithProcessingGroupColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .processingGroupColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertThat(result.processingGroupColumn()).isEqualTo(TEST_COLUMN_NAME);
    }

    @Test
    void buildWithNullProcessingGroupColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.processingGroupColumn(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptyProcessingGroupColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.processingGroupColumn(""))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithSequenceIdentifierColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .sequenceIdentifierColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertThat(result.sequenceIdentifierColumn()).isEqualTo(TEST_COLUMN_NAME);
    }

    @Test
    void buildWithNullSequenceIdentifierColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.sequenceIdentifierColumn(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptySequenceIdentifierColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.sequenceIdentifierColumn(""))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithSequenceIndexColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .sequenceIndexColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertThat(result.sequenceIndexColumn()).isEqualTo(TEST_COLUMN_NAME);
    }

    @Test
    void buildWithNullSequenceIndexColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.sequenceIndexColumn(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptySequenceIndexColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.sequenceIndexColumn(""))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithMessageTypeColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .messageTypeColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertThat(result.messageTypeColumn()).isEqualTo(TEST_COLUMN_NAME);
    }

    @Test
    void buildWithNullMessageTypeColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.messageTypeColumn(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptyMessageTypeColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.messageTypeColumn(""))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEventIdentifierColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .eventIdentifierColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertThat(result.eventIdentifierColumn()).isEqualTo(TEST_COLUMN_NAME);
    }

    @Test
    void buildWithNullEventIdentifierColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.eventIdentifierColumn(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptyEventIdentifierColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.eventIdentifierColumn(""))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithTimeStampColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .timestampColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertThat(result.timestampColumn()).isEqualTo(TEST_COLUMN_NAME);
    }

    @Test
    void buildWithNullTimeStampColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.timestampColumn(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptyTimeStampColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.timestampColumn("")).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithPayloadTypeColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .payloadTypeColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertThat(result.payloadTypeColumn()).isEqualTo(TEST_COLUMN_NAME);
    }

    @Test
    void buildWithNullPayloadTypeColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.payloadTypeColumn(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptyPayloadTypeColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.payloadTypeColumn(""))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithPayloadRevisionColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .payloadRevisionColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertThat(result.payloadRevisionColumn()).isEqualTo(TEST_COLUMN_NAME);
    }

    @Test
    void buildWithNullPayloadRevisionColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.payloadRevisionColumn(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptyPayloadRevisionColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.payloadRevisionColumn(""))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithPayloadColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .payloadColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertThat(result.payloadColumn()).isEqualTo(TEST_COLUMN_NAME);
    }

    @Test
    void buildWithNullPayloadColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.payloadColumn(null)).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptyPayloadColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.payloadColumn("")).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithMetaDataColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .metaDataColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertThat(result.metaDataColumn()).isEqualTo(TEST_COLUMN_NAME);
    }

    @Test
    void buildWithNullMetaDataColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.metaDataColumn(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptyMetaDataColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.metaDataColumn("")).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithAggregateTypeColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .aggregateTypeColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertThat(result.aggregateTypeColumn()).isEqualTo(TEST_COLUMN_NAME);
    }

    @Test
    void buildWithNullAggregateTypeColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.aggregateTypeColumn(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptyAggregateTypeColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.aggregateTypeColumn(""))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithAggregateIdentifierColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .aggregateIdentifierColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertThat(result.aggregateIdentifierColumn()).isEqualTo(TEST_COLUMN_NAME);
    }

    @Test
    void buildWithNullAggregateIdentifierColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.aggregateIdentifierColumn(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptyAggregateIdentifierColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.aggregateIdentifierColumn(""))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithSequenceNumberColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .sequenceNumberColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertThat(result.sequenceNumberColumn()).isEqualTo(TEST_COLUMN_NAME);
    }

    @Test
    void buildWithNullSequenceNumberColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.sequenceNumberColumn(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptySequenceNumberColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.sequenceNumberColumn(""))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithTokenTypeColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .tokenTypeColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertThat(result.tokenTypeColumn()).isEqualTo(TEST_COLUMN_NAME);
    }

    @Test
    void buildWithNullTokenTypeColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.tokenTypeColumn(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptyTokenTypeColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.tokenTypeColumn("")).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithTokenColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .tokenColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertThat(result.tokenColumn()).isEqualTo(TEST_COLUMN_NAME);
    }

    @Test
    void buildWithNullTokenColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.tokenColumn(null)).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptyTokenColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.tokenColumn("")).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEnqueuedAtColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .enqueuedAtColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertThat(result.enqueuedAtColumn()).isEqualTo(TEST_COLUMN_NAME);
    }

    @Test
    void buildWithNullEnqueuedAtColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.enqueuedAtColumn(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptyEnqueuedAtColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.enqueuedAtColumn(""))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithLastTouchedColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .lastTouchedColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertThat(result.lastTouchedColumn()).isEqualTo(TEST_COLUMN_NAME);
    }

    @Test
    void buildWithNullLastTouchedColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.lastTouchedColumn(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptyLastTouchedColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.lastTouchedColumn(""))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithProcessingStartedColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .processingStartedColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertThat(result.processingStartedColumn()).isEqualTo(TEST_COLUMN_NAME);
    }

    @Test
    void buildWithNullProcessingStartedColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.processingStartedColumn(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptyProcessingStartedColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.processingStartedColumn(""))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithCauseTypeColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .causeTypeColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertThat(result.causeTypeColumn()).isEqualTo(TEST_COLUMN_NAME);
    }

    @Test
    void buildWithNullCauseTypeColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.causeTypeColumn(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptyCauseTypeColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.causeTypeColumn("")).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithCauseMessageColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .causeMessageColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertThat(result.causeMessageColumn()).isEqualTo(TEST_COLUMN_NAME);
    }

    @Test
    void buildWithNullCauseMessageColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.causeMessageColumn(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptyCauseMessageColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.causeMessageColumn(""))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithDiagnosticsColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .diagnosticsColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertThat(result.diagnosticsColumn()).isEqualTo(TEST_COLUMN_NAME);
    }

    @Test
    void buildWithNullDiagnosticsColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.diagnosticsColumn(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptyDiagnosticsColumnThrowsSophonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThatThrownBy(() -> testBuilder.diagnosticsColumn(""))
                .isInstanceOf(SophonConfigurationException.class);
    }
}