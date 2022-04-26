import scala.language.reflectiveCalls
import scoverage.ScoverageKeys

Global / onChangedBuildSource := ReloadOnSourceChanges
Global / excludeLintKeys += scalacOptions
Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)

inThisBuild(List(
  organization := "com.github.finagle",
  homepage := Some(url("https://github.com/finagle/finatra-kafka")),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  developers := List(
    Developer(
      "cacoco",
      "Christopher Coco",
      "ccoco@twitter.com",
      url("https://opensource.twitter.dev/")
    ),
    Developer(
      "scosenza",
      "Steve Cosenza",
      "scosenza@twitter.com",
      url("https://opensource.twitter.dev/")
    )
  )
))

def gcJavaOptions: Seq[String] = {
  val javaVersion = System.getProperty("java.version")
  if (javaVersion.startsWith("1.8")) {
    jdk8GcJavaOptions
  } else {
    jdk11GcJavaOptions
  }
}

def jdk8GcJavaOptions: Seq[String] = {
  Seq(
    "-XX:+UseParNewGC",
    "-XX:+UseConcMarkSweepGC",
    "-XX:+CMSParallelRemarkEnabled",
    "-XX:+CMSClassUnloadingEnabled",
    "-XX:ReservedCodeCacheSize=128m",
    "-XX:SurvivorRatio=128",
    "-XX:MaxTenuringThreshold=0",
    "-Xss8M",
    "-Xms512M",
    "-Xmx2G"
  )
}

def jdk11GcJavaOptions: Seq[String] = {
  Seq(
    "-XX:+UseConcMarkSweepGC",
    "-XX:+CMSParallelRemarkEnabled",
    "-XX:+CMSClassUnloadingEnabled",
    "-XX:ReservedCodeCacheSize=128m",
    "-XX:SurvivorRatio=128",
    "-XX:MaxTenuringThreshold=0",
    "-Xss8M",
    "-Xms512M",
    "-Xmx2G"
  )
}

def travisTestJavaOptions: Seq[String] = {
  // When building on travis-ci, we want to suppress logging to error level only.
  // https://docs.travis-ci.com/user/environment-variables/#default-environment-variables
  val travisBuild = sys.env.getOrElse("TRAVIS", "false").toBoolean
  if (travisBuild) {
    Seq(
      "-DSKIP_FLAKY=true",
      "-DSKIP_FLAKY_TRAVIS=true",
      "-Dorg.slf4j.simpleLogger.defaultLogLevel=off",
      "-Dcom.twitter.inject.test.logging.disabled",
      // Needed to avoid cryptic EOFException crashes in forked tests
      // in Travis with `sudo: false`.
      // See https://github.com/sbt/sbt/issues/653
      // and https://github.com/travis-ci/travis-ci/issues/3775
      "-Xmx3G"
    )
  } else {
    Seq("-DSKIP_FLAKY=true")
  }
}

lazy val versions = new {
  // All Twitter library releases are date versioned as YY.MM.patch
  val twLibVersion = "22.4.0"

  val agrona = "0.9.22"
  val fastutil = "8.1.1"
  val junit = "4.12"
  val kafka24 = "2.4.1"
  val kafka25 = "2.5.0"
  val rocksdbjni = "5.14.2"
  val scalaCheck = "1.15.4"
  val scalaTest = "3.1.2"
  val scalaTestPlusJunit = "3.1.2.0"
  val scalaTestPlusScalaCheck = "3.1.2.0"
  val slf4j = "1.7.30"
}

lazy val scalaCollectionCompat = "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2"

lazy val scalaCompilerOptions = scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-unchecked",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Xlint",
  "-Ywarn-unused:imports"
)

lazy val testDependenciesSettings = Seq(
  libraryDependencies ++= Seq(
    "junit" % "junit" % versions.junit, // used by Java tests & org.junit.runner.RunWith annotation on c.t.inject.Test
    "org.scalacheck" %% "scalacheck" % versions.scalaCheck % Test,
    "org.scalatest" %% "scalatest" % versions.scalaTest % Test,
    "org.scalatestplus" %% "junit-4-12" % versions.scalaTestPlusJunit % Test,
    "org.scalatestplus" %% "scalacheck-1-14" % versions.scalaTestPlusScalaCheck % Test
  )
)

