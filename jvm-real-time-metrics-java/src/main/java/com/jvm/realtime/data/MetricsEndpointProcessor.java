package com.jvm.realtime.data;

import com.github.dockerjava.api.model.Container;
import com.jvm.realtime.config.Config;
import com.jvm.realtime.config.ConfigurationProps;
import com.jvm.realtime.model.ClientAppSnapshot;
import com.jvm.realtime.service.AlertService;
import com.jvm.realtime.websocket.WebSocketConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class MetricsEndpointProcessor implements DataProcessor {

    private RestTemplate restTemplate;
    private DockerProcessor dockerPoller;
    private final SimpMessagingTemplate websocket;
    private ConfigurationProps configurationProps;
    private AlertService alertService;

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsEndpointProcessor.class);

    /**
     * Constructor.
     * @param dockerPoller The docker processor to get the current docker applications from.
     * @param websocket Websocket connection for sending real time data.
     * @param configurationProps Dynamic configuration properties for connecting to docker.
     * @param alertService The alertService to check for alerts on the latest metrics.
     */
    @Autowired
    public MetricsEndpointProcessor(DockerProcessor dockerPoller,
                                    SimpMessagingTemplate websocket,
                                    ConfigurationProps configurationProps,
                                    AlertService alertService) {
        this.restTemplate = new RestTemplate();
        this.dockerPoller = dockerPoller;
        this.websocket = websocket;
        this.configurationProps = configurationProps;
        this.alertService = alertService;
    }

    public void poll() {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                // Get the metrics from each application running in the environment.
                Set<ClientAppSnapshot> currentApplicationMetrics = retrieveActuatorMetricsFromDockerHosts();
                // Send the latest data over the websocket.
                transmitLatestSnapshotOverWebsocket(currentApplicationMetrics);
            }
        }, new Date(), 3000);
    }

    /**
     * Retrieve spring boot actuator metrics from the docker hosts and send them over the websocket to be used by the
     * client.
     */
    public Set<ClientAppSnapshot> retrieveActuatorMetricsFromDockerHosts() {
        Set<ClientAppSnapshot> currentClientAppSnapshots = new HashSet<>();

        for (Container container : this.dockerPoller.getCurrentContainers()) {

            ClientAppSnapshot currentAppModel = this.dockerPoller.getDockerApplicationMetaData(container);

            // Do the HTTP request to get metrics from spring boot actuator.
            String metricsUrl = String.format("http://%s:%s/metrics", configurationProps.getDockerHost() , currentAppModel.getPublicPort());


            try {
                Map<String, Object> metricsMap = restTemplate.getForObject(metricsUrl, Map.class);
                Map<String, Object> formattedMetricsMap = new HashMap<>();

                // Mongo doesn't accept dots in map keys, this loop simply removes them.
                for (Map.Entry<String, Object> metric: metricsMap.entrySet()) {
                    formattedMetricsMap.put(metric.getKey().replace(".", ""), metric.getValue());
                }

                // Set the latest metrics on the clientAppSnapshot object.
                currentAppModel.setActuatorMetrics(formattedMetricsMap);
                // Check if any of the latest metrics trigger any alerts set by the user.
                alertService.checkForAlerts(formattedMetricsMap);

                currentClientAppSnapshots.add(currentAppModel);

            } catch (RestClientException e) {
                LOGGER.error("Error connecting to docker host at " + metricsUrl, e);
            }
        }

        return currentClientAppSnapshots;
    }

    /**
     * Send the latest client application data over the websocket to update the front end of the application.
     * @param currentClientAppSnapshots List of ClientAppSnapshot objects, sent as JSON.
     */
    private void transmitLatestSnapshotOverWebsocket(Set<ClientAppSnapshot> currentClientAppSnapshots) {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        String currentTime = sdf.format(cal.getTime());

        if (!currentClientAppSnapshots.isEmpty()) {
            LOGGER.info("Real time application snapshots being transmitted over websocket");
            websocket.convertAndSend(WebSocketConfiguration.MESSAGE_PREFIX + "/metricsUpdate", currentClientAppSnapshots);
            LOGGER.info("Real time application snapshots transmitted over websocket at " + currentTime);
        } else {
            websocket.convertAndSend(WebSocketConfiguration.MESSAGE_PREFIX + "/metricsUpdate", Collections.emptyList());
            LOGGER.warn("No docker hosts currently available at " + currentTime);
        }
    }



}
