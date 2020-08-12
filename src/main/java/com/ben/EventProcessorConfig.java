package com.ben;

import akka.actor.typed.ActorSystem;
import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

@Slf4j
public class EventProcessorConfig {

    public final String id;
    public final Duration keepAliveInterval;
    public final String tagPrefix;
    public final int parallelism;

    public EventProcessorConfig(String id, Duration keepAliveInterval, String tagPrefix, int parallelism) {
        this.id = id;
        this.keepAliveInterval = keepAliveInterval;
        this.tagPrefix = tagPrefix;
        this.parallelism = parallelism;
    }

    public static EventProcessorConfig create(ActorSystem<?> system) {
        log.info("Creating event config");
        return create(system.settings().config().getConfig("event-processor"));
    }

    public static EventProcessorConfig create(Config config) {
        log.info("Using event config {}. {}, {}, {}",config.getString("id"),config.getDuration("keep-alive-interval"),config.getString("tag-prefix"),config.getInt("parallelism"));
        return new EventProcessorConfig(
                config.getString("id"),
                config.getDuration("keep-alive-interval"),
                config.getString("tag-prefix"),
                config.getInt("parallelism")
        );
    }
}