lazy val projectSettings = Seq(
  scalaVersion := "2.13.6",
  crossScalaVersions := Seq("2.12.12", "2.13.6"),
  scalaModuleInfo := scalaModuleInfo.value.map(_.withOverrideScalaVersion(true)),
  Test / fork := true, // We have to fork to get the JavaOptions
  Test / javaOptions ++= travisTestJavaOptions,
  libraryDependencies += scalaCollectionCompat,
  resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots")
  ),
  scalaCompilerOptions,
  Compile / compile / javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint:unchecked"),
  doc / javacOptions ++= Seq("-source", "1.8"),
  javaOptions ++= Seq(
    "-Djava.net.preferIPv4Stack=true",
    "-XX:+AggressiveOpts",
    "-server"
  ),
  javaOptions ++= gcJavaOptions,
  // -a: print stack traces for failing asserts
  testOptions += Tests.Argument(TestFrameworks.JUnit, "-a", "-v"),
  // broken in 2.12 due to: https://issues.scala-lang.org/browse/SI-10134
  Compile / doc / scalacOptions ++= {
    if (scalaVersion.value.startsWith("2.12")) Seq("-no-java-comments")
    else Nil
  }
)

def aggregatedProjects = Seq[sbt.ProjectReference](
  kafka,
  kafkaStreams,
  kafkaStreamsPrerestore,
  kafkaStreamsQueryableThrift,
  kafkaStreamsQueryableThriftClient,
  kafkaStreamsStaticPartitioning
)

def mappingContainsAnyPath(mapping: (File, String), paths: Seq[String]): Boolean = {
  paths.foldLeft(false)(_ || mapping._1.getPath.contains(_))
}

lazy val root = (project in file("."))
  .enablePlugins(ScalaUnidocPlugin)
  .settings(
    moduleName := "root",
    ScalaUnidoc / unidoc / unidocProjectFilter := inAnyProject
  ).aggregate(aggregatedProjects: _*)

lazy val kafkaStreamsExclusionRules = Seq(
  ExclusionRule(organization = "javax.ws.rs", name = "javax.ws.rs-api"),
  ExclusionRule(organization = "log4j", name = "log4j"),
  ExclusionRule(organization = "org.slf4j", name = "slf4j-log4j12")
)

lazy val kafkaTestJarSources =
  Seq(
    "com/twitter/finatra/kafka/test/EmbeddedKafka",
    "com/twitter/finatra/kafka/test/KafkaTopic",
    "com/twitter/finatra/kafka/test/utils/ThreadUtils",
    "com/twitter/finatra/kafka/test/utils/PollUtils",
    "com/twitter/finatra/kafka/test/utils/InMemoryStatsUtil",
    "com/twitter/finatra/kafka/test/KafkaFeatureTest",
    "com/twitter/finatra/kafka/test/KafkaStateStore"
  )

def kafkaDependencies(kafkaVersion: String): Seq[ModuleID] = {
  Seq(
    "org.apache.kafka" %% "kafka" % kafkaVersion % "compile->compile;test->test",
    "org.apache.kafka" %% "kafka" % kafkaVersion % "test" classifier "test",
    "org.apache.kafka" % "kafka-clients" % kafkaVersion % "test->test",
    "org.apache.kafka" % "kafka-clients" % kafkaVersion % "test" classifier "test",
    "org.apache.kafka" % "kafka-streams" % kafkaVersion % "test" classifier "test",
    "org.apache.kafka" % "kafka-streams-test-utils" % kafkaVersion % "compile->compile;test->test",
    "org.apache.kafka" % "kafka-streams-test-utils" % kafkaVersion % "test" classifier "test"
  )
}

def kafkaStreamsDependencies(kafkaVersion: String): Seq[ModuleID] = {
  Seq(
    "org.apache.kafka" %% "kafka-streams-scala" % kafkaVersion % "compile->compile;test->test",
    "org.apache.kafka" % "kafka-streams" % kafkaVersion % "test" classifier "test"
  )
}

// select the library and different set of source (files with different scala version
def crossVersionKafka(
  scalaVersion: Option[(Long, Long)],
  kafkaWithscala212: String,
  kafkaWithscala213: String
): String = {
  scalaVersion match {
    case Some((2, n)) if n <= 12 => kafkaWithscala212
    case _ => kafkaWithscala213
  }
}

