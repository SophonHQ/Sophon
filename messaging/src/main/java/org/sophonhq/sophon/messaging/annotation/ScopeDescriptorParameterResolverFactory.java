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

package org.sophonhq.sophon.messaging.annotation;

import org.sophonhq.sophon.messaging.Message;
import org.sophonhq.sophon.messaging.Scope;
import org.sophonhq.sophon.messaging.ScopeDescriptor;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

import static org.sophonhq.sophon.messaging.NoScopeDescriptor.INSTANCE;

/**
 * Factory for a {@link ScopeDescriptor} {@link ParameterResolver}. Will return the result of {@link
 * Scope#describeCurrentScope()}. If no current scope is active, {@link org.sophonhq.sophon.messaging.NoScopeDescriptor#INSTANCE}
 * will be returned.
 *
 * @author Steven van Beelen
 * @since axon-4.5
 */
public class ScopeDescriptorParameterResolverFactory implements ParameterResolverFactory {

    @Override
    public ParameterResolver<ScopeDescriptor> createInstance(Executable executable,
                                                             Parameter[] parameters,
                                                             int parameterIndex) {
        return ScopeDescriptor.class.isAssignableFrom(parameters[parameterIndex].getType())
                ? new ScopeDescriptorParameterResolver() : null;
    }

    private static class ScopeDescriptorParameterResolver implements ParameterResolver<ScopeDescriptor> {

        @Override
        public ScopeDescriptor resolveParameterValue(Message<?> message) {
            try {
                return Scope.describeCurrentScope();
            } catch (IllegalStateException e) {
                return INSTANCE;
            }
        }

        @Override
        public boolean matches(Message<?> message) {
            return true;
        }
    }
}
