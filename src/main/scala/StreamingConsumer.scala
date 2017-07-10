/**
  * Created by gamer on 6/29/2017.
  */
import kafka.serializer.{DefaultDecoder, StringDecoder}
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.kafka010.KafkaUtils
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.kafka.common.serialization.StringDeserializer

object StreamingConsumer {
  def main(args: Array[String]): Unit = {
    val conf = new SparkConf()
    conf.setAppName("Datasets Test")
    conf.set("spark.master", "yarn-client")
    conf.set("spark.local.ip", "192.168.144.133")
    conf.set("spark.driver.host", "localhost")
    conf.set("spark.sql.hive.metastore.jars", "builtin")
    conf.setAppName("Data Analyzer")

    val sc = new SparkContext(conf)
    // creating the StreamingContext with 5 seconds interval
    val ssc =  StreamingContext.getActiveOrCreate(() => new StreamingContext(sc, Seconds(5)))

    val kafkaParams = Map[String, Object](
      "bootstrap.servers" -> "vrxhdpkfknod.eastus.cloudapp.azure.com:6667",
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> "use_a_separate_group_id_for_each_stream",
      "auto.offset.reset" -> "earliest",
      "enable.auto.commit" -> (false: java.lang.Boolean)
    )
    val topics = Array("spark-streaming")
    val messages = KafkaUtils.createDirectStream[String, String](
      ssc,
      PreferConsistent,
      Subscribe[String, String](topics, kafkaParams)
    )


    messages.foreachRDD { rdd =>
      System.out.println("--- New RDD with " + rdd.partitions.size + " partitions and " + rdd.count + " records")
      rdd.foreach { record =>
        System.out.println(record.value())
      }
    }




    ssc.start()
    ssc.awaitTermination()
    //ssc.stop(true,true);
  }

}
