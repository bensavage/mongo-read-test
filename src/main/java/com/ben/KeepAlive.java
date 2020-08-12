package com.ben;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.cluster.typed.ClusterSingleton;
import akka.cluster.typed.ClusterSingletonSettings;
import akka.cluster.typed.SingletonActor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KeepAlive extends AbstractBehavior<KeepAlive.Probe> {

    private final EventProcessorConfig settings;
    private final EntityTypeKey<EventProcessor.Ping> eventProcessorEntityKey;
    private final ClusterSharding sharding;

    private KeepAlive(ActorContext<Probe> context, EventProcessorConfig settings, EntityTypeKey<EventProcessor.Ping> eventProcessorEntityKey) {
        super(context);
        this.settings = settings;
        this.eventProcessorEntityKey = eventProcessorEntityKey;
        this.sharding = ClusterSharding.get(context.getSystem());
    }

    public static void init(ActorSystem<?> system, EntityTypeKey<EventProcessor.Ping> eventProcessorEntityKey) {
        log.info("init keep alive");
        EventProcessorConfig settings = EventProcessorConfig.create(system);
        ClusterSingleton.get(system).init(
                SingletonActor.of(KeepAlive.create(settings, eventProcessorEntityKey), "keepAlive-" + settings.id)
                        .withSettings(ClusterSingletonSettings.create(system).withRole("read-model")));
    }

    public static Behavior<Probe> create(
            EventProcessorConfig settings,
            EntityTypeKey<EventProcessor.Ping> eventProcessorEntityKey) {
        log.info("created probe");
        return Behaviors.setup(context ->
                Behaviors.withTimers(timers -> {
                    timers.startTimerWithFixedDelay(Probe.INSTANCE, Probe.INSTANCE, settings.keepAliveInterval);
                    return new KeepAlive(context, settings, eventProcessorEntityKey);
                }));
    }

    @Override
    public Receive<Probe> createReceive() {
        log.debug("received probe");
        return newReceiveBuilder()
                .onMessage(Probe.class, p -> onProbe())
                .build();
    }

    private Behavior<Probe> onProbe() {
        for (int i = 0; i < settings.parallelism; i++) {
            String eventProcessorId = settings.tagPrefix + "-" + i;
            sharding.entityRefFor(eventProcessorEntityKey, eventProcessorId).tell(EventProcessor.Ping.INSTANCE);
        }
        return this;
    }

    enum Probe {
        INSTANCE
    }


}
