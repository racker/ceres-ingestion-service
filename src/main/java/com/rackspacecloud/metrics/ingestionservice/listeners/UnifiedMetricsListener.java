package com.rackspacecloud.metrics.ingestionservice.listeners;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
public class UnifiedMetricsListener implements ConsumerSeekAware {

    protected long batchProcessedCount = 0;

    // At the end of every 1000 messages, log this information
    protected static final int MESSAGE_PROCESS_REPORT_COUNT = 1000;

    @Override
    public void registerSeekCallback(ConsumerSeekCallback consumerSeekCallback) {
        log.info("Registering seekCallback at [{}]", Instant.now());
    }

    protected void processPostInfluxDbIngestion(final List<?> records, final Acknowledgment ack) {

        ack.acknowledge();

        if (batchProcessedCount % MESSAGE_PROCESS_REPORT_COUNT == 0) {
            log.info("Processed {} batches.", batchProcessedCount);
        }

        log.debug("Done processing for records:{}", records);

        // Reset the counter
        if(batchProcessedCount == Long.MAX_VALUE) batchProcessedCount = 0;

        if(batchProcessedCount % MESSAGE_PROCESS_REPORT_COUNT == 0) {
            log.info("Processed {} batches so far after start or reset...", getBatchProcessedCount());
        }
    }

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> map, ConsumerSeekCallback consumerSeekCallback) {
        for(TopicPartition topicPartition : map.keySet()) {
            String topic = topicPartition.topic();
            int partition = topicPartition.partition();
            long offset = map.get(topicPartition);
            log.info("At Partition assignment for topic [{}], partition [{}], offset is at [{}] at time [{}]",
                    topic, partition, offset, Instant.now());
        }
    }

    @Override
    public void onIdleContainer(Map<TopicPartition, Long> map, ConsumerSeekCallback consumerSeekCallback) {
        log.info("Listener container is idle at [{}]", Instant.now());
    }

    /**
     * Get the current count of the total message processed by the consumer
     * @return
     */
    public long getBatchProcessedCount(){
        return batchProcessedCount;
    }

    public static String replaceSpecialCharacters(String inputString){
        final String[] metaCharacters =
                {"\\",":","^","$","{","}","[","]","(",")",".","*","+","?","|","<",">","-","&","%"," "};

        for (int i = 0 ; i < metaCharacters.length ; i++){
            if(inputString.contains(metaCharacters[i])){
                inputString = inputString.replace(metaCharacters[i],"_");
            }
        }
        return inputString;
    }
}
