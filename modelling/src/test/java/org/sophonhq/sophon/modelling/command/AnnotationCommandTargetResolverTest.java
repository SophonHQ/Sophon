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

package org.sophonhq.sophon.modelling.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.commandhandling.CommandMessage;

import java.util.UUID;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sophonhq.sophon.commandhandling.GenericCommandMessage.asCommandMessage;

/**
 * @author Allard Buijze
 */
class AnnotationCommandTargetResolverTest {

    private AnnotationCommandTargetResolver testSubject;

    @BeforeEach
    void setUp() {
        testSubject = AnnotationCommandTargetResolver.builder().build();
    }

    @Test
    void resolveTarget_CommandWithoutAnnotations() {
        assertThatThrownBy(() -> testSubject.resolveTarget(asCommandMessage("That won't work")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolveTarget_WithAnnotatedMethod() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        VersionedAggregateIdentifier actual = testSubject.resolveTarget(asCommandMessage(new Object() {
            @TargetAggregateIdentifier
            private UUID getIdentifier() {
                return aggregateIdentifier;
            }
        }));

        assertThat(actual.getIdentifier()).isEqualTo(aggregateIdentifier.toString());
        assertThat(actual.getVersion()).isNull();
    }

    @Test
    void resolveTarget_WithAnnotatedMethodAndVersion() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        VersionedAggregateIdentifier actual = testSubject.resolveTarget(asCommandMessage(new Object() {
            @TargetAggregateIdentifier
            private UUID getIdentifier() {
                return aggregateIdentifier;
            }

            @TargetAggregateVersion
            private Long version() {
                return 1L;
            }
        }));

        assertThat(actual.getIdentifier()).isEqualTo(aggregateIdentifier.toString());
        assertThat(actual.getVersion()).isEqualTo(1L);
    }

    @Test
    void resolveTarget_WithAnnotatedMethodAndStringVersion() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        VersionedAggregateIdentifier actual = testSubject.resolveTarget(asCommandMessage(new Object() {
            @TargetAggregateIdentifier
            private UUID getIdentifier() {
                return aggregateIdentifier;
            }

            @TargetAggregateVersion
            private String version() {
                return "1000230";
            }
        }));

        assertThat(actual.getIdentifier()).isEqualTo(aggregateIdentifier.toString());
        assertThat(actual.getVersion()).isEqualTo(1000230L);
    }

    @Test
    void resolveTarget_WithAnnotatedMethodAndVoidIdentifier() {
        CommandMessage<Object> command = asCommandMessage(new Object() {
            @TargetAggregateIdentifier
            private void getIdentifier() {
            }
        });
        assertThatThrownBy(() -> testSubject.resolveTarget(command)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolveTarget_WithAnnotatedFields() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        final Object version = 1L;
        VersionedAggregateIdentifier actual = testSubject.resolveTarget(
                asCommandMessage(new FieldAnnotatedCommand(aggregateIdentifier, version)));
        assertThat(actual.getIdentifier()).isEqualTo(aggregateIdentifier.toString());
        assertThat(actual.getVersion()).isEqualTo(version);
    }

    @Test
    void resolveTarget_WithAnnotatedFields_StringIdentifier() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        final Object version = 1L;
        VersionedAggregateIdentifier actual = testSubject.resolveTarget(
                asCommandMessage(new FieldAnnotatedCommand(aggregateIdentifier, version)));
        assertThat(actual.getIdentifier()).isEqualTo(aggregateIdentifier.toString());
        assertThat(actual.getVersion()).isEqualTo(version);
    }

    @Test
    void resolveTarget_WithAnnotatedFields_ObjectIdentifier() {
        final Object aggregateIdentifier = new Object();
        final Object version = 1L;
        VersionedAggregateIdentifier actual = testSubject.resolveTarget(
                asCommandMessage(new FieldAnnotatedCommand(aggregateIdentifier, version)));
        assertThat(actual.getIdentifier()).isEqualTo(aggregateIdentifier.toString());
        assertThat(actual.getVersion()).isEqualTo(version);
    }

