package com.echaris.api.notification;

import com.mongodb.client.MongoChangeStreamCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationStreamService {

    private final MongoTemplate mongoTemplate;

    private final Map<String, Set<SseEmitter>> clients =
            new ConcurrentHashMap<>();

    private final ScheduledExecutorService executor =
            Executors.newScheduledThreadPool(
                    2,
                    task -> {
                        Thread thread = new Thread(
                                task,
                                "notification-stream"
                        );

                        thread.setDaemon(true);
                        return thread;
                    }
            );

    private volatile boolean running = true;

    private volatile MongoChangeStreamCursor<
            ChangeStreamDocument<Document>> cursor;

    @PostConstruct
    public void start() {
        executor.execute(this::watch);

        executor.scheduleWithFixedDelay(
                this::heartbeat,
                15,
                15,
                TimeUnit.SECONDS
        );
    }

    public SseEmitter connect(
            String userId,
            long timeoutMillis
    ) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);

        clients.compute(
                userId,
                (id, connections) -> {
                    Set<SseEmitter> set = connections == null
                            ? new HashSet<>()
                            : connections;

                    set.add(emitter);
                    return set;
                }
        );

        Runnable remove = () -> remove(
                userId,
                emitter
        );

        emitter.onCompletion(remove);

        emitter.onTimeout(() -> {
            remove.run();
            emitter.complete();
        });

        emitter.onError(error -> remove.run());

        send(
                userId,
                emitter,
                SseEmitter.event()
                        .name("ready")
                        .data(
                                "Fetch your persisted inbox to reconcile missed notifications"
                        )
        );

        return emitter;
    }

    private void watch() {
        while (running) {
            try (
                    var stream = mongoTemplate
                            .getCollection("notifications")
                            .watch(
                                    List.of(
                                            Aggregates.match(
                                                    Filters.eq(
                                                            "operationType",
                                                            "insert"
                                                    )
                                            )
                                    )
                            )
                            .maxAwaitTime(
                                    2,
                                    TimeUnit.SECONDS
                            )
                            .cursor()
            ) {
                cursor = stream;

                // Mongo change streams expose committed inserts.
                // On reconnection, clients reconcile their persisted inbox.
                resync();

                while (running) {
                    ChangeStreamDocument<Document> change =
                            stream.tryNext();

                    if (change == null
                            || change.getFullDocument() == null) {
                        continue;
                    }

                    Notification notification =
                            mongoTemplate.getConverter().read(
                                    Notification.class,
                                    change.getFullDocument()
                            );

                    NotificationResponse response =
                            NotificationResponse.from(notification);

                    for (SseEmitter emitter :
                            connections(
                                    notification.getRecipientUserId()
                            )) {

                        send(
                                notification.getRecipientUserId(),
                                emitter,
                                SseEmitter.event()
                                        .id(response.id())
                                        .name("notification")
                                        .data(
                                                response,
                                                MediaType.APPLICATION_JSON
                                        )
                        );
                    }
                }

            } catch (RuntimeException exception) {
                if (!running) {
                    return;
                }

                log.warn(
                        "Notification change stream disconnected: {}",
                        exception.getClass().getSimpleName()
                );

                try {
                    Thread.sleep(1000);

                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }

            } finally {
                cursor = null;
            }
        }
    }

    private List<SseEmitter> connections(
            String userId
    ) {
        List<SseEmitter> result = new ArrayList<>();

        clients.computeIfPresent(
                userId,
                (id, connections) -> {
                    result.addAll(connections);
                    return connections;
                }
        );

        return result;
    }

    private void resync() {
        for (String userId : clients.keySet()) {
            for (SseEmitter emitter :
                    connections(userId)) {
                send(
                        userId,
                        emitter,
                        SseEmitter.event()
                                .name("resync")
                                .data("Fetch your persisted inbox")
                );
            }
        }
    }

    private void heartbeat() {
        for (String userId : clients.keySet()) {
            for (SseEmitter emitter :
                    connections(userId)) {
                send(
                        userId,
                        emitter,
                        SseEmitter.event()
                                .comment("heartbeat")
                );
            }
        }
    }

    private void send(
            String userId,
            SseEmitter emitter,
            SseEmitter.SseEventBuilder event
    ) {
        try {
            synchronized (emitter) {
                emitter.send(event);
            }

        } catch (IOException | IllegalStateException exception) {
            remove(userId, emitter);
            emitter.complete();
        }
    }

    private void remove(
            String userId,
            SseEmitter emitter
    ) {
        clients.computeIfPresent(
                userId,
                (id, connections) -> {
                    connections.remove(emitter);

                    return connections.isEmpty()
                            ? null
                            : connections;
                }
        );
    }

    @PreDestroy
    public void stop() {
        running = false;

        if (cursor != null) {
            cursor.close();
        }

        executor.shutdownNow();

        for (String userId :
                List.copyOf(clients.keySet())) {
            for (SseEmitter emitter :
                    connections(userId)) {
                emitter.complete();
            }
        }

        clients.clear();
    }
}