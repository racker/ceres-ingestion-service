package com.rackspacecloud.metrics.ingestionservice.producer;

import com.rackspace.monplat.protocol.AccountType;
import com.rackspace.monplat.protocol.ExternalMetric;
import com.rackspace.monplat.protocol.MonitoringSystem;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class MockMetricHelper {

    public static ExternalMetric getValidMetric(
            int i, String accountType, String account,
            int randomNum, boolean wantIValues){

        ExternalMetric metric = new ExternalMetric();

        metric.setAccount(account);
        metric.setAccountType(Enum.valueOf(AccountType.class, accountType));

        metric.setDevice((randomNum + i) + "");
        metric.setDeviceLabel("dummy-device-label-" + randomNum + "-" + i);
        metric.setDeviceMetadata(new HashMap<>());
        metric.setMonitoringSystem(MonitoringSystem.MAAS);

        Map<String, String> systemMetadata = new HashMap<>();
        systemMetadata.put("accountId", "dummy-account-id-" + randomNum + "-" + i);
        systemMetadata.put("entityId", "dummy-entity-id-" + randomNum + "-" + i);
        systemMetadata.put("checkId", "dummy-check-id-" + randomNum + "-" + i);
        systemMetadata.put("monitoringZone", "");
        metric.setSystemMetadata(systemMetadata);

        metric.setCollectionName("agent.filesystem" + "." + i);

        metric.setCollectionLabel("dummy-collection-label");
        metric.setCollectionTarget("");

        Map<String, String> collectionMetadata = new HashMap<>();
        collectionMetadata.put("rpc_maas_version", "1.7.7");
        collectionMetadata.put("rpc_maas_deploy_date", "2018-10-04");
        collectionMetadata.put("rpc_check_category", "host");
        collectionMetadata.put("product", "osa");
        collectionMetadata.put("osa_version", "14.2.4");
        collectionMetadata.put("rpc_env_identifier", "as-c");
        metric.setCollectionMetadata(collectionMetadata);

        Map<String, Long> iValues = new HashMap<>();

        if(wantIValues) iValues = getIValues();

        metric.setIvalues(iValues);

        metric.setFvalues(new HashMap<>());
        metric.setSvalues(new HashMap<>());

        Map<String, String> units = new HashMap<>();
        units.put("filesystem.free_files", "free_files");
        units.put("filesystem.files", "files");
        units.put("filesystem.total", "KILOBYTES");
        units.put("filesystem.free", "KILOBYTES");
        units.put("filesystem.avail", "KILOBYTES");
        units.put("filesystem.used", "KILOBYTES");

        metric.setUnits(units);
        metric.setTimestamp(Instant.now().toString());

        return metric;
    }

    private static Map<String, Long> getIValues() {
        Map<String, Long> iValues = new HashMap<>();
        iValues.put("filesystem.total", getNextLongValue());
        iValues.put("filesystem.free", getNextLongValue());
        iValues.put("filesystem.free_files", getNextLongValue());
        iValues.put("filesystem.avail", getNextLongValue());
        iValues.put("filesystem.files", getNextLongValue());
        iValues.put("filesystem.used", getNextLongValue());
        return iValues;
    }

    private static long getNextLongValue() {
        return ThreadLocalRandom.current().nextLong(1000L, 50_000L);
    }

}
