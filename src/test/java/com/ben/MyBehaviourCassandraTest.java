package com.ben;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.persistence.cassandra.query.javadsl.CassandraReadJournal;
import akka.persistence.cassandra.session.javadsl.CassandraSession;
import akka.persistence.query.PersistenceQuery;
import akka.persistence.query.javadsl.EventsByTagQuery;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import de.flapdoodle.embed.mongo.MongodExecutable;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.*;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

@Slf4j
public class MyBehaviourCassandraTest {

    private final int transactionCount = 10;
    private final int eventsPerTransaction = 10;
    private final int delayPerEvent = 5;
    private final int randomDelay = 5;

    private static Config config;
    private static ActorSystem system;
    private static MongodExecutable mongodExecutable;

    @BeforeClass
    public static void setup() throws IOException {
        config = ConfigFactory.load("cassandra.conf");
        system = ActorSystem.create(Behaviors.empty(), "Testing", config);
        CassandraSession session = CassandraSessionExtension.Id.get(system).session;

        String keyspaceStmt =
                "CREATE KEYSPACE IF NOT EXISTS akkaboot \n" +
                        "WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 } \n";

        String offsetTableStmt =
                "CREATE TABLE IF NOT EXISTS akkaboot.offsetStore ( \n" +
                        "  eventProcessorId text, \n" +
                        "  tag text, \n" +
                        "  timeUuidOffset timeuuid, \n" +
                        "  PRIMARY KEY (eventProcessorId, tag) \n" +
                        ") \n";

        // ok to block here, main thread
        try {
            session.executeDDL(keyspaceStmt).toCompletableFuture().get(30, TimeUnit.SECONDS);
            session.executeDDL(offsetTableStmt).toCompletableFuture().get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @AfterClass
    public static void tearDown() throws IOException {
        system.terminate();
    }

    @Test
    @Ignore
    public void testSendAndReceive() throws InterruptedException {
        Config config = ConfigFactory.load();
        List<MyBehaviour.Event> persistedEvents = Collections.synchronizedList(new ArrayList());
        List<MyBehaviour.Event> processedEvent = Collections.synchronizedList(new ArrayList());

        MyBehaviour.init(system, persistedEvents);

        ExecutorService service = Executors.newFixedThreadPool(transactionCount);
        for (int i = 1; i <= transactionCount; i++) {
            service.execute(() -> sendEvents());
        }

        try {
            await().atMost(Duration.ofSeconds(30)).until(() -> persistedEvents.size() == (transactionCount * eventsPerTransaction));
        } catch (ConditionTimeoutException e) {
            log.debug("********** TEST HAS FAILED **************");
            Assert.fail("The test has failed as not enough events persisted, " + persistedEvents.size() + " identified");
        }

        log.debug("Successfully sent all the messages, now trying to retrieve them....");

        EventsByTagQuery eventsByTagQuery = PersistenceQuery.get(Adapter.toClassic(system)).getReadJournalFor(CassandraReadJournal.class, CassandraReadJournal.Identifier());

        // now try the read side.
        EventProcessorConfig settings = EventProcessorConfig.create(system);
        EventProcessor.init(
                system,
                settings,
                tag -> new EventProcessorStream(system, settings.id, tag, eventsByTagQuery, processedEvent) {
                });

        try {
            await().atMost(Duration.ofSeconds(30)).until(() -> processedEvent.size() == (transactionCount * eventsPerTransaction));
        } catch (ConditionTimeoutException e) {
            log.debug("********** TEST HAS FAILED **************");
            Assert.fail("The test has failed, " + processedEvent.size() + " identified");
        }

    }

    private void sendEvents() {
        String persistenceId = UUID.randomUUID().toString();
        EntityRef<MyBehaviour.Command> entityRef = ClusterSharding.get(system).entityRefFor(MyBehaviour.ENTITY_TYPE_KEY, persistenceId);
        for (int i = 1; i <= eventsPerTransaction; i++) {
            log.debug("Adding event: {}, {}", persistenceId, i);
            try {
                Thread.sleep(Double.valueOf(delayPerEvent + Math.random() * randomDelay).longValue());
            } catch (Exception e) {
                e.printStackTrace();
            }
            entityRef.tell(new MyCommand(i));
        }
    }

}
