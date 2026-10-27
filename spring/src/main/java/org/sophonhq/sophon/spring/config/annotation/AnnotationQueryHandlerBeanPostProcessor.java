/*
 * Copyright (c) 2010-2022. Axon Framework
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
package org.sophonhq.sophon.spring.config.annotation;

import org.jspecify.annotations.NonNull;
import org.sophonhq.sophon.common.annotation.AnnotationUtils;
import org.sophonhq.sophon.messaging.MessageHandler;
import org.sophonhq.sophon.messaging.annotation.HandlerDefinition;
import org.sophonhq.sophon.messaging.annotation.ParameterResolverFactory;
import org.sophonhq.sophon.queryhandling.QueryHandler;
import org.sophonhq.sophon.queryhandling.QueryHandlerAdapter;
import org.sophonhq.sophon.queryhandling.annotation.AnnotationQueryHandlerAdapter;
import org.sophonhq.sophon.spring.config.AbstractAnnotationHandlerBeanPostProcessor;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Spring Bean post processor that automatically generates an adapter for each bean containing {@link QueryHandler}
 * annotated methods.
 *
 * @author Marc Gathier
 * @since axon-3.1
 * @deprecated Replaced by the {@link org.sophonhq.sophon.spring.config.MessageHandlerLookup} and {@link
 * org.sophonhq.sophon.spring.config.MessageHandlerConfigurer}.
 */
@Deprecated
public class AnnotationQueryHandlerBeanPostProcessor extends AbstractAnnotationHandlerBeanPostProcessor<QueryHandlerAdapter, AnnotationQueryHandlerAdapter> {
    @Override
    protected Class<?>[] getAdapterInterfaces() {
        return new Class[]{QueryHandlerAdapter.class, MessageHandler.class};
    }

    @Override
    protected boolean isPostProcessingCandidate(Class<?> targetClass) {
        return this.hasQueryHandlerMethod(targetClass);
    }

    private boolean hasQueryHandlerMethod(Class<?> beanClass) {
        AtomicBoolean result = new AtomicBoolean(false);
        ReflectionUtils.doWithMethods(beanClass, new HasQueryHandlerAnnotationMethodCallback(result));
        return result.get();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected AnnotationQueryHandlerAdapter initializeAdapterFor(Object o,
                                                                 ParameterResolverFactory parameterResolverFactory,
                                                                 HandlerDefinition handlerDefinition) {
        return new AnnotationQueryHandlerAdapter<>(o, parameterResolverFactory, handlerDefinition);
    }

    private class HasQueryHandlerAnnotationMethodCallback implements ReflectionUtils.MethodCallback {
        private final AtomicBoolean result;

        public HasQueryHandlerAnnotationMethodCallback(AtomicBoolean result) {
            this.result = result;
        }

        @Override
        public void doWith(@NonNull Method method) throws IllegalArgumentException {
            if (AnnotationUtils.findAnnotationAttributes(method, QueryHandler.class).isPresent()) {
                result.set(true);
            }
        }
    }
}
