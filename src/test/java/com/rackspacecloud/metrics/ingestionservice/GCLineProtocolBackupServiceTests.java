package com.rackspacecloud.metrics.ingestionservice;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.rackspacecloud.metrics.ingestionservice.influxdb.GCLineProtocolBackupService;
import com.rackspacecloud.metrics.ingestionservice.influxdb.config.BackupProperties;
import com.rackspacecloud.metrics.ingestionservice.influxdb.providers.LineProtocolBackupService;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles(value = { "test" })
@DirtiesContext
@EmbeddedKafka(partitions = 1, topics = { IngestionServiceApplicationTests.UNIFIED_METRICS_TOPIC })
@EnableConfigurationProperties(BackupProperties.class)
public class GCLineProtocolBackupServiceTests {

    @Autowired
    private LineProtocolBackupService backupService;

    @Autowired
    private Storage storage;

    @Autowired
    private BackupProperties backupProperties;

    @Before
    public void setUp() {
        // Purge any remaining buffers
        backupService.flush();
        
        // Clear all storage
        if(storage.list(backupProperties.getGcsBackupBucket())!=null) {
            storage.list(backupProperties.getGcsBackupBucket()).iterateAll().forEach(blob -> blob.delete());
        }
    }

    @Test
    public void configurationTest() {
        assertThat(backupProperties.getMaxCacheSize() > 100);
        assertTrue(backupProperties.isBackupEnabled());
    }

    @Test
    public void backupServiceGetProperName() throws MalformedURLException, UnsupportedEncodingException {
        assertThat(GCLineProtocolBackupService.getBackupLocation("testPayload 1557777267",
                new URL("https://influx-test.com:8080"), "myDB", "1440h"))
                        .matches("influx-test.com/myDB/1440h/20190513");
    }

    @Test
    public void backupServiceGetCachedStream() throws IOException {
        GZIPOutputStream outputStream1 = backupService.getBackupStream("testFile1", "db0", "rp0");
        GZIPOutputStream outputStream2 = backupService.getBackupStream("testFile1", "db0", "rp0");
        GZIPOutputStream outputStream3 = backupService.getBackupStream("testFile1", "db0", "rp0");
        assertThat(outputStream1).isEqualTo(outputStream2);
        assertThat(outputStream2).isEqualTo(outputStream3);
    }

    @Test
    public void backupServiceGetCachedStream2() throws IOException {
        GZIPOutputStream outputStream1 = backupService.getBackupStream("testFile1", "db0", "rp0");
        GZIPOutputStream outputStream2 = backupService.getBackupStream("testFile1", "db0", "rp0");
        GZIPOutputStream outputStream3 = backupService.getBackupStream("testFile2", "db0", "rp0");
        assertThat(outputStream1).isEqualTo(outputStream2);
        assertThat(outputStream2).isNotEqualTo(outputStream3);
    }

    @Test
    public void checkFile() throws IOException {
        GZIPOutputStream outputStream1 = backupService.getBackupStream("testFile1", "db0", "rp0");
        outputStream1.write("test1".getBytes());
        outputStream1.close();
        assertThat(IOUtils.toString(new GZIPInputStream(Channels.newInputStream(storage.reader(backupProperties.getGcsBackupBucket(),
                storage.list(backupProperties.getGcsBackupBucket()).getValues().iterator().next().getName()))))).contains("test1");
    }

    @Test
    public void checkBucket() {
        assertThat(backupProperties.getGcsBackupBucket()).isEqualTo("ceres-backup-dev");
    }

    @Test(expected = StorageException.class)
    public void testWriteAndRead() throws IOException {
        GZIPOutputStream outputStream1 = backupService.getBackupStream("testFile1", "db0", "rp0");
        outputStream1.write("test1".getBytes());
        IOUtils.toString(new GZIPInputStream(Channels.newInputStream(storage.reader(backupProperties.getGcsBackupBucket(),
                storage.list(backupProperties.getGcsBackupBucket()).getValues().iterator().next().getName()))));
    }

    @Test
    public void testCacheClear() throws IOException, InterruptedException {
        GZIPOutputStream outputStream1 = backupService.getBackupStream("testFile1", "db0", "rp0");
        outputStream1.write("test1".getBytes());
        backupService.flush();

        TimeUnit.SECONDS.sleep(1);
        
        assertThat(IOUtils.toString(new GZIPInputStream(Channels.newInputStream(storage.reader(backupProperties.getGcsBackupBucket(),
                storage.list(backupProperties.getGcsBackupBucket()).getValues().iterator().next().getName()))))).contains("test1");
    }

    // This test will fail when the cache is not working properly, i.e. re-issuing multiple files instead of caching.
    @Test
    public void testServiceTwoDbInstances() throws IOException, InterruptedException {
        backupService.writeToBackup("testPayload11 1557777267",
                new URL("https://influx-test1.com:8080"),
                "myDB1", "1440h");
        backupService.writeToBackup("testPayload12 1557777268",
                new URL("https://influx-test1.com:8080"),
                "myDB1", "1440h");
        backupService.writeToBackup("testPayload13 1557777269",
                new URL("https://influx-test1.com:8080"),
                "myDB1", "1440h");

        backupService.writeToBackup("testPayload21 1557777267",
                new URL("https://influx-test2.com:8080"),
                "myDB2", "1440h");
        backupService.writeToBackup("testPayload22 1557777268",
                new URL("https://influx-test2.com:8080"),
                "myDB2", "1440h");
        backupService.writeToBackup("testPayload23 1557777269",
                new URL("https://influx-test2.com:8080"),
                "myDB2", "1440h");

        backupService.flush();

        TimeUnit.SECONDS.sleep(1);

        Iterator<Blob> iterator = storage.list(backupProperties.getGcsBackupBucket()).getValues().iterator();

        List<Blob> blobList = new ArrayList<>();
        iterator.forEachRemaining(blobList::add);

        assertThat(blobList.size()).isEqualTo(2);

        String blob1 = blobList.get(0).getName();
        String blob2 = blobList.get(1).getName();

        if (blob1.contains("myDB2")) {
            String blob = blob1;
            blob1 = blob2;
            blob2 = blob;
        }

        TimeUnit.SECONDS.sleep(1);

        assertThat(IOUtils.toString(new GZIPInputStream(Channels.newInputStream(storage.reader(backupProperties.getGcsBackupBucket(), blob1)))))
                .matches("# DML\n" +
                        "# CONTEXT-DATABASE: myDB1\n" +
                        "# CONTEXT-RETENTION-POLICY: 1440h\n" +
                        "testPayload[12]1 1557777267\ntestPayload[12]2 1557777268\ntestPayload[12]3 1557777269\n");
        assertThat(IOUtils.toString(new GZIPInputStream(Channels.newInputStream(storage.reader(backupProperties.getGcsBackupBucket(), blob2)))))
                .matches("# DML\n" +
                        "# CONTEXT-DATABASE: myDB2\n" +
                        "# CONTEXT-RETENTION-POLICY: 1440h\n" +
                        "testPayload[12]1 1557777267\ntestPayload[12]2 1557777268\ntestPayload[12]3 1557777269\n");
    }
}
