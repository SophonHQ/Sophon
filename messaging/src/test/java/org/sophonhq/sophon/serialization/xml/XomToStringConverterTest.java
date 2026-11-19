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

package org.sophonhq.sophon.serialization.xml;

import nu.xom.Document;
import nu.xom.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jochen Munz
 */
class XomToStringConverterTest {

    private XomToStringConverter testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new XomToStringConverter();
    }

    @Test
    void canConvert() {
        assertThat(testSubject.expectedSourceType()).isEqualTo(Document.class);
        assertThat(testSubject.targetType()).isEqualTo(String.class);
    }

    @Test
    void convert() {
        Document doc = new Document(new Element("rootElement"));

        String actual = testSubject.convert(doc);

        assertThat(actual).isNotNull();

        assertThat(actual).as("Wrong output: " + actual).contains("rootElement");
    }
}
