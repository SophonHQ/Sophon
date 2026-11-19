/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.sophonhq.sophon.common.property;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


public abstract class AbstractPropertyAccessStrategyTest<T> {

    @Test
    void getValue() {
        final Property<T> actualProperty = getProperty(regularPropertyName());
        assertThat(actualProperty).isNotNull();
        assertThat(actualProperty.<String>getValue(propertyHoldingInstance())).isNotNull();
    }

    @Test
    void getValue_BogusProperty() {
        assertThat(getProperty(unknownPropertyName())).isNull();
    }

    @Test
    void getValue_ExceptionOnAccess() {
        Property<T> property = getProperty(exceptionPropertyName());

        assertThatThrownBy(() -> property.getValue(propertyHoldingInstance()))
                .isInstanceOf(PropertyAccessException.class);
    }

    @Test
    void voidReturnTypeRejected() {
        Property<T> property = getProperty(voidPropertyName());
        assertThat(property).as("void methods should not be accepted as property").isNull();
    }

    protected abstract String voidPropertyName();

    protected abstract String exceptionPropertyName();

    protected abstract String regularPropertyName();

    protected abstract String unknownPropertyName();

    protected abstract T propertyHoldingInstance();

    protected abstract Property<T> getProperty(String property);
}
