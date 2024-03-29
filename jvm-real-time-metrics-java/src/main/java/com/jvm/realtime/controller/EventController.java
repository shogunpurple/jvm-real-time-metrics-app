package com.jvm.realtime.controller;

import com.github.dockerjava.api.model.Event;
import com.google.common.collect.Lists;
import com.jvm.realtime.model.DockerEvent;
import com.jvm.realtime.persistence.ClientAppSnapshotRepository;
import com.jvm.realtime.persistence.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api")
public class EventController {

    @Autowired
    EventRepository eventRepository;

    @RequestMapping(value = "/events/all", method = RequestMethod.GET)
    public List<DockerEvent> getAllEvents() {
        return eventRepository.findAll();
    }

    @RequestMapping(value = "/events", method = RequestMethod.GET)
    public List<DockerEvent> getEventsForApp(@RequestParam(value = "appName") String appName) {
       return eventRepository.findByImage(appName);
    }

    @RequestMapping(value = "/events/mostRecent", method = RequestMethod.GET)
    public List<DockerEvent> getMostRecentEvent() {
        return Lists.newArrayList(eventRepository.findTopByOrderByTimeDesc());
    }
}
