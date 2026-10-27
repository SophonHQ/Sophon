/*
 * Copyright (c) 2010-2026. Axon Framework
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

package io.github.sophonhq.sophon.config;

import io.github.sophonhq.sophon.lifecycle.Phase;
import io.github.sophonhq.sophon.messaging.timeout.SophonTaskJanitor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * {@link ConfigurerModule} that registers a shutdown hook to shut down the {@link SophonTaskJanitor} executor service
 * when the Sophon {@link Configuration} is shut down. This ensures the non-daemon janitor thread is terminated in all
 * environments (Spring and non-Spring), allowing the JVM to exit gracefully.
 * <p>
 * This module is automatically loaded via {@link java.util.ServiceLoader} from
 * {@code META-INF/services/io.github.sophonhq.sophon.config.ConfigurerModule} and does not require any user configuration.
 *
 * @author wadhwaroh-lang
 * @see SophonTaskJanitor
 * @since axon-4.13.1
 */
public class SophonTaskJanitorShutdownConfigurerModule implements ConfigurerModule {

    private static final Logger logger = LoggerFactory.getLogger(SophonTaskJanitorShutdownConfigurerModule.class);

    private static final int SHUTDOWN_PHASE = Phase.EXTERNAL_CONNECTIONS - 100;

    @Override
    public void configureModule(@NonNull Configurer configurer) {
        configurer.onShutdown(SHUTDOWN_PHASE, this::shutdownJanitor);
    }

    private void shutdownJanitor() {
        try {
            logger.debug("Shutting down Sophon task janitor executor service");
            SophonTaskJanitor.INSTANCE.shutdown();

            if (!SophonTaskJanitor.INSTANCE.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("Sophon task janitor executor service did not terminate within the timeout period. " +
                        "Attempting to force shutdown.");
                SophonTaskJanitor.INSTANCE.shutdownNow();
            }
            logger.debug("Sophon task janitor executor service has been shut down successfully");
        } catch (InterruptedException e) {
            logger.warn("Interrupted while waiting for Sophon task janitor executor service to terminate", e);
            SophonTaskJanitor.INSTANCE.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