    @Test
    void resolveTarget_WithAnnotatedFields_ParsableVersion() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        final Object version = "1";
        VersionedAggregateIdentifier actual = testSubject.resolveTarget(
                asCommandMessage(new FieldAnnotatedCommand(aggregateIdentifier, version)));
        assertThat(actual.getIdentifier()).isEqualTo(aggregateIdentifier.toString());
        assertThat(actual.getVersion()).isEqualTo(1L);
    }

    @Test
    void resolveTarget_WithAnnotatedFields_NonNumericVersion() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        final Object version = "abc";
        CommandMessage<Object> command = asCommandMessage(new FieldAnnotatedCommand(aggregateIdentifier, version));
        assertThatThrownBy(() -> testSubject.resolveTarget(command)).isInstanceOf(IllegalArgumentException.class);
    }

	@Test
    void metaAnnotationsOnMethods() {
		final UUID aggregateIdentifier = UUID.randomUUID();
		final Long version = Long.valueOf(98765432109L);

		VersionedAggregateIdentifier actual = testSubject.resolveTarget(asCommandMessage(new Object() {
			@MetaTargetAggregateIdentifier
			private UUID getIdentifier() {
				return aggregateIdentifier;
			}

			@MetaTargetAggregateVersion
			private Long version() {
				return version;
			}
		}));
        assertThat(actual.getIdentifier()).isEqualTo(aggregateIdentifier.toString());
        assertThat(actual.getVersion()).isEqualTo(version);
	}

	@Test
    void metaAnnotationsOnFields() {
		final UUID aggregateIdentifier = UUID.randomUUID();
		final Long version = Long.valueOf(98765432109L);

		VersionedAggregateIdentifier actual = testSubject.resolveTarget(
				asCommandMessage(new FieldMetaAnnotatedCommand(aggregateIdentifier, version)));

        assertThat(actual.getIdentifier()).isEqualTo(aggregateIdentifier.toString());
        assertThat(actual.getVersion()).isEqualTo(version);
	}

	@Test
    void customAnnotationsOnMethods() {
		testSubject = AnnotationCommandTargetResolver.builder()
                .targetAggregateIdentifierAnnotation(CustomTargetAggregateIdentifier.class)
                .targetAggregateVersionAnnotation(CustomTargetAggregateVersion.class)
				.build();

		final UUID aggregateIdentifier = UUID.randomUUID();
		final Long version = Long.valueOf(98765432109L);

		VersionedAggregateIdentifier actual = testSubject.resolveTarget(asCommandMessage(new Object() {
			@CustomTargetAggregateIdentifier
			private UUID getIdentifier() {
				return aggregateIdentifier;
			}

			@CustomTargetAggregateVersion
			private Long version() {
				return version;
			}
		}));
        assertThat(actual.getIdentifier()).isEqualTo(aggregateIdentifier.toString());
        assertThat(actual.getVersion()).isEqualTo(version);
	}

	@Test
    void customAnnotationsOnFields() {
		testSubject = AnnotationCommandTargetResolver.builder()
                .targetAggregateIdentifierAnnotation(CustomTargetAggregateIdentifier.class)
                .targetAggregateVersionAnnotation(CustomTargetAggregateVersion.class)
				.build();

		final UUID aggregateIdentifier = UUID.randomUUID();
		final Long version = Long.valueOf(98765432109L);

		VersionedAggregateIdentifier actual = testSubject.resolveTarget(
				asCommandMessage(new FieldCustomAnnotatedCommand(aggregateIdentifier, version)));

        assertThat(actual.getIdentifier()).isEqualTo(aggregateIdentifier.toString());
        assertThat(actual.getVersion()).isEqualTo(version);
	}

    private static class FieldAnnotatedCommand {

        @TargetAggregateIdentifier
        private final Object aggregateIdentifier;

        @TargetAggregateVersion
        private final Object version;

        FieldAnnotatedCommand(Object aggregateIdentifier, Object version) {
            this.aggregateIdentifier = aggregateIdentifier;
            this.version = version;
        }
    }

	private static class FieldMetaAnnotatedCommand {

		@MetaTargetAggregateIdentifier
		private final Object aggregateIdentifier;

		@MetaTargetAggregateVersion
		private final Object version;

		FieldMetaAnnotatedCommand(Object aggregateIdentifier, Object version) {
			this.aggregateIdentifier = aggregateIdentifier;
			this.version = version;
		}
	}

	private static class FieldCustomAnnotatedCommand {

		@CustomTargetAggregateIdentifier
		private final Object aggregateIdentifier;

		@CustomTargetAggregateVersion
		private final Object version;

		FieldCustomAnnotatedCommand(Object aggregateIdentifier, Object version) {
			this.aggregateIdentifier = aggregateIdentifier;
			this.version = version;
		}
	}

	@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE })
	@Retention(RetentionPolicy.RUNTIME)
	@TargetAggregateIdentifier
    static @interface MetaTargetAggregateIdentifier {
	}

	@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE })
	@Retention(RetentionPolicy.RUNTIME)
	@TargetAggregateVersion
    static @interface MetaTargetAggregateVersion {
	}

	@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE })
	@Retention(RetentionPolicy.RUNTIME)
    static @interface CustomTargetAggregateIdentifier {
	}

	@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE })
	@Retention(RetentionPolicy.RUNTIME)
    static @interface CustomTargetAggregateVersion {
	}
}