package com.rackspacecloud.metrics.ingestionservice.producer;

import com.rackspace.monplat.protocol.ExternalMetric;
import com.rackspacecloud.metrics.ingestionservice.listeners.rolluplisteners.models.MetricRollup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

@Slf4j
public class Sender {    
    @Autowired
    private KafkaTemplate<String, ExternalMetric> kafkaTemplate;

    @Autowired
    private KafkaTemplate<String, MetricRollup> kafkaTemplateRollup;

    public void send(ExternalMetric payload, String topic) {
        log.info("START: Sending payload [{}]", payload);
        kafkaTemplate.send(topic, payload);
        log.info("FINISH: Processing");
    }

    public void sendRollup(MetricRollup payload, String topic) {
        log.info("START: Sending payload [{}]", payload);
        kafkaTemplateRollup.send(topic, payload);
        log.info("FINISH: Processing");
    }
}
