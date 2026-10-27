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

package io.github.sophonhq.sophon.eventhandling.deadletter.jdbc;

import io.github.sophonhq.sophon.common.SophonConfigurationException;
import io.github.sophonhq.sophon.serialization.TestSerializer;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;

import static io.github.sophonhq.sophon.common.DateTimeUtils.formatInstant;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test class validating the {@link DefaultDeadLetterStatementFactory}.
 *
 * @author Steven van Beelen
 */
class DefaultDeadLetterStatementFactoryTest {

    @Test
    void buildWithNullSchemaThrowsSophonConfigurationException() {
        DefaultDeadLetterStatementFactory.Builder<?> testBuilder = DefaultDeadLetterStatementFactory.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.schema(null));
    }

    @Test
    void buildWithNullGenericSerializerThrowsSophonConfigurationException() {
        DefaultDeadLetterStatementFactory.Builder<?> testBuilder = DefaultDeadLetterStatementFactory.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.genericSerializer(null));
    }

    @Test
    void buildWithNullEventSerializerThrowsSophonConfigurationException() {
        DefaultDeadLetterStatementFactory.Builder<?> testBuilder = DefaultDeadLetterStatementFactory.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.eventSerializer(null));
    }

    @Test
    void buildWithoutTheGenericSerializerThrowsSophonConfigurationException() {
        DefaultDeadLetterStatementFactory.Builder<?> testBuilder =
                DefaultDeadLetterStatementFactory.builder()
                                                 .eventSerializer(TestSerializer.JACKSON.getSerializer());

        assertThrows(SophonConfigurationException.class, testBuilder::build);
    }

    @Test
    void buildWithoutTheEventSerializerThrowsSophonConfigurationException() {
        DefaultDeadLetterStatementFactory.Builder<?> testBuilder =
                DefaultDeadLetterStatementFactory.builder()
                                                 .genericSerializer(TestSerializer.JACKSON.getSerializer());

        assertThrows(SophonConfigurationException.class, testBuilder::build);
    }

    @Test
    void claimableSequencesStatementUsesSqlOffsetPagingOnOrderedSequenceHeads() throws Exception {
        DefaultDeadLetterStatementFactory<?> testSubject = DefaultDeadLetterStatementFactory.builder()
                .genericSerializer(TestSerializer.JACKSON.getSerializer())
                .eventSerializer(TestSerializer.JACKSON.getSerializer())
                .build();
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString(), eq(ResultSet.TYPE_FORWARD_ONLY), eq(ResultSet.CONCUR_READ_ONLY)))
                .thenReturn(statement);

        Instant processingStartedLimit = Instant.parse("2026-05-08T00:00:00Z");
        testSubject.claimableSequencesStatement(connection, "processing-group", processingStartedLimit, 20, 10);

        String expectedSql = "SELECT * "
                + "FROM DeadLetterEntry dl "
                + "WHERE dl.processingGroup=? "
                + "AND dl.sequenceIndex="
                + "("
                + "SELECT MIN(dl2.sequenceIndex) "
                + "FROM DeadLetterEntry dl2 "
                + "WHERE dl2.processingGroup=dl.processingGroup "
                + "AND dl2.sequenceIdentifier=dl.sequenceIdentifier"
                + ") "
                + "AND ("
                + "dl.processingStarted IS NULL "
                + "OR dl.processingStarted<?"
                + ") "
                + "ORDER BY dl.lastTouched "
                + "ASC "
                + "LIMIT ? "
                + "OFFSET ?";

        verify(connection).prepareStatement(expectedSql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        verify(statement).setString(1, "processing-group");
        verify(statement).setString(2, formatInstant(processingStartedLimit));
        verify(statement).setInt(3, 10);
        verify(statement).setInt(4, 20);
    }
}
