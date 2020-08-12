package com.ben;


import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.*;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.time.Duration.ofMillis;

@Slf4j
public class MyBehaviour extends EventSourcedBehavior<MyBehaviour.Command, MyBehaviour.Event, MyBehaviour.State> {

    public static EntityTypeKey<Command> ENTITY_TYPE_KEY =
            EntityTypeKey.create(Command.class, "MyBehaviour");

    private final String transactionId;
    private final ActorContext<Command> context;
    private final Set<String> eventProcessorTags;
    private final List<Event> persistedEvents;

    private MyBehaviour(String transactionId,
                        ActorContext<Command> actorContext,
                        Set<String> eventProcessorTags,
                        List<Event> persistedEvents) {
        super(PersistenceId.of(ENTITY_TYPE_KEY.name(), transactionId), SupervisorStrategy.restartWithBackoff(ofMillis(5000), Duration.ofSeconds(5), 0.1));
        this.transactionId = transactionId;
        this.context = actorContext;
        this.eventProcessorTags = eventProcessorTags;
        this.persistedEvents = persistedEvents;
    }

    public static void init(ActorSystem<?> system, List<Event> persistedEvents) {
        ClusterSharding.get(system).init(Entity.of(ENTITY_TYPE_KEY, entityContext -> {
            int n = Math.abs(entityContext.getEntityId().hashCode() % 4);
            String eventProcessorTag = "tag-" + n;
            return MyBehaviour.create(entityContext.getEntityId(), Collections.singleton(eventProcessorTag), persistedEvents);
        })
                .withRole("write-model"));
    }

    public static Behavior<Command> create(String transactionId, Set<String> tags, List<Event> persistedEvents) {
        log.info("Creating behaviour for id {}", transactionId);
        return Behaviors.setup(ctx -> new MyBehaviour(transactionId, ctx, tags, persistedEvents));
    }

    @Override
    public CommandHandler<Command, Event, State> commandHandler() {

        CommandHandlerBuilder<Command, Event, State> commandHandlerBuilder = newCommandHandlerBuilder();

        commandHandlerBuilder
                .forAnyState()
                .onAnyCommand(this::persistEvent);

        return commandHandlerBuilder.build();
    }

    private Effect<Event, State> persistEvent(Command command) {
        log.debug("Persisting event with sequence {} on id {}", command.getSequence(), transactionId);
        Event myEvent = new MyEvent(command.getSequence());
        return Effect().persist(myEvent).thenRun(() -> persistedEvents.add(myEvent));
    }

    @Override
    public EventHandler<State, Event> eventHandler() {
        EventHandlerBuilder<State, Event> eventHandlerBuilder = newEventHandlerBuilder();

        eventHandlerBuilder
                .forStateType(MyState.class)
                .onAnyEvent((state, event) -> {
                    state.increment();
                    return state;
                });

        return eventHandlerBuilder.build();
    }

    @Override
    public Set<String> tagsFor(Event paymentEvent) {
        return eventProcessorTags;
    }

    @Override
    public State emptyState() {
        return new MyState();
    }

    public interface Event extends MySerializable {
        int getSequence();
    }

    public interface Command extends MySerializable {
        int getSequence();
    }

    public interface State extends MySerializable {
        void increment();
    }

}
