# Finatra Kafka & Kafka Streams
[![status: unmaintained](https://opensource.twitter.dev/status/unmaintained.svg)](https://opensource.twitter.dev/status/#unmaintained)
[![Build Status](https://github.com/finagle/finatra-kafka/workflows/continuous%20integration/badge.svg?branch=main)](https://github.com/finagle/finatra-kafka/actions/workflows/ci.yml?query=workflow%3A%22continuous+integration%22+branch%3A%22main%22+)
[![Codecov](https://codecov.io/gh/finagle/finatra-kafka/branch/main/graph/badge.svg)](https://codecov.io/gh/finagle/finatra-kafka)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.twitter/finatra-kafka_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.twitter/finatra-kafka_2.12)

Finatra integration with [Kafka Streams](https://kafka.apache.org/documentation/streams) to easily build Kafka Streams applications on top of a [TwitterServer](https://github.com/twitter/twitter-server).

> **Note**: Versions of finatra-kafka and finatra-kafka-streams that are published against Scala 2.12 use Kafka 2.2, versions of that are published against Scala 2.13 use Kafka 2.5. This simplified cross-version support is ephemeral until we can drop Kafka 2.2.

#Announcement

Finatra-Kafka was migrated out of Finatra core library as a stand-alone project in 2022. Currently, Twitter has no plans to develop, maintain or support Finatra-Kafka in any form in the future.
If your organization is interested in maintaining this framework, please file an issue or open a discussion to engage the community.

## Development version

The [main branch](https://github.com/finagle/finatra-kafka/tree/main) in Github tracks the latest code. If you want to contribute a patch or fix, please use this branch as the basis of your [Pull Request](https://help.github.com/articles/creating-a-pull-request/).

## Features

-   Intuitive [DSL](https://github.com/finagle/finatra-kafka/tree/main/kafka-streams/kafka-streams/src/main/scala/com/twitter/finatra/kafkastreams/dsl) for topology creation, compatible with the [Kafka Streams DSL](https://kafka.apache.org/21/documentation/streams/developer-guide/dsl-api.html)
-   Full Kafka Streams metric integration, exposed as [TwitterServer Metrics](https://twitter.github.io/twitter-server/Features.html#metrics)
-   [RocksDB integration](#rocksdb)
-   [Queryable State](#queryable-state)
-   [Rich testing functionality](#testing)

## Basics

With [KafkaStreamsTwitterServer](https://github.com/finagle/finatra-kafka/blob/main/kafka-streams/kafka-streams/src/main/scala/com/twitter/finatra/kafkastreams/KafkaStreamsTwitterServer.scala),
a fully functional service can be written by simply configuring the Kafka Streams Builder via the `configureKafkaStreams()` lifecycle method. See the [examples](#examples) section.

### Transformers

Implement custom [transformers](https://kafka.apache.org/21/javadoc/org/apache/kafka/streams/kstream/Transformer.html) using [FinatraTransformer](https://github.com/finagle/finatra-kafka/blob/main/kafka-streams/kafka-streams/src/main/scala/com/twitter/finatra/kafkastreams/transformer/FinatraTransformer.scala).

#### Aggregations

There are several included aggregating transformers, which may be used when configuring a `StreamsBuilder`

:   -   `aggregate`
    -   `sample`
    -   `sum`

## Stores

### RocksDB

In addition to using [state stores](https://kafka.apache.org/21/javadoc/org/apache/kafka/streams/state/Stores.html), you may also use a RocksDB-backed store. This affords all of the advantages of using [RocksDB](https://rocksdb.org/), including efficient range scans.

### Queryable State

Finatra Kafka Streams supports directly querying the state from a store. This can be useful for creating a service that serves data aggregated within a local Topology. You can use [static partitioning](https://github.com/finagle/finatra-kafka/blob/main/kafka-streams/kafka-streams-static-partitioning/src/main/scala/com/twitter/finatra/kafkastreams/partitioning/StaticPartitioning.scala) to query an instance deterministically known to hold a key.

See how the queryable state is used in the following [example](#queryable-state).

#### Queryable Stores

> -   [QueryableFinatraKeyValueStore](https://github.com/finagle/finatra-kafka/blob/main/kafka-streams/kafka-streams/src/main/scala/com/twitter/finatra/kafkastreams/query/QueryableFinatraKeyValueStore.scala)
> -   [QueryableFinatraWindowStore](https://github.com/finagle/finatra-kafka/blob/main/kafka-streams/kafka-streams/src/main/scala/com/twitter/finatra/kafkastreams/query/QueryableFinatraWindowStore.scala)
> -   [QueryableFinatraCompositeWindowStore](https://github.com/finagle/finatra-kafka/blob/main/kafka-streams/kafka-streams/src/main/scala/com/twitter/finatra/kafkastreams/query/QueryableFinatraCompositeWindowStore.scala)


## Examples

The [integration tests](https://github.com/finagle/finatra-kafka/tree/main/kafka-streams/kafka-streams/src/test/scala/com/twitter/finatra/kafkastreams/integration) serve as a good collection of example Finatra Kafka Streams servers.

Word Count Server
-----------------

We can build a lightweight server that counts the unique words from an input topic, storing the results in RocksDB.

``` {.sourceCode .scala}
class WordCountRocksDbServer extends KafkaStreamsTwitterServer {

  override val name = "wordcount"
  private val countStoreName = "CountsStore"

  override protected def configureKafkaStreams(builder: StreamsBuilder): Unit = {
    builder.asScala
      .stream[Bytes, String]("TextLinesTopic")(Consumed.`with`(Serdes.Bytes, Serdes.String))
      .flatMapValues(_.split(' '))
      .groupBy((_, word) => word)(Serialized.`with`(Serdes.String, Serdes.String))
      .count()(Materialized.as(countStoreName))
      .toStream
      .to("WordsWithCountsTopic")(Produced.`with`(Serdes.String, ScalaSerdes.Long))
  }
}
```

### Queryable State

We can then expose a Thrift endpoint enabling clients to directly query the state via [interactive queries](https://kafka.apache.org/21/documentation/streams/developer-guide/interactive-queries.html).

``` {.sourceCode .scala}
class WordCountRocksDbServer extends KafkaStreamsTwitterServer with QueryableState {

  ...

  final override def configureThrift(router: ThriftRouter): Unit = {
    router
      .add(
        new WordCountQueryService(
          queryableFinatraKeyValueStore[String, Long](
            storeName = countStoreName,
            primaryKeySerde = Serdes.String
          )
        )
      )
  }
}
```

In this example, `WordCountQueryService` is an underlying Thrift service.

## Testing

Finatra Kafka Streams includes tooling that simplifies the process of writing highly testable services. See [TopologyFeatureTest](https://github.com/finagle/finatra-kafka/blob/main/kafka-streams/kafka-streams/src/test/scala/com/twitter/finatra/kafkastreams/test/TopologyFeatureTest.scala), which includes a [FinatraTopologyTester](https://github.com/finagle/finatra-kafka/blob/main/kafka-streams/kafka-streams/src/test/scala/com/twitter/finatra/kafkastreams/test/FinatraTopologyTester.scala) that integrates Kafka Streams' [TopologyTestDriver](https://kafka.apache.org/21/javadoc/org/apache/kafka/streams/TopologyTestDriver.html) with a [KafkaStreamsTwitterServer](https://github.com/finagle/finatra-kafka/blob/main/kafka-streams/kafka-streams/src/main/scala/com/twitter/finatra/kafkastreams/KafkaStreamsTwitterServer.scala).

## License

Licensed under the Apache License, Version 2.0: https://www.apache.org/licenses/LICENSE-2.0
