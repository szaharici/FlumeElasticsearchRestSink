package com.flumetest.elasticsearch;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.flume.Channel;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.FlumeException;
import org.apache.flume.Transaction;
import org.apache.flume.conf.Configurable;
import org.apache.flume.instrumentation.SinkCounter;
import org.apache.flume.sink.AbstractSink;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.ContentType;
import org.apache.log4j.Logger;

import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


public class ElasticsearchSink extends AbstractSink implements Configurable {
    private static final Logger LOG = Logger.getLogger(ElasticsearchSink.class);
    private int batchSize;
    private String indexName;
    private String indexType;
    private String bulkline;
    private RestClientBuilder builder; 
    private SinkCounter sinkCounter;
    private RestClient restClient;
    private JsonParser parser;
    
    @Override
    public void configure(Context context) {
        String hosts_configured = StringUtils.deleteWhitespace(context.getString("hosts", "localhost:9200"));
        try {
	    List<String> es_hosts=Arrays.asList(hosts_configured.split(","));
	    List<HttpHost> hosts = new ArrayList<>(es_hosts.size());
	    for (String hostslist: es_hosts) {
               String hostname = hostslist.split(":")[0];
               int port = Integer.parseInt(hostslist.split(":")[1]);
               LOG.info("Connecting to "+hostname+":"+port);
               hosts.add(new HttpHost(hostname,port,"http"));
	    }
            RestClientBuilder builder = RestClient.builder(hosts.toArray(new HttpHost[hosts.size()]));
	    this.builder = builder;
	}
        catch (Throwable e) {
            throw new FlumeException ("Error configuring the ElasticSearch sink");
        }
        String indexName = context.getString("indexName", "flume");
        String indexType = context.getString("indexType", "kukta");
        String bulkline = "{\"index\": {}}\n";
        int batchSize = context.getInteger("batchSize", 100);
        this.batchSize = batchSize;
        this.bulkline = bulkline;
        this.indexName = indexName;
        this.indexType = indexType;
        this.parser =  new JsonParser();
        if (sinkCounter == null) {
            sinkCounter = new SinkCounter(getName());
        }
    }

    @Override
    public void start() {
        LOG.info("Starting Elasticsearch Sink {} ...Connecting to configured hosts");
        try {
            RestClient restClient = builder.build();
            this.restClient = restClient;
            sinkCounter.incrementConnectionCreatedCount();
        }
        catch (Throwable e){
            LOG.error(e.getStackTrace());
            sinkCounter.incrementConnectionFailedCount();
        }
        sinkCounter.start();
    }

    @Override
    public void stop () {
        LOG.info("Stopping Elasticsearch Sink {} ...");
        try {
            restClient.close();
        } 
        catch (IOException e) {
            e.printStackTrace();
 	    sinkCounter.incrementConnectionClosedCount();
            sinkCounter.stop();
        }
    }

    @Override
    public Status process() throws EventDeliveryException {
       Status status = null;
       // Start transaction
       Channel ch = getChannel();
       Transaction txn = ch.getTransaction();
       txn.begin();
       try {
           StringBuilder batch = new StringBuilder();
           String timeStamp = new SimpleDateFormat("-yyyy-MM-dd").format(new java.util.Date());
           String endpoint = "/"+indexName+timeStamp+"/"+indexType+"/_bulk";
           Event event = null;
           int count = 0;
           sinkCounter.incrementEventDrainAttemptCount();
           for (count = 0; count <= batchSize; ++count) {
              event = ch.take();
              if (event == null) {
                 break;
              }
              String ElasticsearchDoc = ExtractEvent(event);
              batch.append(bulkline);
              batch.append(ElasticsearchDoc);
              batch.append("\n");
              sinkCounter.incrementConnectionCreatedCount();
           }
           if (count == 0) {
              sinkCounter.incrementBatchEmptyCount();
              sinkCounter.incrementEventDrainSuccessCount();
              status = Status.BACKOFF;
              txn.commit();
           } 
           else {
               try {
                   HttpEntity entity = new StringEntity(batch.toString(),ContentType.APPLICATION_JSON);
                   restClient.performRequest("POST", endpoint, Collections.<String, String>emptyMap(), entity);
                   txn.commit();
                   status = Status.READY;
                   if ( count < batchSize ) {
                      sinkCounter.incrementBatchUnderflowCount();
                   }
                   sinkCounter.incrementBatchCompleteCount();
               }
               catch ( Exception e) {
                   LOG.info(e.getMessage());
                   LOG.info("Could not send data to ES, I will retry");
                   txn.rollback();
                   status = Status.BACKOFF;
                   sinkCounter.incrementConnectionFailedCount();
               }
           } 
	   	
           if(event == null) {
              status = Status.BACKOFF;
           }
           return status;
       }
       catch (Throwable t) {
           txn.rollback();
           LOG.info(t.getMessage());
           status = Status.BACKOFF;
           // re-throw all Errors
           if (t instanceof Error) {
               throw (Error)t;
           }
       }
       finally {
           txn.close();
       }
       return status;
    }

    private String ExtractEvent(Event event) {
        Map<String, String> headers = event.getHeaders();
        Iterator it = headers.entrySet().iterator();
        JsonObject logline = new JsonObject();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            String key = (String) entry.getKey();//Dragos said so 
            String value = (String)entry.getValue();
	    	try{
                 logline.add(key, parser.parse(value));
            }
            catch (Exception e) {
            //After all this is not valid json, but I will send it anyway
                 logline.addProperty(key, value);
            }
            
        }
        String body = new String(event.getBody());
        logline.addProperty("message", body.toString());
        return logline.toString();
			
    }

}
