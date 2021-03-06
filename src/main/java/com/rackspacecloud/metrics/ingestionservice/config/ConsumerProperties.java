package com.rackspacecloud.metrics.ingestionservice.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

/**
 * Purpose of this class is to create common configuration for consumers.
 */
@Slf4j
public abstract class ConsumerProperties {
    public Map<String, Object> properties;
    public ConsumerConfigurationProperties configurationProperties;

    public ConsumerProperties(ConsumerConfigurationProperties configProps){
        this.properties = new HashMap<>();
        this.configurationProperties = configProps;

        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, configProps.getServers());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, configProps.getSessionTimeoutMsConfig());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, configProps.getMaxPollRecordsConfig());
        properties.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, configProps.getFetchMinBytesConfig());
        properties.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, configProps.getFetchMaxWaitMsConfig());
        properties.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, configProps.getMaxPartitionFetchBytesConfig());
        properties.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, configProps.getHeartbeatIntervalMsConfig());
        properties.put(ConsumerConfig.CLIENT_ID_CONFIG, getHostName());
    }

    public void addSslConfig(){
        properties.put("ssl.keystore.location", configurationProperties.getSsl().getKeystoreLocation());
        properties.put("ssl.keystore.password", configurationProperties.getSsl().getKeystorePassword());
        properties.put("ssl.truststore.location", configurationProperties.getSsl().getTruststoreLocation());
        properties.put("ssl.truststore.password", configurationProperties.getSsl().getTruststorePassword());
        properties.put("ssl.key.password", configurationProperties.getSsl().getKeyPassword());
        properties.put("security.protocol", configurationProperties.getProperties().getSecurityProtocol());
    }

    private String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.error("Couldn't get hostname. [{}]", e.getMessage());
        }

        return "";
    }
}
