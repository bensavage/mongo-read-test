package com.ben;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorSystem;
import akka.persistence.query.Offset;
import akka.persistence.query.javadsl.EventsByTagQuery;
import akka.persistence.typed.PersistenceId;
import akka.stream.SharedKillSwitch;
import akka.stream.javadsl.RestartSource;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class EventProcessorStream {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final ActorSystem<?> system;
    protected final String tag;
    protected final String eventProcessorId;
    protected final EventsByTagQuery query;
    protected final List<MyBehaviour.Event> processedEvents;

    protected EventProcessorStream(ActorSystem<?> system, String eventProcessorId, String tag, EventsByTagQuery query, List<MyBehaviour.Event> processedEvents) {
        this.system = system;
        this.eventProcessorId = eventProcessorId;
        this.tag = tag;
        this.query = query;
        this.processedEvents = processedEvents;
    }

    protected CompletionStage<Object> processEvent(MyBehaviour.Event event, PersistenceId persistenceId, long sequenceNr) {
        log.debug("EventProcessor({}) consumed {} from {} with seqNr {}", tag, event, persistenceId, sequenceNr);
        processedEvents.add(event);
        return CompletableFuture.completedFuture(Done.getInstance());
    }

    public void runQueryStream(SharedKillSwitch killSwitch) {
        RestartSource.withBackoff(Duration.ofMillis(500), Duration.ofSeconds(20), 0.1, () ->
                Source.completionStageSource(
                        readOffset().thenApply(offset -> {
                            log.debug("Starting stream for tag [{}] from offset [{}]", tag, offset);
                            return processEventsByTag(offset)
                                    .groupedWithin(1000, Duration.ofSeconds(10));
                        })))
                .via(killSwitch.flow())
                .runWith(Sink.ignore(), system);
    }

    @SuppressWarnings("unchecked")
    private Source<Offset, NotUsed> processEventsByTag(Offset offset) {
        log.debug("Starting source for offset {}", offset);
        return query
                .eventsByTag(tag, offset)
                .mapAsync(1, eventEnvelope -> processEvent((MyBehaviour.Event) eventEnvelope.event(), PersistenceId.ofUniqueId(eventEnvelope.persistenceId()), eventEnvelope.sequenceNr())
                        .thenApply(done -> eventEnvelope.offset()));
    }

    protected CompletionStage<Offset> readOffset() {
        return CompletableFuture.completedFuture(Offset.noOffset());
    }
}
