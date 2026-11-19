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

package org.sophonhq.sophon.eventhandling.tokenstore.jdbc;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.sophonhq.sophon.common.transaction.Transaction;
import org.sophonhq.sophon.common.transaction.TransactionManager;
import org.sophonhq.sophon.eventhandling.GlobalSequenceTrackingToken;
import org.sophonhq.sophon.eventhandling.Segment;
import org.sophonhq.sophon.eventhandling.TrackingToken;
import org.sophonhq.sophon.eventhandling.tokenstore.AbstractTokenEntry;
import org.sophonhq.sophon.eventhandling.tokenstore.ConfigToken;
import org.sophonhq.sophon.eventhandling.tokenstore.UnableToClaimTokenException;
import org.sophonhq.sophon.serialization.TestSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.inject.Named;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.*;

@ContextConfiguration
@ExtendWith(SpringExtension.class)
class JdbcTokenStoreTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    @Named("tokenStore")
    private JdbcTokenStore tokenStore;

    @Autowired
    @Named("concurrentTokenStore")
    private JdbcTokenStore concurrentTokenStore;

    @Autowired
    @Named("stealingTokenStore")
    private JdbcTokenStore stealingTokenStore;

    @Autowired
    private TransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        transactionManager.executeInTransaction(() -> {
            try {
                dataSource.getConnection().prepareStatement("DROP TABLE IF EXISTS TokenEntry").executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to drop or create token table", e);
            }
            tokenStore.createSchema(GenericTokenTableFactory.INSTANCE);
        });
    }

    @AfterEach
    void tearDown() {
        AbstractTokenEntry.clock = Clock.systemUTC();
    }

    @Test
    void claimAndUpdateToken() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("test", 1));

        transactionManager.executeInTransaction(() -> assertThat(tokenStore.fetchToken("test", 0)).isNull());
        TrackingToken token = new GlobalSequenceTrackingToken(1L);
        transactionManager.executeInTransaction(() -> tokenStore.storeToken(token, "test", 0));
        transactionManager.executeInTransaction(() -> assertThat(tokenStore.fetchToken("test", 0)).isEqualTo(token));
    }

    @Transactional
    @Test
    void updateAndLoadNullToken() {
        tokenStore.initializeTokenSegments("test", 1);
        tokenStore.fetchToken("test", 0);

        tokenStore.storeToken(null, "test", 0);

        TrackingToken token = tokenStore.fetchToken("test", 0);
        assertThat(token).isNull();
    }

    @Transactional
    @Test
    void fetchTokenBySegment() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("test", 2));
        Segment segmentToFetch = Segment.computeSegment(1, 0, 1);

        transactionManager.executeInTransaction(
                () -> assertThat(tokenStore.fetchToken("test", segmentToFetch)).isNull()
        );
    }

    @Transactional
    @Test
    void fetchTokenBySegmentSegment0() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("test", 1));
        Segment segmentToFetch = Segment.computeSegment(0, 0);

        transactionManager.executeInTransaction(
                () -> assertThat(tokenStore.fetchToken("test", segmentToFetch)).isNull()
        );
    }

    @Transactional
    @Test
    void fetchTokenBySegmentFailsDuringMerge() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("test", 1));
        //Create a segment as if there would be two segments in total. This simulates that these two segments have been merged into one.
        Segment segmentToFetch = Segment.computeSegment(1, 0, 1);

        assertThatThrownBy(() -> transactionManager.executeInTransaction(
                () -> tokenStore.fetchToken("test", segmentToFetch)
        )).isInstanceOf(UnableToClaimTokenException.class);
    }

    @Transactional
    @Test
    void fetchTokenBySegmentFailsDuringMergeSegment0() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("test", 1));
        Segment segmentToFetch = Segment.computeSegment(0, 0, 1);

        assertThatThrownBy(() -> transactionManager.executeInTransaction(
                () -> tokenStore.fetchToken("test", segmentToFetch)
        )).isInstanceOf(UnableToClaimTokenException.class);
    }

    @Transactional
    @Test
    void fetchTokenBySegmentFailsDuringSplit() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("test", 4));
        //Create a segment as if there would be only two segments in total. This simulates that the segments have been split into 4 segments.
        Segment segmentToFetch = Segment.computeSegment(1, 0, 1);

        assertThatThrownBy(() -> transactionManager.executeInTransaction(
                () -> tokenStore.fetchToken("test", segmentToFetch)
        )).isInstanceOf(UnableToClaimTokenException.class);
    }

    @Transactional
    @Test
    void fetchTokenBySegmentFailsDuringSplitSegment0() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("test", 2));
        Segment segmentToFetch = Segment.computeSegment(0, 0);

        assertThatThrownBy(() -> transactionManager.executeInTransaction(
                () -> tokenStore.fetchToken("test", segmentToFetch)
        )).isInstanceOf(UnableToClaimTokenException.class);
    }

    @Transactional
    @Test
    void initializeTokens() {
        tokenStore.initializeTokenSegments("test1", 7);

        int[] actual = tokenStore.fetchSegments("test1");
        Arrays.sort(actual);
        assertThat(actual).containsExactly(0, 1, 2, 3, 4, 5, 6);
    }

    @SuppressWarnings("Duplicates")
    @Transactional
    @Test
    void initializeTokensAtGivenPosition() {
        tokenStore.initializeTokenSegments("test1", 7, new GlobalSequenceTrackingToken(10));

        int[] actual = tokenStore.fetchSegments("test1");
        Arrays.sort(actual);
        assertThat(actual).containsExactly(0, 1, 2, 3, 4, 5, 6);

        for (int segment : actual) {
            assertThat(tokenStore.fetchToken("test1", segment)).isEqualTo(new GlobalSequenceTrackingToken(10));
        }
    }

    @Transactional
    @Test
    void initializeTokensWhileAlreadyPresent() {
        assertThatThrownBy(() -> tokenStore.fetchToken("test1", 1))
                .isInstanceOf(UnableToClaimTokenException.class);
    }

    @Transactional
    @Test
    void querySegments() {
        prepareTokenStore();

        transactionManager.executeInTransaction(() -> {
            final int[] segments = tokenStore.fetchSegments("proc1");
            assertThat(segments).hasSize(2);
        });
        transactionManager.executeInTransaction(() -> {
            final int[] segments = tokenStore.fetchSegments("proc2");
            assertThat(segments).hasSize(2);
        });
        transactionManager.executeInTransaction(() -> {
            final int[] segments = tokenStore.fetchSegments("proc3");
            assertThat(segments).isEmpty();
        });
    }

    @Transactional
    @Test
    void queryAvailableSegments() {
        prepareTokenStore();

        transactionManager.executeInTransaction(() -> {
            final List<Segment> segments = concurrentTokenStore.fetchAvailableSegments("proc1");
            assertThat(segments).hasSize(0);
            tokenStore.releaseClaim("proc1", 0);
            final List<Segment> segmentsAfterRelease = concurrentTokenStore.fetchAvailableSegments("proc1");
            assertThat(segmentsAfterRelease).hasSize(1);
        });
        transactionManager.executeInTransaction(() -> {
            final List<Segment> segments = concurrentTokenStore.fetchAvailableSegments("proc2");
            assertThat(segments).hasSize(1);
            tokenStore.releaseClaim("proc2", 1);
            final List<Segment> segmentsAfterRelease = concurrentTokenStore.fetchAvailableSegments("proc2");
            assertThat(segmentsAfterRelease).hasSize(2);
        });
        transactionManager.executeInTransaction(() -> {
            final List<Segment> segments = tokenStore.fetchAvailableSegments("proc3");
            assertThat(segments).hasSize(0);
        });
    }

    private void prepareTokenStore() {
        transactionManager.executeInTransaction(() -> {
            tokenStore.initializeTokenSegments("test", 1);
            tokenStore.initializeTokenSegments("proc1", 2);
            tokenStore.initializeTokenSegments("proc2", 2);
        });
        transactionManager.executeInTransaction(() -> assertThat(tokenStore.fetchToken("test", 0)).isNull());

        transactionManager.executeInTransaction(
                () -> tokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "proc1", 0)
        );
        transactionManager.executeInTransaction(
                () -> tokenStore.storeToken(new GlobalSequenceTrackingToken(2L), "proc1", 1)
        );
        transactionManager.executeInTransaction(
                () -> tokenStore.storeToken(new GlobalSequenceTrackingToken(2L), "proc2", 1)
        );
    }


    @Test
    void claimAndUpdateTokenWithoutTransaction() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("test", 1));

        assertThat(tokenStore.fetchToken("test", 0)).isNull();
        TrackingToken token = new GlobalSequenceTrackingToken(1L);
        tokenStore.storeToken(token, "test", 0);
        assertThat(tokenStore.fetchToken("test", 0)).isEqualTo(token);
    }

    @Test
    void claimTokenConcurrently() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("concurrent", 1));

        transactionManager.executeInTransaction(() -> assertThat(tokenStore.fetchToken("concurrent", 0)).isNull());
        try {
            transactionManager.executeInTransaction(() -> concurrentTokenStore.fetchToken("concurrent", 0));
            fail("Expected UnableToClaimTokenException");
        } catch (UnableToClaimTokenException e) {
            // expected
        }
    }

    @Test
    void claimTokenConcurrentlyAfterRelease() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("concurrent", 1));

        transactionManager.executeInTransaction(() -> tokenStore.fetchToken("concurrent", 0));
        transactionManager.executeInTransaction(() -> tokenStore.releaseClaim("concurrent", 0));
        transactionManager.executeInTransaction(
                () -> assertThat(concurrentTokenStore.fetchToken("concurrent", 0)).isNull()
        );
    }

    @Test
    void claimTokenConcurrentlyAfterTimeLimit() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("concurrent", 1));

        transactionManager.executeInTransaction(() -> tokenStore.fetchToken("concurrent", 0));
        AbstractTokenEntry.clock = Clock.offset(Clock.systemUTC(), Duration.ofHours(1));
        transactionManager.executeInTransaction(
                () -> assertThat(concurrentTokenStore.fetchToken("concurrent", 0)).isNull()
        );
    }

    @Test
    void stealToken() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("stealing", 1));

        transactionManager.executeInTransaction(() -> assertThat(tokenStore.fetchToken("stealing", 0)).isNull());
        transactionManager.executeInTransaction(
                () -> assertThat(stealingTokenStore.fetchToken("stealing", 0)).isNull()
        );

        try {
            transactionManager.executeInTransaction(
                    () -> tokenStore.storeToken(new GlobalSequenceTrackingToken(0), "stealing", 0));
            fail("Expected UnableToClaimTokenException");
        } catch (UnableToClaimTokenException e) {
            // expected
        }
        transactionManager.executeInTransaction(() -> tokenStore.releaseClaim("stealing", 0));
        // claim should still be on stealingTokenStore:
        transactionManager.executeInTransaction(
                () -> stealingTokenStore.storeToken(new GlobalSequenceTrackingToken(1), "stealing", 0));
    }

    @Test
    void storeAndLoadAcrossTransactions() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("multi", 1));

        transactionManager.executeInTransaction(() -> {
            tokenStore.fetchToken("multi", 0);
            tokenStore.storeToken(new GlobalSequenceTrackingToken(1), "multi", 0);
        });

        transactionManager.executeInTransaction(() -> {
            TrackingToken actual = tokenStore.fetchToken("multi", 0);
            assertThat(actual).isEqualTo(new GlobalSequenceTrackingToken(1));
            tokenStore.storeToken(new GlobalSequenceTrackingToken(2), "multi", 0);
        });

        transactionManager.executeInTransaction(() -> {
            TrackingToken actual = tokenStore.fetchToken("multi", 0);
            assertThat(actual).isEqualTo(new GlobalSequenceTrackingToken(2));
        });
    }

    @Test
    void claimAndDeleteToken() {
        transactionManager.executeInTransaction(() -> tokenStore.initializeTokenSegments("test1", 2));

        tokenStore.fetchToken("test1", 0);
        tokenStore.fetchToken("test1", 1);

        tokenStore.deleteToken("test1", 1);

        assertThat(tokenStore.fetchSegments("test1")).containsExactly(0);
    }

    @Test
    void deleteUnclaimedTokenFails() {
        assertThatThrownBy(() -> tokenStore.fetchToken("test1", 1))
                .isInstanceOf(UnableToClaimTokenException.class);
    }

    @Transactional
    @Test
    void deleteTokenFailsWhenClaimedByOtherNode() {
        assertThatThrownBy(() -> concurrentTokenStore.fetchToken("test1", 1))
                .isInstanceOf(UnableToClaimTokenException.class);
    }

    @Transactional
    @Test
    void identifierInitializedOnDemand() {
        Optional<String> id1 = tokenStore.retrieveStorageIdentifier();
        assertThat(id1).isPresent();
        Optional<String> id2 = tokenStore.retrieveStorageIdentifier();
        assertThat(id2).isPresent();
        assertThat(id2).hasValue(id1.get());
    }

    @Transactional
    @Test
    void identifierReadIfAvailable() throws SQLException {
        ConfigToken token = new ConfigToken(Collections.singletonMap("id", "test123"));
        PreparedStatement ps = dataSource.getConnection().prepareStatement(
                "INSERT INTO TokenEntry(processorName, segment, tokenType, token) VALUES(?, ?, ?, ?)"
        );
        ps.setString(1, "__config");
        ps.setInt(2, 0);
        ps.setString(3, ConfigToken.class.getName());
        ps.setBytes(4, tokenStore.serializer().serialize(token, byte[].class).getData());
        ps.executeUpdate();

        Optional<String> id1 = tokenStore.retrieveStorageIdentifier();
        assertThat(id1).isPresent();
        Optional<String> id2 = tokenStore.retrieveStorageIdentifier();
        assertThat(id2).isPresent();
        assertThat(id2).hasValue(id1.get());

        assertThat(id1).hasValue("test123");
    }

    @Configuration
    public static class Context {

        @SuppressWarnings("Duplicates")
        @Bean
        public DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
            dataSource.setUser("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        public PlatformTransactionManager txManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        public JdbcTokenStore tokenStore(DataSource dataSource) {
            return JdbcTokenStore.builder()
                                 .connectionProvider(dataSource::getConnection)
                                 .serializer(TestSerializer.XSTREAM.getSerializer())
                                 .build();
        }

        @Bean
        public JdbcTokenStore concurrentTokenStore(DataSource dataSource) {
            return JdbcTokenStore.builder()
                                 .connectionProvider(dataSource::getConnection)
                                 .serializer(TestSerializer.XSTREAM.getSerializer())
                                 .claimTimeout(Duration.ofSeconds(2))
                                 .nodeId("concurrent")
                                 .build();
        }

        @Bean
        public JdbcTokenStore stealingTokenStore(DataSource dataSource) {
            return JdbcTokenStore.builder()
                                 .connectionProvider(dataSource::getConnection)
                                 .serializer(TestSerializer.XSTREAM.getSerializer())
                                 .claimTimeout(Duration.ofSeconds(-1))
                                 .nodeId("stealing")
                                 .build();
        }

        @Bean
        public TransactionManager transactionManager(PlatformTransactionManager txManager) {
            //noinspection Duplicates
            return () -> {
                TransactionStatus transaction = txManager.getTransaction(new DefaultTransactionDefinition());
                return new Transaction() {
                    @Override
                    public void commit() {
                        txManager.commit(transaction);
                    }

                    @Override
                    public void rollback() {
                        txManager.rollback(transaction);
                    }
                };
            };
        }
    }
}
