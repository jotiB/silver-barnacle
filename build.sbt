name := "verax-spark-poc"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies += "org.apache.spark" % "spark-core_2.11" % "2.1.1" % "provided"

libraryDependencies += "org.apache.spark" % "spark-sql_2.11" % "2.1.1"

libraryDependencies += "org.apache.spark" % "spark-streaming-kafka-0-10_2.11" % "2.1.1"

libraryDependencies += "org.apache.kafka" % "kafka_2.11" % "0.10.2.1"

libraryDependencies += "org.apache.spark" % "spark-sql-kafka-0-10_2.11" % "2.1.0"

libraryDependencies += "org.apache.spark" % "spark-streaming-kafka-0-10-assembly_2.10" % "2.1.0"

libraryDependencies += "org.apache.spark" % "spark-hive_2.11" % "2.1.1"

libraryDependencies += "org.apache.spark" % "spark-hive-thriftserver_2.11" % "2.1.1"

