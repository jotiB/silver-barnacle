/**
  * Created by gamer on 6/29/2017.
  */


import org.apache.spark.streaming.kafka010.KafkaUtils
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types._
import org.json4s.DefaultFormats




object StreamingConsumer extends Serializable {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession
      .builder()
      .appName("StructuredStreamingConsumer")
      .enableHiveSupport()
      .getOrCreate()

    val conf = new SparkConf()

    conf.setAppName("StreamingConsumer")
    conf.set("spark.eventLog.enabled", "true")
    conf.set("spark.driver.port", "43189")
    conf.set("spark.yarn.historyServer.address", "developmentbox.hadoop.verax.ca:18081")
    conf.set("spark.history.ui.port", "18081")
    conf.set("spark.yarn.queue", "default")
    conf.set("spark.executor.id", "driver")
    conf.set("spark.driver.host", "10.0.2.15")

    val sc = new SparkContext(conf)

    //sc.getConf.getAll.mkString("\n")

    // creating the StreamingContext with 5 seconds interval
    val ssc = StreamingContext.getActiveOrCreate(() => new StreamingContext(sc, Seconds(5)))

    val kafkaParams = Map[String, Object](
      "bootstrap.servers" -> "developmentBox.hadoop.verax.ca:9092",
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> "kafkastream1",
      "auto.offset.reset" -> "earliest",
      "enable.auto.commit" -> (false: java.lang.Boolean)
    )
    val topics = Array("stock-quotes")
    val messages = KafkaUtils.createDirectStream[String, String](
      ssc,
      PreferConsistent,
      Subscribe[String, String](topics, kafkaParams)
    )

    /*
  Kafka Schema
root
 |-- key: binary (nullable = true)
 |-- value: binary (nullable = true)
 |-- topic: string (nullable = true)
 |-- partition: integer (nullable = true)
 |-- offset: long (nullable = true)
 |-- timestamp: timestamp (nullable = true)
 |-- timestampType: integer (nullable = true)hema
   */

    // Kafka Schema is known but stock-quotes schema is unknown

    messages.foreachRDD(x => {
      if (x != null) {
        val y = x.map(record => (record.key, record.value)).map(_._2)
        System.out.println("Starting dataframe read json command")
        val df = spark.read.json(y) // add schema for transactions
        System.out.println("Kafka Quotes table")
        df.printSchema()
        df.show()

        // Create a temporary view over transactions received from kafka

        df.createOrReplaceTempView("quotes") // Your DF Operations
        if (df.count() > 1) {
          val joinDF = spark.sql("select exchange, ticker, lastTradeDate, high, low, open, close, volume from quotes").write.mode("append")
            .saveAsTable("quotes")
          System.out.println(joinDF)
        }
      }
    })

    ssc.start()
    ssc.awaitTermination()
    //ssc.stop(true,true);

  }
}
