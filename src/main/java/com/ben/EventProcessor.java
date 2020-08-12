package com.ben;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.stream.KillSwitches;
import akka.stream.SharedKillSwitch;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;

@Slf4j
public class EventProcessor {

    public static EntityTypeKey<Ping> entityKey(String eventProcessorId) {
        return EntityTypeKey.create(Ping.class, eventProcessorId);
    }

    public static void init(
            ActorSystem<?> system,
            EventProcessorConfig settings,
            Function<String, EventProcessorStream> eventProcessorStream) {
        EntityTypeKey<Ping> eventProcessorEntityKey = entityKey(settings.id);

        ClusterSharding.get(system).init(Entity.of(eventProcessorEntityKey, entityContext ->
                EventProcessor.create(eventProcessorStream.apply(entityContext.getEntityId()))).withRole("read-model"));
        ;

        KeepAlive.init(system, eventProcessorEntityKey);
    }

    public static Behavior<Ping> create(EventProcessorStream eventProcessorStream) {
        return Behaviors.setup(context -> {
            SharedKillSwitch killSwitch = KillSwitches.shared("eventProcessorSwitch");
            eventProcessorStream.runQueryStream(killSwitch);

            return Behaviors.receive(Ping.class)
                    .onMessage(Ping.class, msg -> Behaviors.same())
                    .onSignal(PostStop.class, sig -> {
                        killSwitch.shutdown();
                        return Behaviors.same();
                    })
                    .build();
        });
    }

    public enum Ping implements MySerializable {
        INSTANCE
    }


}
