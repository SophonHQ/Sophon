package io.github.sophonhq.sophon.test.saga;

import io.github.sophonhq.sophon.eventhandling.DomainEventMessage;
import io.github.sophonhq.sophon.eventhandling.EventMessage;
import io.github.sophonhq.sophon.messaging.annotation.MessageHandlingMember;
import io.github.sophonhq.sophon.modelling.saga.AssociationResolver;
import io.github.sophonhq.sophon.modelling.saga.PayloadAssociationResolver;
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
