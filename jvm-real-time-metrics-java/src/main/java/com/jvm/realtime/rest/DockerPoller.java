package com.jvm.realtime.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Event;
import com.github.dockerjava.api.model.Filters;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.EventsResultCallback;
import com.jvm.realtime.config.Config;
import com.jvm.realtime.model.ClientAppSnapshot;
import com.jvm.realtime.model.DockerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.*;

@Component
public class DockerPoller implements DataPoller {


    private DockerClient dockerClient;
    private List<Container> currentContainers;
    private final SimpMessagingTemplate websocket;

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerPoller.class);

    @Autowired
    public DockerPoller(SimpMessagingTemplate websocket) {
        DockerClientConfig config = DockerClientConfig.createDefaultConfigBuilder()
                .withVersion("1.18")
                .withUri("http://" + Config.dockerHost + ":2376")
                .build();
        this.dockerClient = DockerClientBuilder.getInstance(config).build();
        this.currentContainers = Collections.emptyList();
        this.websocket = websocket;
    }

    public void poll() {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                fetchCurrentContainers();
            }
        }, 0, 20000);

    }

    public ClientAppSnapshot getDockerApplicationMetaData(Container container) {
        ClientAppSnapshot currentAppModel = new ClientAppSnapshot();

        Integer publicPort = container.getPorts()[0].getPublicPort();
        String appName = container.getImage();

        currentAppModel.setPublicPort(publicPort);
        currentAppModel.setAppName(appName);
        currentAppModel.setTimeStamp(System.currentTimeMillis());

        return currentAppModel;
    }

    private List<Container> fetchCurrentContainers() {
        try {
            setCurrentContainers(this.dockerClient.listContainersCmd().exec());
        } catch (Exception e) {
            LOGGER.error("Error when polling docker containers endpoint.", e);
        }

        return getCurrentContainers();
    }



    public DockerClient getDockerClient() {
        return dockerClient;
    }

    public List<Container> getCurrentContainers() {
        return currentContainers;
    }

    public void setCurrentContainers(List<Container> currentContainers) {
        this.currentContainers = currentContainers;
    }
}
