package org.sophonhq.sophon.test.saga;

import org.jspecify.annotations.NonNull;
import org.sophonhq.sophon.eventhandling.DomainEventMessage;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.messaging.annotation.MessageHandlingMember;
import org.sophonhq.sophon.modelling.saga.AssociationResolver;
import org.sophonhq.sophon.modelling.saga.PayloadAssociationResolver;

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
