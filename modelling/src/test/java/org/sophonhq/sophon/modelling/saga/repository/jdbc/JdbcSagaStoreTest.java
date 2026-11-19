/*
 * Copyright (c) 2010-2021. Axon Framework
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

package org.sophonhq.sophon.modelling.saga.repository.jdbc;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.modelling.saga.AssociationValue;
import org.sophonhq.sophon.modelling.saga.AssociationValues;
import org.sophonhq.sophon.modelling.saga.AssociationValuesImpl;
import org.sophonhq.sophon.modelling.saga.repository.SagaStore;
import org.sophonhq.sophon.modelling.saga.repository.StubSaga;
import org.sophonhq.sophon.modelling.utils.TestSerializer;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link JdbcSagaStore}.
 *
 * @author Kristian Rosenvold
 */
class JdbcSagaStoreTest {

    private Connection connection;

    private JdbcSagaStore testSubject;

    private JdbcDataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        connection = dataSource.getConnection();
        testSubject = JdbcSagaStore.builder()
                                   .dataSource(dataSource)
                                   .sqlSchema(new GenericSagaSqlSchema())
                                   .serializer(TestSerializer.xStreamSerializer())
                                   .build();
        testSubject.createSchema();
    }

    @AfterEach
    void shutDown() throws SQLException {
        connection.createStatement().execute("SHUTDOWN");
        connection.close();
    }

    @Test
    void insertUpdateAndLoadSaga() {
        StubSaga saga = new StubSaga();
        Set<AssociationValue> associationValues = singleton(new AssociationValue("key", "value"));
        testSubject.insertSaga(StubSaga.class, "123", saga, associationValues);
        testSubject.updateSaga(StubSaga.class, "123", saga, new AssociationValuesImpl(associationValues));

        SagaStore.Entry<StubSaga> entry = testSubject.loadSaga(StubSaga.class, "123");
        assertThat(entry).isNotNull();
        assertThat(entry.saga()).isNotNull();
        assertThat(entry.associationValues()).isEqualTo(associationValues);
    }

    @Test
    void loadSaga_NotFound() {
        assertThat(testSubject.loadSaga(StubSaga.class, "123456")).isNull();
    }

    @Test
    void loadSagaByAssociationValue() {
        AssociationValues associationsValues =
                new AssociationValuesImpl(singleton(new AssociationValue("key", "value")));
        testSubject.insertSaga(StubSaga.class, "123", new StubSaga(), associationsValues.asSet());
        testSubject.insertSaga(StubSaga.class, "456", new StubSaga(), singleton(new AssociationValue("key", "value2")));

        associationsValues.add(new AssociationValue("key", "value2"));
        testSubject.updateSaga(StubSaga.class, "123", new StubSaga(), associationsValues);
        associationsValues.commit();

        associationsValues.remove(new AssociationValue("key", "value2"));
        testSubject.updateSaga(StubSaga.class, "123", new StubSaga(), associationsValues);
        associationsValues.commit();

        Set<String> actual = testSubject.findSagas(StubSaga.class, new AssociationValue("key", "value"));
        assertThat(actual).isEqualTo(singleton("123"));
    }

    @Test
    void updateSagaWhenDeleted() {
        AssociationValues associationsValues = new AssociationValuesImpl(singleton(new AssociationValue("key", "value")));
        testSubject.updateSaga(StubSaga.class, "123456", new StubSaga(), associationsValues);
        assertThat(testSubject.loadSaga(StubSaga.class, "123456")).isNull();
    }
}
