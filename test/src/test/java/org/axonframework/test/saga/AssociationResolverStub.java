package org.axonframework.test.saga;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.modelling.saga.AssociationResolver;
import org.axonframework.modelling.saga.PayloadAssociationResolver;
import org.jspecify.annotations.NonNull;

import static org.junit.Assert.*;

public class AssociationResolverStub implements AssociationResolver {

    private final PayloadAssociationResolver defaultResolver = new PayloadAssociationResolver();

    @Override
    public <T> void validate(@NonNull String associationPropertyName, @NonNull MessageHandlingMember<T> handler) {
        defaultResolver.validate(associationPropertyName, handler);
    }

    @Override
    public <T> Object resolve(@NonNull String associationPropertyName, @NonNull EventMessage<?> message,
                              @NonNull MessageHandlingMember<T> handler) {


        if (!DomainEventMessage.class.isAssignableFrom(message.getClass())) {
            fail("message is not assignable from DomainEventMessage");
        }
        return defaultResolver.resolve(associationPropertyName, message, handler);
    }
}
