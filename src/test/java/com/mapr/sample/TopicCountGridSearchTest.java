package com.mapr.sample;

import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Random;

/**
 * Performance tests intended to explore effect of number of output topics, buffer size,
 * threading and so on.
 */
@RunWith(Parameterized.class)
public class TopicCountGridSearchTest {

    @Parameterized.Parameters(name = "{index}: fib({0})={1}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {0, 100, 100}, {0, 300, 100},
                {16384, 100, 100}, {16384, 300, 100}
        });
    }

    @BeforeClass
    public static void openDataFile() throws FileNotFoundException {
        data = new PrintWriter(new File("topic-count.csv"));
        data.printf("batchSize, topicCount, messageSize, i, t, rate, dt, batchRate\n");
    }

    @AfterClass
    public static void closeDataFile() {
        data.close();
    }

    private static PrintWriter data;

    private int batchSize;
    private int topicCount;
    private int messageSize;

    public TopicCountGridSearchTest(int batchSize, int topicCount, int messageSize) {
        this.batchSize = batchSize;
        this.topicCount = topicCount;
        this.messageSize = messageSize;
    }

    @Test
    public void testSpeed() throws IOException {
        System.out.printf("batchSize = %d, topicCount = %d\n", batchSize, topicCount);

        List<String> ourTopics = Lists.newArrayList();
        for (int i = 0; i < topicCount; i++) {
            ourTopics.add(String.format("t-%05d", i));
        }
        Random rand = new Random();

        byte[] buf = new byte[messageSize];
        rand.nextBytes(buf);
        Tick message = new Tick(buf);

        KafkaProducer<String, byte[]> producer = getProducer();

        double t0 = System.nanoTime() * 1e-9;
        double batchStart = 0;
        double timeout = 15;

        int batch = 500000;

        for (int i = 0; i < 1e8; ) {
            for (int j = 0; j < batch; j++) {
                String topic = ourTopics.get(rand.nextInt(topicCount));
                producer.send(new ProducerRecord<>(topic, message.getData()));
            }
            double t = System.nanoTime() * 1e-9 - t0;
            double dt = t - batchStart;
            i += batch;
            batchStart = t;
            data.printf("%d,%d,%d,%d,%.3f,%.1f,%.3f,%.1f\n", batchSize, topicCount, messageSize, i, t, i / t, dt, batch / dt);
            data.flush();
            if (t > timeout) {
                break;
            }
        }
    }

    KafkaProducer<String, byte[]> getProducer() throws IOException {
        Properties p = new Properties();
        p.load(Resources.getResource("producer.props").openStream());

        if (batchSize > 0) {
            p.setProperty("batch.size", String.valueOf(batchSize));
        }
        return new KafkaProducer<>(p);
    }
}
