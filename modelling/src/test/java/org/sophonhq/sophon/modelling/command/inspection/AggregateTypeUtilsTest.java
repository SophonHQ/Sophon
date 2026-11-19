package org.sophonhq.sophon.modelling.command.inspection;

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.modelling.command.AggregateRoot;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link AggregateTypeUtils}.
 *
 * @author Steven van Beelen
 */
class AggregateTypeUtilsTest {

    @Test
    void declaredTypeOfDefaultsToSimpleName() {
        assertThat(AggregateTypeUtils.declaredTypeOf(SimpleNameType.class)).isEqualTo("SimpleNameType");
    }

    @Test
    void declaredTypeOfHonorsAggregateRootTypeOverride() {
        assertThat(AggregateTypeUtils.declaredTypeOf(AggregateRootType.class)).isEqualTo("customType");
    }

    private static class SimpleNameType {

    }

    @AggregateRoot(type = "customType")
    private static class AggregateRootType extends SimpleNameType {

    }
}