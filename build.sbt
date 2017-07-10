name := "verax-spark-poc"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies += "org.apache.spark" % "spark-core_2.11" % "2.1.1" % "provided"

libraryDependencies += "org.apache.spark" % "spark-streaming-kafka-0-10_2.11" % "2.1.1"

libraryDependencies += "org.apache.kafka" % "kafka_2.11" % "0.10.2.1"

libraryDependencies += "org.apache.spark" % "spark-yarn_2.10" % "2.0.1"