lazy val kafka = (project in file("kafka"))
  .settings(projectSettings)
  .settings(
    name := "finatra-kafka",
    moduleName := "finatra-kafka",
    ScoverageKeys.coverageExcludedPackages := "<empty>;.*",
    libraryDependencies ++= Seq(
      "com.twitter" %% "finagle-core" % versions.twLibVersion,
      "com.twitter" %% "finagle-exp" % versions.twLibVersion,
      "com.twitter" %% "finagle-thrift" % versions.twLibVersion,
      "com.twitter" %% "inject-app" % versions.twLibVersion % "test->test",
      "com.twitter" %% "inject-core" % versions.twLibVersion % "test->test;compile->compile",
      "com.twitter" %% "inject-modules" % versions.twLibVersion % "test->test",
      "com.twitter" %% "inject-server" % versions.twLibVersion % "test->test",
      "com.twitter" %% "inject-utils" % versions.twLibVersion % "test->test;compile->compile",
      "com.twitter" %% "finatra-jackson" % versions.twLibVersion% "test->test",
      "com.twitter" %% "finatra-utils" % versions.twLibVersion % "test->test;compile->compile",
      "com.twitter" %% "scrooge-serializer" % versions.twLibVersion,
      "com.twitter" %% "util-codec" % versions.twLibVersion,
      "com.twitter" %% "util-slf4j-api" % versions.twLibVersion,
      "org.slf4j" % "slf4j-api" % versions.slf4j % "compile->compile;test->test",
      "org.slf4j" % "slf4j-simple" % versions.slf4j % "test-internal"
    ),
    libraryDependencies ++= {
      val scalaV = CrossVersion.partialVersion(scalaVersion.value)
      kafkaDependencies(crossVersionKafka(scalaV, versions.kafka24, versions.kafka25))
    },
    Test / excludeDependencies ++= kafkaStreamsExclusionRules,
    excludeDependencies ++= kafkaStreamsExclusionRules,
    Test / scroogeThriftIncludeFolders := Seq(file("src/test/thrift")),
    Test / scroogeLanguages := Seq("scala"),
    Test / publishArtifact := true,
    (Test / packageBin / mappings) := {
      val previous = (Test / packageBin / mappings).value
      previous.filter(mappingContainsAnyPath(_, kafkaTestJarSources))
    },
    (Test / packageDoc / mappings) := {
      val previous = (Test / packageDoc / mappings).value
      previous.filter(mappingContainsAnyPath(_, kafkaTestJarSources))
    },
    (Test / packageSrc / mappings) := {
      val previous = (Test / packageSrc / mappings).value
      previous.filter(mappingContainsAnyPath(_, kafkaTestJarSources))
    }
  )

lazy val kafkaStreamsQueryableThriftClient =
  (project in file("kafka-streams/kafka-streams-queryable-thrift-client"))
    .settings(projectSettings)
    .settings(
      name := "finatra-kafka-streams-queryable-thrift-client",
      moduleName := "finatra-kafka-streams-queryable-thrift-client",
      ScoverageKeys.coverageExcludedPackages := "<empty>;.*",
      libraryDependencies ++= Seq(
        "com.twitter" %% "util-slf4j-api" % versions.twLibVersion,
        "com.twitter" %% "finagle-serversets" % versions.twLibVersion,
        "com.twitter" %% "inject-core" % versions.twLibVersion % "test->test;compile->compile",
        "com.twitter" %% "inject-utils" % versions.twLibVersion % "test->test;compile->compile",
        "com.twitter" %% "finatra-thrift" % versions.twLibVersion % "test->test;compile->compile",
        "com.twitter" %% "finatra-utils" % versions.twLibVersion % "test->test;compile->compile",
        "org.slf4j" % "slf4j-simple" % versions.slf4j % "test-internal"
      ),
      Test / excludeDependencies ++= kafkaStreamsExclusionRules,
      excludeDependencies ++= kafkaStreamsExclusionRules
    )

lazy val kafkaStreamsStaticPartitioning =
  (project in file("kafka-streams/kafka-streams-static-partitioning"))
    .settings(projectSettings)
    .settings(
      name := "finatra-kafka-streams-static-partitioning",
      moduleName := "finatra-kafka-streams-static-partitioning",
      ScoverageKeys.coverageExcludedPackages := "<empty>;.*",
      libraryDependencies ++= Seq(
        "com.twitter" %% "inject-core" % versions.twLibVersion % "test->test;compile->compile",
        "com.twitter" %% "inject-utils" % versions.twLibVersion % "test->test;compile->compile",
        "com.twitter" %% "finatra-thrift" % versions.twLibVersion % "test->test;compile->compile",
        "com.twitter" %% "finatra-utils" % versions.twLibVersion % "test->test;compile->compile"
      ),
      Compile / unmanagedSources / includeFilter := {
        val scalaV = CrossVersion.partialVersion(scalaVersion.value)
        scalaV match {
          case _ => "*.scala"
        }
      },
      Compile / unmanagedSourceDirectories += {
        val sourceDir = (Compile / sourceDirectory).value
        sourceDir / "scala-kafka2.5"
      },
      Test / excludeDependencies ++= kafkaStreamsExclusionRules,
      excludeDependencies ++= kafkaStreamsExclusionRules,
      libraryDependencies ++= Seq(
        "org.slf4j" % "slf4j-simple" % versions.slf4j % "test-internal"
      )
    ).dependsOn(
      kafkaStreams % "test->test;compile->compile",
      kafkaStreamsQueryableThriftClient % "test->test;compile->compile"
    )

