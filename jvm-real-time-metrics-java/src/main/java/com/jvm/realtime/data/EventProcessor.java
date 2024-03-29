package com.jvm.realtime.data;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Event;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.EventsResultCallback;
import com.jvm.realtime.config.Config;
import com.jvm.realtime.config.ConfigurationProps;
import com.jvm.realtime.model.DockerEvent;
import com.jvm.realtime.persistence.EventRepository;
import com.jvm.realtime.websocket.WebSocketConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class EventProcessor implements DataProcessor {

    private DockerClient dockerClient;
    private final EventRepository eventRepository;
    private final SimpMessagingTemplate websocket;
    private ConfigurationProps configurationProps;

    private static final Logger LOGGER = LoggerFactory.getLogger(EventProcessor.class);

    /**
     *
     * @param eventRepository The persistence layer for storing events.
     * @param websocket The websocket connection for sending real time data.
     * @param configurationProps Dynamic configuration properties for connecting to docker.
     */
    @Autowired
    public EventProcessor(EventRepository eventRepository, SimpMessagingTemplate websocket, ConfigurationProps configurationProps) {
        this.configurationProps = configurationProps;
        DockerClientConfig config = DockerClientConfig.createDefaultConfigBuilder()
                .withVersion("1.18")
                .withUri(String.format("http://%s:%s", configurationProps.getDockerHost() , configurationProps.getDockerPort()))
                .build();
        this.dockerClient = DockerClientBuilder.getInstance(config).build();
        this.eventRepository = eventRepository;
        this.websocket = websocket;
    }

    @Override
    public void poll() {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                updateDockerEvents();
            }
        }, 0, 5000);
    }

    /**
     * Update the docker events every time they come in, by sending them over the websocket.
     */
    private void updateDockerEvents() {
        EventsCallback eventCallback = new EventsCallback();
        try {
            this.dockerClient.eventsCmd()
                    .withSince("1374067924")
                    .exec(eventCallback);

        } catch (Exception e) {
            LOGGER.error("Exception getting events from docker API", e);
        }
    }

    /**
     * The callback that is executed every time a new Docker event comes in.
     * The event gets saved to the database and then sent over the websocket to update the user interface.
     */
    private class EventsCallback extends EventsResultCallback {
        public void onNext(Event event) {
            DockerEvent currentEvent = new DockerEvent(
                    event.getStatus(),
                    event.getFrom(),
                    event.getTime()
            );
            eventRepository.save(currentEvent);
            websocket.convertAndSend(WebSocketConfiguration.MESSAGE_PREFIX + "/eventsUpdate", currentEvent);
            LOGGER.info("Received event : {} from image {} at {}",
                    currentEvent.getStatus(),
                    currentEvent.getImage(),
                    System.currentTimeMillis());
        }
    }

}
