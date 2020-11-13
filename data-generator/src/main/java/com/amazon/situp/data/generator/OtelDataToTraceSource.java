package com.amazon.situp.data.generator;

import com.google.protobuf.ByteString;
import com.linecorp.armeria.client.Clients;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.InstrumentationLibrarySpans;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.Span;

import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class OtelDataToTraceSource {

    private static final Random RANDOM = new Random();
    private static final List<Span.SpanKind> SPAN_KINDS =
            Arrays.asList(Span.SpanKind.SPAN_KIND_CLIENT, Span.SpanKind.SPAN_KIND_CONSUMER, Span.SpanKind.SPAN_KIND_INTERNAL, Span.SpanKind.SPAN_KIND_PRODUCER, Span.SpanKind.SPAN_KIND_SERVER);

    /**
     * Build ExportTraceServiceRequest object, an array of ResourceSpans
     *
     * @param spans
     * @return ExportTraceServiceRequest
     */
    public static ExportTraceServiceRequest getExportTraceServiceRequest(List<ResourceSpans> spans) {
        return ExportTraceServiceRequest.newBuilder()
                .addAllResourceSpans(spans)
                .build();
    }

    private static byte[] getRandomBytes(int len) {
        final byte[] bytes = new byte[len];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    /**
     * Returns a list of random ResourceSpans
     *
     * @return List<ResourceSpans>
     */
    private static List<ResourceSpans> getRandomResourceSpans(int size) throws UnsupportedEncodingException {
        final ArrayList<ResourceSpans> spansList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            spansList.add(
                    getResourceSpans(
                            UUID.randomUUID().toString(),
                            UUID.randomUUID().toString(),
                            getRandomBytes(8),
                            getRandomBytes(8),
                            getRandomBytes(16),
                            SPAN_KINDS.get(RANDOM.nextInt(SPAN_KINDS.size())),
                            Instant.now().toEpochMilli()*1000000L,
                            Instant.now().toEpochMilli()*1000000L
                    )
            );
        }
        return spansList;
    }

    /**
     * Builds a ResourceSpan
     *
     * @return ResourceSpan
     * @params serviceName, spanName, spanId, parentId, traceId, spanKind
     */
    public static ResourceSpans getResourceSpans(
            final String serviceName, final String spanName, final byte[] spanId, final byte[] parentId,
            final byte[] traceId, final Span.SpanKind spanKind, final long startTime, final long endTime)
            throws UnsupportedEncodingException {
        final ByteString parentSpanId = parentId != null ? ByteString.copyFrom(parentId) : ByteString.EMPTY;
        return ResourceSpans.newBuilder()
                .setResource(
                        Resource.newBuilder()
                                .addAttributes(KeyValue.newBuilder()
                                        .setKey("service.name")
                                        .setValue(AnyValue.newBuilder().setStringValue(serviceName).build()).build())
                                .build()
                )
                .addInstrumentationLibrarySpans(
                        0,
                        InstrumentationLibrarySpans.newBuilder()
                                .addSpans(
                                        Span.newBuilder()
                                                .setName(spanName)
                                                .setKind(spanKind)
                                                .setSpanId(ByteString.copyFrom(spanId))
                                                .setParentSpanId(parentSpanId)
                                                .setTraceId(ByteString.copyFrom(traceId))
                                                .setStartTimeUnixNano(startTime)
                                                .setEndTimeUnixNano(endTime)
                                                .build()
                                )
                                .build()
                )
                .build();
    }

    private static void sendExportTraceServiceRequestToSource(String URL, ExportTraceServiceRequest request, final TraceServiceGrpc.TraceServiceBlockingStub client) {
        client.export(request);
    }

    public static void main(String[] args) throws UnsupportedEncodingException, InterruptedException {
        String URL = args[0];
        int batchSize = Integer.parseInt(args[1]);
        int batchesPerSecond = Integer.parseInt(args[2]);
        long millisPerBatch = 1000L/(long)batchesPerSecond;
        final TraceServiceGrpc.TraceServiceBlockingStub client = Clients.newClient(URL, TraceServiceGrpc.TraceServiceBlockingStub.class);
        long nextBatch = System.currentTimeMillis();
        while (true) {
            try {
                if(System.currentTimeMillis() < nextBatch) {
                    Thread.sleep(nextBatch - System.currentTimeMillis());
                }
                nextBatch += millisPerBatch;
                final ExportTraceServiceRequest exportTraceServiceRequest =
                        getExportTraceServiceRequest(getRandomResourceSpans(batchSize));
                sendExportTraceServiceRequestToSource(URL, exportTraceServiceRequest, client);
            } catch (Exception e) {
                System.out.println("Caught exception: " + e.getMessage());
                Thread.sleep(10);
            }
        }
    }
}