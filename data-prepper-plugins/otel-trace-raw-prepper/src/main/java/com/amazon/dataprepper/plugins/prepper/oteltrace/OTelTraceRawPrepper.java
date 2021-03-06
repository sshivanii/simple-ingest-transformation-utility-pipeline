package com.amazon.dataprepper.plugins.prepper.oteltrace;

import com.amazon.dataprepper.model.PluginType;
import com.amazon.dataprepper.model.annotations.DataPrepperPlugin;
import com.amazon.dataprepper.model.configuration.PluginSetting;
import com.amazon.dataprepper.model.prepper.AbstractPrepper;
import com.amazon.dataprepper.model.record.Record;
import com.amazon.dataprepper.plugins.prepper.oteltrace.model.OTelProtoHelper;
import com.amazon.dataprepper.plugins.prepper.oteltrace.model.RawSpanBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.trace.v1.InstrumentationLibrarySpans;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


@DataPrepperPlugin(name = "otel_trace_raw_prepper", type = PluginType.PREPPER)
public class OTelTraceRawPrepper extends AbstractPrepper<Record<ExportTraceServiceRequest>, Record<String>> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String INSTRUMENTATION_LIBRARY_SPANS = "instrumentationLibrarySpans";
    private static final String INSTRUMENTATION_LIBRARY = "instrumentationLibrary";
    private static final String SPANS = "spans";
    private static final String RESOURCE = "resource";
    private static final String ATTRIBUTES = "attributes";
    private static final String START_TIME_UNIX_NANOS = "startTimeUnixNano";
    private static final String END_TIME_UNIX_NANOS = "endTimeUnixNano";
    private static final String START_TIME = "startTime";
    private static final String END_TIME = "endTime";
    private static final BigDecimal MILLIS_TO_NANOS = new BigDecimal(1_000_000);
    private static final BigDecimal SEC_TO_MILLIS = new BigDecimal(1_000);
    private static final Logger log = LoggerFactory.getLogger(OTelTraceRawPrepper.class);

    public static final String SPAN_PROCESSING_ERRORS = "spanProcessingErrors";
    public static final String RESOURCE_SPANS_PROCESSING_ERRORS = "resourceSpansProcessingErrors";
    public static final String TOTAL_PROCESSING_ERRORS = "totalProcessingErrors";

    private final Counter spanErrorsCounter;
    private final Counter resourceSpanErrorsCounter;
    private final Counter totalProcessingErrorsCounter;


    //TODO: https://github.com/opendistro-for-elasticsearch/simple-ingest-transformation-utility-pipeline/issues/66
    public OTelTraceRawPrepper(final PluginSetting pluginSetting) {
        super(pluginSetting);
        spanErrorsCounter = pluginMetrics.counter(SPAN_PROCESSING_ERRORS);
        resourceSpanErrorsCounter = pluginMetrics.counter(RESOURCE_SPANS_PROCESSING_ERRORS);
        totalProcessingErrorsCounter = pluginMetrics.counter(TOTAL_PROCESSING_ERRORS);
    }


    /**
     * execute the prepper logic which could potentially modify the incoming record. The level to which the record has
     * been modified depends on the implementation
     *
     * @param records Input records that will be modified/processed
     * @return Record  modified output records
     */
    @Override
    public Collection<Record<String>> doExecute(Collection<Record<ExportTraceServiceRequest>> records) {
        final List<Record<String>> finalRecords = new LinkedList<>();
        for (Record<ExportTraceServiceRequest> ets : records) {
            for (ResourceSpans rs : ets.getData().getResourceSpansList()) {
                try {
                    final String serviceName = OTelProtoHelper.getServiceName(rs.getResource()).orElse(null);
                    final Map<String, Object> resourceAttributes = OTelProtoHelper.getResourceAttributes(rs.getResource());
                    for (InstrumentationLibrarySpans is : rs.getInstrumentationLibrarySpansList()) {
                        for (Span sp : is.getSpansList()) {
                            try {
                                finalRecords.add(new Record<>(new RawSpanBuilder()
                                        .setFromSpan(sp, is.getInstrumentationLibrary(), serviceName, resourceAttributes)
                                        .build().toJson()));
                            } catch (Exception ex) {
                                log.error("Unable to process invalid Span {}:", sp, ex);
                                spanErrorsCounter.increment();
                                totalProcessingErrorsCounter.increment();
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.error("Unable to process invalid ResourceSpan {} :", rs, ex);
                    resourceSpanErrorsCounter.increment();
                    totalProcessingErrorsCounter.increment();
                }
            }
        }
        return finalRecords;
    }

    @Override
    public void shutdown() {

    }
}
