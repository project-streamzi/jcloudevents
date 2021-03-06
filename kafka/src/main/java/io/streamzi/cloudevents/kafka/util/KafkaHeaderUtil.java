package io.streamzi.cloudevents.kafka.util;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventBuilder;
import org.aerogear.kafka.serialization.CafdiSerdes;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Serde;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import static java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME;

/**
 * Utility methods for working with CloudEvents and Kafka.
 *
 */
public final class KafkaHeaderUtil {
    public static final String EVENT_TYPE_KEY = "tpe";
    public static final String CLOUD_EVENTS_VERSION_KEY = "specversion";
    public static final String SOURCE_KEY = "source";
    public static final String EVENT_ID_KEY = "id";
    public static final String EVENT_TIME_KEY = "time";
    public static final String SCHEMA_URL_KEY = "schemaurl";
    public static final String CONTENT_TYPE_KEY = "contenttype";
    public static final String EXTENSIONS_KEY = "extensions";
    public static final String DATA_KEY = "data";

    private KafkaHeaderUtil() {
        // no-op
    }

    /**
     * Create Kafka Headers from a CloudEvent
     * @param ce Event to extract the headers from
     * @return Headers that can be used to construct a ProducerRecord
     */
    public static Headers extractHeaders(final CloudEvent<?> ce) {

        final RecordHeaders headers = new RecordHeaders();

        headers.add(new RecordHeader(EVENT_TYPE_KEY, ((Serde) CafdiSerdes.serdeFrom(String.class)).serializer().serialize(null, ce.getType())));
        headers.add(new RecordHeader(CLOUD_EVENTS_VERSION_KEY, ((Serde) CafdiSerdes.serdeFrom(String.class)).serializer().serialize(null, ce.getSpecVersion())));
        headers.add(new RecordHeader(SOURCE_KEY, ((Serde) CafdiSerdes.serdeFrom(String.class)).serializer().serialize(null, ce.getSource().toString())));
        headers.add(new RecordHeader(EVENT_ID_KEY, ((Serde) CafdiSerdes.serdeFrom(String.class)).serializer().serialize(null, ce.getId())));

        if (ce.getSchemaURL().isPresent()) {
            headers.add(new RecordHeader(SCHEMA_URL_KEY, ((Serde) CafdiSerdes.serdeFrom(String.class)).serializer().serialize(null, ce.getSchemaURL().get().toString()) ));
        }

        if (ce.getContentType().isPresent()) {
            headers.add(new RecordHeader(CONTENT_TYPE_KEY, ((Serde) CafdiSerdes.serdeFrom(String.class)).serializer().serialize(null, ce.getContentType().get()) ));
        }

        if (ce.getTime().isPresent()) {
            headers.add(new RecordHeader(EVENT_TIME_KEY, ((Serde) CafdiSerdes.serdeFrom(String.class)).serializer().serialize(null, ce.getTime().get().toString()) ));
        }

        //todo: extensions?

        return headers;
    }

    /**
     * Create a CloudEvent from a message consumed from a Kafka topic. Populates the CloudEvent attributes
     * from the Kafka Headers and the data from the Kafka value.
     * @param record message receieved from Kafka
     * @param <K> Message Key
     * @param <V> Message Value
     * @return CloudEvent representation of the Kafka message
     */
    public static <K, V> CloudEvent<Map<K, V>> createFromConsumerRecord(final ConsumerRecord<K, V> record) {

        final Headers headers = record.headers();
        final CloudEventBuilder builder = new CloudEventBuilder();

        try {

            builder.type(CafdiSerdes.serdeFrom(String.class).deserializer().deserialize(null, headers.lastHeader(EVENT_TYPE_KEY).value()));
            builder.source(URI.create(CafdiSerdes.serdeFrom(String.class).deserializer().deserialize(null, headers.lastHeader(SOURCE_KEY).value())));
            builder.id(CafdiSerdes.serdeFrom(String.class).deserializer().deserialize(null, headers.lastHeader(EVENT_ID_KEY).value()));

            if (headers.lastHeader(EVENT_TIME_KEY) != null) {
                builder.time(ZonedDateTime.parse(CafdiSerdes.serdeFrom(String.class).deserializer().deserialize(null, headers.lastHeader(EVENT_TIME_KEY).value()), ISO_ZONED_DATE_TIME));
            }

            if (headers.lastHeader(SCHEMA_URL_KEY) != null) {
                builder.schemaURL(URI.create(CafdiSerdes.serdeFrom(String.class).deserializer().deserialize(null, headers.lastHeader(SCHEMA_URL_KEY).value())));
            }

            //todo: add extensions

            // use the pure key/value as the 'data' part
            final Map<K, V> rawKafkaRecord = new HashMap();
            rawKafkaRecord.put(record.key(), record.value());
            builder.data(rawKafkaRecord);

            if (headers.lastHeader(CONTENT_TYPE_KEY) != null) {
                builder.contentType(CafdiSerdes.serdeFrom(String.class).deserializer().deserialize(null, headers.lastHeader(CONTENT_TYPE_KEY).value()));
            } else {
                // if nothing was specified we use a 'custom' content type to describe the data format
                builder.contentType("application/ce-kafka-data"); // todo: move to constant
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return builder.build();
    }

}

