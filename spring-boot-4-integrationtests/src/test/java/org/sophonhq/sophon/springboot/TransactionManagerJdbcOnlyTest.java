/*
 * Copyright (c) 2026. SophonHQ
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

package org.sophonhq.sophon.springboot;

import org.junit.jupiter.api.*;
import org.sophonhq.sophon.common.transaction.TransactionManager;
import org.sophonhq.sophon.spring.messaging.unitofwork.SpringTransactionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TransactionManagerJdbcOnlyTest.class)
@EnableAutoConfiguration(
        excludeName = {
                "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
                "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
                "org.springframework.boot.hibernate.autoconfigure.metrics.HibernateMetricsAutoConfiguration",
                "org.sophonhq.sophon.springboot.autoconfig.JpaAutoConfiguration",
                "org.sophonhq.sophon.springboot.autoconfig.JpaEventStoreAutoConfiguration"
        })
class TransactionManagerJdbcOnlyTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void sophonTransactionManagerWrapsSpringsTransactionManager() {
        assertNotNull(context.getBean(PlatformTransactionManager.class));
        assertInstanceOf(SpringTransactionManager.class, context.getBean(TransactionManager.class));
    }
}
