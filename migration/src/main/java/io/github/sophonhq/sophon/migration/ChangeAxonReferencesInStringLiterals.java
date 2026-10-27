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

package io.github.sophonhq.sophon.migration;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

import java.util.regex.Pattern;

/**
 * Replaces Axon Framework 4 package and type names, and {@code ${axon.*}} property placeholders, in Java string
 * literals with their Sophon equivalents. This covers references the type-aware recipes cannot see, such as
 * {@code @EntityScan("org.axonframework.eventhandling.tokenstore.jpa")} or
 * {@code @Value("${axon.serializer.general}")}.
 *
 * @since 1.0.0
 */
public class ChangeAxonReferencesInStringLiterals extends Recipe {

    private static final Pattern RENAMED_TYPE =
            Pattern.compile("org\\.axonframework\\.((?:[a-z0-9_]+\\.)*)(\\w*)Axon(\\w*)");
    private static final Pattern PACKAGE = Pattern.compile("org\\.axonframework\\b");

    @Override
    public String getDisplayName() {
        return "Migrate Axon Framework 4 references in string literals to Sophon";
    }

    @Override
    public String getDescription() {
        return "Replaces Axon Framework package and type names and `${axon.*}` property placeholders in string "
                + "literals with their Sophon equivalents.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (!(visited.getValue() instanceof String) || visited.getValueSource() == null) {
                    return visited;
                }
                String value = (String) visited.getValue();
                String migrated = migrate(value);
                if (migrated.equals(value)) {
                    return visited;
                }
                return visited.withValue(migrated).withValueSource(migrate(visited.getValueSource()));
            }
        };
    }

    static String migrate(String text) {
        String result = RENAMED_TYPE.matcher(text).replaceAll("io.github.sophonhq.sophon.$1$2Sophon$3");
        result = PACKAGE.matcher(result).replaceAll("io.github.sophonhq.sophon");
        return result.replace("${axon.", "${sophon.");
    }
}
