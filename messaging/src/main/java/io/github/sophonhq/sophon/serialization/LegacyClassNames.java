/*
 * Copyright (c) 2026. Sophon contributors
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

package io.github.sophonhq.sophon.serialization;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates class names written by Axon Framework 4 into their Sophon equivalents.
 * <p>
 * Sophon moved all classes from the {@code org.axonframework} package to {@code io.github.sophonhq.sophon} and replaced
 * "Axon" with "Sophon" in class names. Data stored by Axon Framework 4, such as tracking tokens, sagas and dead
 * letters, still refers to the original names. Serializers configured to translate legacy class names use this class
 * to map those names when reading.
 *
 * @since 1.0.0
 */
public final class LegacyClassNames {

    private static final String LEGACY_PACKAGE_PREFIX = "org.axonframework.";
    private static final String PACKAGE_PREFIX = "io.github.sophonhq.sophon.";
    // Matches names not preceded by another identifier character, as well as names in array descriptors ("[L...;").
    private static final Pattern LEGACY_CLASS_NAME = Pattern.compile(
            "(?:(?<=\\[L)|(?<![A-Za-z0-9_$.]))org\\.axonframework\\.((?:[a-z0-9_]+\\.)*)([A-Za-z0-9_$-]+)"
    );

    private LegacyClassNames() {
        // Utility class
    }

    /**
     * Indicates whether the given {@code text} contains at least one Axon Framework class or package name.
     *
     * @param text The text to check.
     * @return {@code true} if the text contains an Axon Framework class or package name, {@code false} otherwise.
     */
    public static boolean containsLegacyName(String text) {
        return text.contains(LEGACY_PACKAGE_PREFIX);
    }

    /**
     * Translates the given {@code className} if it refers to an Axon Framework class. Other class names are returned
     * unchanged.
     *
     * @param className The fully qualified class name to translate.
     * @return The Sophon equivalent of an Axon Framework class name, or the given {@code className} otherwise.
     */
    public static String translate(String className) {
        return className.startsWith(LEGACY_PACKAGE_PREFIX) ? translateAll(className) : className;
    }

    /**
     * Translates every Axon Framework class name in the given {@code text}, for example a serialized XML or JSON
     * document.
     *
     * @param text The text containing class names to translate.
     * @return The given {@code text} with Axon Framework class names replaced by their Sophon equivalents.
     */
    public static String translateAll(String text) {
        if (!containsLegacyName(text)) {
            return text;
        }
        Matcher matcher = LEGACY_CLASS_NAME.matcher(text);
        StringBuilder result = new StringBuilder(text.length() + 16);
        while (matcher.find()) {
            String translated = PACKAGE_PREFIX + matcher.group(1) + matcher.group(2).replace("Axon", "Sophon");
            matcher.appendReplacement(result, Matcher.quoteReplacement(translated));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
