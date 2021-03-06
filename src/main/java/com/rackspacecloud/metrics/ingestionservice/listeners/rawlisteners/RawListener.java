package com.rackspacecloud.metrics.ingestionservice.listeners.rawlisteners;

import com.rackspace.monplat.protocol.ExternalMetric;
import com.rackspacecloud.metrics.ingestionservice.exceptions.IngestFailedException;
import com.rackspacecloud.metrics.ingestionservice.exceptions.InvalidDataException;
import com.rackspacecloud.metrics.ingestionservice.influxdb.InfluxDBHelper;
import com.rackspacecloud.metrics.ingestionservice.listeners.UnifiedMetricsListener;
import com.rackspacecloud.metrics.ingestionservice.listeners.processors.TenantIdAndMeasurement;
import com.rackspacecloud.metrics.ingestionservice.listeners.rawlisteners.processors.RawMetricsProcessor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.influxdb.InfluxDB;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Payload;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RawListener extends UnifiedMetricsListener {
    private ConcurrentMap<String, Map<String, Map<String, Long>>> topicPartitionMonitoringSystemRecordsCount =
            new ConcurrentHashMap<>();
    private InfluxDBHelper influxDBHelper;
    private MeterRegistry registry;
    private Timer batchProcessingTimer;

    String localMetricsUrl;
    String localMetricsDatabase;
    String localMetricsRetPolicy;

    private Tag rawListenerTag;
    private String hostName;
    private InfluxDB influxDBCeresWriter;

    private Map<String, Integer> payloadMetrics;
    private static final String PAYLOAD_SIZE = "payloadsize";
    private static final String PAYLOAD_RECORDS_COUNT = "payloadrecordscount";

    @Value("${tenant-routing-service.url}")
    protected static String tenantRoutingServiceUrl;

    public RawListener(InfluxDBHelper influxDBHelper, MeterRegistry registry,
                       String localMetricsUrl, String localMetricsDatabase, String localMetricsRetPolicy) {
        this.localMetricsUrl = localMetricsUrl;
        this.localMetricsDatabase = localMetricsDatabase;
        this.localMetricsRetPolicy = localMetricsRetPolicy;

        this.rawListenerTag = Tag.of("listener", "raw");
        this.registry = registry;
        this.batchProcessingTimer =
                this.registry.timer("ingestion.batch.processing", Arrays.asList(rawListenerTag));
        this.influxDBHelper = influxDBHelper;
        this.payloadMetrics = new HashMap<>();
        this.influxDBCeresWriter = influxDBHelper.getInfluxDBFactory().getInfluxDB(localMetricsUrl);
        try {
            this.hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    /**
     * This listener listens to unified.metrics.json topic.
     * @param records
     */
    @KafkaListener(
            topics = "${kafka.topics.in}",
            containerFactory = "batchFactory",
            errorHandler = "listenerErrorHandler"
    )
    public void listenUnifiedMetricsTopic(
            @Payload final List<Message<ExternalMetric>> records, final Acknowledgment ack)
        throws InvalidDataException {

        long batchProcessingStartTime = System.currentTimeMillis();
        batchProcessedCount++;

        topicPartitionMonitoringSystemRecordsCount.clear(); // start clean every call

        // Prepare the payloads to ingest
        Map<TenantIdAndMeasurement, List<String>> tenantPayloadsMap =
                RawMetricsProcessor.getTenantPayloadsMap(records, topicPartitionMonitoringSystemRecordsCount);

        writeIntoInfluxDb(tenantPayloadsMap);

        processPostInfluxDbIngestion(records, ack);

        batchProcessingTimer.record(System.currentTimeMillis() - batchProcessingStartTime, TimeUnit.MILLISECONDS);

        // Post topic-partition-monitoringSystem metrics to ceres database
        List<String> lineProtocoledCollection = new ArrayList<>();

        lineProtocoledCollection.add(String.format("ingestion_records_count,listener=raw,hostname=%s count=%d",
                this.hostName, records.size()));
        lineProtocoledCollection.add(String.format("ingestion_payload,listener=raw,hostname=%s %s=%d,%s=%d",
                this.hostName, PAYLOAD_RECORDS_COUNT, payloadMetrics.get(PAYLOAD_RECORDS_COUNT),
                        PAYLOAD_SIZE, payloadMetrics.get(PAYLOAD_SIZE)));

        topicPartitionMonitoringSystemRecordsCount.forEach((topic, pMap) -> {
            pMap.forEach((partition, msMap) -> {
                msMap.forEach((monitoringSystem, count) -> {
                    String tags = String.format("topic=%s,partitionid=%s,monitoringsystem=%s",
                            topic, partition, monitoringSystem);

                    lineProtocoledCollection.add(String.format("ingested_data_count,%s count=%d", tags, count));
                });
            });
        });

        String metricsToPublish = String.join("\n", lineProtocoledCollection);

        influxDBCeresWriter.write(localMetricsDatabase, localMetricsRetPolicy,
                InfluxDB.ConsistencyLevel.ONE, TimeUnit.SECONDS, metricsToPublish);
    }

    private void writeIntoInfluxDb(Map<TenantIdAndMeasurement, List<String>> tenantPayloadsMap) {

        for(Map.Entry<TenantIdAndMeasurement, List<String>> entry : tenantPayloadsMap.entrySet()) {
            TenantIdAndMeasurement tenantIdAndMeasurement = entry.getKey();
            String payload = String.join("\n", entry.getValue());
            payloadMetrics.put(PAYLOAD_RECORDS_COUNT, entry.getValue().size());
            payloadMetrics.put(PAYLOAD_SIZE, payload.getBytes().length);

            try {
                // cleanup tenantIdAndMeasurement by replacing any special character
                // with "_" before passing it to the function
                influxDBHelper.ingestToInfluxDb(
                        payload, tenantIdAndMeasurement.getTenantId(),
                        tenantIdAndMeasurement.getMeasurement(), "full");
                // TODO: make enum for rollup level

            } catch (IngestFailedException e) {
                log.error("Write to InfluxDB failed with Payload [{}] \n [{}]", payload, e);
            }
        }
    }
}
