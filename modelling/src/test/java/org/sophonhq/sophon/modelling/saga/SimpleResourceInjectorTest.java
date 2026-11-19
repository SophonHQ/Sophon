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

package org.sophonhq.sophon.modelling.saga;

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.modelling.utils.MockException;

import java.util.function.Consumer;
import java.util.function.Function;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Allard Buijze
 */
class SimpleResourceInjectorTest {

    private SimpleResourceInjector testSubject;

    @Test
    void injectFieldResource() {
        SomeFieldResource expectedFieldResource = new SomeFieldResource();
        testSubject = new SimpleResourceInjector(expectedFieldResource);
        final StubSaga saga = new StubSaga();
        testSubject.injectResources(saga);

        assertThat(saga.getSomeWeirdResource()).isNull();
        assertThat(saga.getSomeFieldResource()).isSameAs(expectedFieldResource);
    }

    @Test
    void injectMethodResource() {
        final SomeMethodResource expectedMethodResource = new SomeMethodResource();
        testSubject = new SimpleResourceInjector(expectedMethodResource);
        final StubSaga saga = new StubSaga();
        testSubject.injectResources(saga);

        assertThat(saga.getSomeWeirdResource()).isNull();
        assertThat(saga.getSomeMethodResource()).isSameAs(expectedMethodResource);
    }

    @Test
    void injectFieldAndMethodResources() {
        final SomeFieldResource expectedFieldResource = new SomeFieldResource();
        final SomeMethodResource expectedMethodResource = new SomeMethodResource();
        testSubject = new SimpleResourceInjector(expectedFieldResource, expectedMethodResource);
        final StubSaga saga = new StubSaga();
        testSubject.injectResources(saga);

        assertThat(saga.getSomeWeirdResource()).isNull();
        assertThat(saga.getSomeFieldResource()).isSameAs(expectedFieldResource);
        assertThat(saga.getSomeMethodResource()).isSameAs(expectedMethodResource);
    }

    @Test
    void injectResource_ExceptionsIgnored() {
        final SomeMethodResource resource = new SomeMethodResource();
        testSubject = new SimpleResourceInjector(resource, new SomeWeirdResource());
        final StubSaga saga = new StubSaga();
        testSubject.injectResources(saga);

        assertThat(saga.getSomeWeirdResource()).isNull();
        assertThat(saga.getSomeMethodResource()).isSameAs(resource);
    }

    private static class StubSaga implements Saga<StubSaga> {

        @Inject
        private SomeFieldResource someFieldResource;
        private SomeMethodResource someMethodResource;
        private SomeWeirdResource someWeirdResource;

        @Override
        public String getSagaIdentifier() {
            return "id";
        }

        @Override
        public AssociationValues getAssociationValues() {
            return new AssociationValuesImpl();
        }

        @Override
        public <R> R invoke(Function<StubSaga, R> invocation) {
            return invocation.apply(this);
        }

        @Override
        public void execute(Consumer<StubSaga> invocation) {
            invocation.accept(this);
        }

        @Override
        public boolean canHandle(EventMessage<?> event) {
            return true;
        }

        @Override
        public Object handle(EventMessage event) {
            return null;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        public SomeFieldResource getSomeFieldResource() {
            return someFieldResource;
        }

        public SomeMethodResource getSomeMethodResource() {
            return someMethodResource;
        }

        @Inject
        public void setSomeMethodResource(SomeMethodResource someMethodResource) {
            this.someMethodResource = someMethodResource;
        }

        public SomeWeirdResource getSomeWeirdResource() {
            return someWeirdResource;
        }

        public void setSomeWeirdResource(SomeWeirdResource someWeirdResource) {
            throw new MockException();
        }

    }

    private static class SomeFieldResource {
    }

    private static class SomeMethodResource {
    }

    private static class SomeWeirdResource {
    }

}
