# FlumeElasticsearchRestSink
Flume elasticsearch REST sink
=============================

Overview
---------

This is a simple Elasticsearch flume sink based on the official java rest client by elastic.co https://www.elastic.co/guide/en/elasticsearch/client/java-rest/current/java-rest.html. 

The sink should be compatible with all elasticsearch versions, it has been tested with elasticsearch 5.0

Installation
------------

Build the jar and add it alongside with the dependencies in the flume lib directory.

Configuration
------------

Add the sink to the flume configuration as follows:
        ....
        agent.sinks.elasticsearch.type = com.flumetest.elasticsearch.ElasticsearchSink
        agent.sinks.elasticsearch.hosts = host1:port1,host2:port2
        agent.sinks.elasticsearch.indexName = test
        agent.sinks.elasticsearch.indexType = bar_type
        agent.sinks.elasticsearch.batchSize = 500