lazy val kafkaStreamsPrerestore = (project in file("kafka-streams/kafka-streams-prerestore"))
  .settings(projectSettings)
  .settings(
    name := "finatra-kafka-streams-prerestore",
    moduleName := "finatra-kafka-streams-prerestore",
    ScoverageKeys.coverageExcludedPackages := "<empty>;.*",
    libraryDependencies ++= Seq(
      "com.twitter" %% "finatra-http-server" % versions.twLibVersion % "test->test"
    ),
    Test / excludeDependencies ++= kafkaStreamsExclusionRules,
    excludeDependencies ++= kafkaStreamsExclusionRules,
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-simple" % versions.slf4j % "test-internal"
    )
  ).dependsOn(
    kafkaStreamsStaticPartitioning % "test->test;compile->compile"
  )

lazy val kafkaStreamsQueryableThrift =
  (project in file("kafka-streams/kafka-streams-queryable-thrift"))
    .settings(projectSettings)
    .settings(
      name := "finatra-kafka-streams-queryable-thrift",
      moduleName := "finatra-kafka-streams-queryable-thrift",
      ScoverageKeys.coverageExcludedPackages := "<empty>;.*",
      libraryDependencies ++= Seq(
        "com.twitter" %% "inject-core" % versions.twLibVersion % "test->test;compile->compile"
      ),
      Test / excludeDependencies ++= kafkaStreamsExclusionRules,
      excludeDependencies ++= kafkaStreamsExclusionRules,
      Compile / scroogeThriftIncludeFolders := Seq(file("src/test/thrift")),
      Compile / scroogeLanguages := Seq("java", "scala"),
      Test / scroogeLanguages := Seq("java", "scala"),
      libraryDependencies ++= Seq(
        "org.slf4j" % "slf4j-simple" % versions.slf4j % "test-internal"
      )
    ).dependsOn(
      kafkaStreamsStaticPartitioning % "test->test;compile->compile"
    )

lazy val kafkaStreams = (project in file("kafka-streams/kafka-streams"))
  .settings(projectSettings)
  .settings(
    name := "finatra-kafka-streams",
    moduleName := "finatra-kafka-streams",
    ScoverageKeys.coverageExcludedPackages := "<empty>;.*",
    Compile / unmanagedSourceDirectories += {
      val sourceDir = (Compile / sourceDirectory).value
      val scalaV = CrossVersion.partialVersion(scalaVersion.value)
      sourceDir / "scala-kafka2.5"
    },
    Test / unmanagedSourceDirectories += {
      val testDir = (Test / sourceDirectory).value
      val scalaV = CrossVersion.partialVersion(scalaVersion.value)
      testDir / crossVersionKafka(scalaV, "scala-kafka2.4", "scala-kafka2.5")
    },
    libraryDependencies ++= Seq(
      "com.twitter" %% "util-jvm" % versions.twLibVersion,
      "com.twitter" %% "util-jackson" % versions.twLibVersion % Test,
      "com.twitter" %% "util-slf4j-api" % versions.twLibVersion,
      "com.twitter" %% "inject-core" % versions.twLibVersion % "test->test;compile->compile",
      "com.twitter" %% "inject-utils" % versions.twLibVersion % "test->test;compile->compile",
      "com.twitter" %% "finatra-jackson" % versions.twLibVersion % "test->test;compile->compile", 
      "com.twitter" %% "finatra-thrift" % versions.twLibVersion % "test->test",
      "com.twitter" %% "finatra-utils" % versions.twLibVersion % "test->test;compile->compile", 
      "it.unimi.dsi" % "fastutil" % versions.fastutil,
      "jakarta.ws.rs" % "jakarta.ws.rs-api" % "2.1.3",
      "org.agrona" % "agrona" % versions.agrona,
      "org.rocksdb" % "rocksdbjni" % versions.rocksdbjni % "provided;compile->compile;test->test" exclude ("org.mockito", "mockito-all"),
      "org.slf4j" % "slf4j-simple" % versions.slf4j % "test-internal"
    ),
    libraryDependencies ++= {
      val scalaV = CrossVersion.partialVersion(scalaVersion.value)
      kafkaStreamsDependencies(crossVersionKafka(scalaV, versions.kafka24, versions.kafka25))
    },
    Test / excludeDependencies ++= kafkaStreamsExclusionRules,
    excludeDependencies ++= kafkaStreamsExclusionRules,
    Test / publishArtifact := true,
    Test / scroogeThriftIncludeFolders := Seq(file("src/test/thrift")),
    Test / scroogeLanguages := Seq("scala")
  ).dependsOn(
    kafka % "test->test;compile->compile",
    kafkaStreamsQueryableThriftClient % "test->test;compile->compile"
  )

lazy val site = (project in file("doc"))
  .enablePlugins(SphinxPlugin)
  .settings(projectSettings++ Seq(
    doc / scalacOptions ++= Seq("-doc-title", "Finatra", "-doc-version", version.value),
    Sphinx / includeFilter := ("*.html" | "*.png" | "*.svg" | "*.js" | "*.css" | "*.gif" | "*.txt")
  ))
