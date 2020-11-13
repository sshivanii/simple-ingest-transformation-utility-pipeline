package com.amazon.situp.generation;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContexts;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;

public class EsDataGenerator {


    private static Map<String, String> getRandomDoc() {
        return new HashMap<String, String>(){{
            put("Field_1", UUID.randomUUID().toString());
            put("Field_2", UUID.randomUUID().toString());
            put("Field_3", UUID.randomUUID().toString());
            put("Field_4", UUID.randomUUID().toString());
            put("Field_5", UUID.randomUUID().toString());
        }};
    }

    private static BulkRequest getBulkRequest(final int batchSize) {
        final BulkRequest bulkRequest = new BulkRequest("test-index");
        for(int i=0; i<batchSize; i++) {
            bulkRequest.add(
                    new IndexRequest().source(getRandomDoc(), XContentType.JSON)
            );
        }
        return bulkRequest;
    }

    private static void sendRequests(final RestHighLevelClient restHighLevelClient, final int batchSize) throws IOException {
        restHighLevelClient.bulk(
                getBulkRequest(batchSize),
                RequestOptions.DEFAULT
        );
    }

    public static void main(String[] args) throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException, InterruptedException, IOException {
        final String endpoint = args[0];
        final int batchSize = Integer.parseInt(args[1]);
        final int batchesPerSecond = Integer.parseInt(args[2]);
        final long millisPerBatch = 1000L/(long)batchesPerSecond;

        RestClientBuilder restClientBuilder = RestClient.builder(HttpHost.create(endpoint));
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("admin", "admin"));
        SSLContext sslContext =  SSLContexts.custom().loadTrustMaterial(null, new TrustAllStrategy()).build();
        restClientBuilder.setHttpClientConfigCallback(
                httpClientBuilder -> httpClientBuilder
                        .setDefaultCredentialsProvider(credentialsProvider)
                        .setSSLContext(sslContext)
                        .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
        );

        final RestHighLevelClient restHighLevelClient = new RestHighLevelClient(restClientBuilder);

        long nextBatchMillis = System.currentTimeMillis();
        while(true) {
            if(System.currentTimeMillis() < nextBatchMillis) {
                Thread.sleep(nextBatchMillis - System.currentTimeMillis());
            }
            nextBatchMillis += millisPerBatch;
            sendRequests(restHighLevelClient, batchSize);
        }

    }


}
