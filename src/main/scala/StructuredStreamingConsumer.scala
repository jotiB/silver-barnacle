import org.apache.spark.sql.{DataFrame, Encoders, SparkSession}
import org.apache.spark.sql.functions.from_json
import org.apache.spark.sql.functions.from_unixtime
import org.apache.spark.sql.functions.col
import org.apache.spark.sql.streaming.OutputMode
import org.apache.spark.sql.functions._
import org.apache.spark.sql.hive.thriftserver._
import org.apache.spark.sql.types._



/* To run the code snippets of this program in Zeppelin, change the following config values
Zeppelin Spark Interpreter:
master = yarn
Dependencies
org.apache.kafka:kafka-clients:0.10.1.1
org.apache.spark:spark-streaming-kafka-0-10_2.11:2.1.1
org.apache.kafka:kafka_2.11:0.10.1.1

Advanced Zeppelin Config in Ambari Zeppelin configs zeppelin_env_content
export MASTER=yarn
export SPARK_HOME=/usr/hdp/current/spark2-client
export SPARK_SUBMIT_OPTIONS="--packages   org.apache.spark:spark-sql-kafka-0-10_2.11:2.1.0,
org.apache.spark:spark-sql_2.11:2.1.0,org.apache.kafka:kafka_2.11:0.10.0.1,
org.apache.spark:spark-streaming-kafka-0-10_2.11:2.1.0,org.apache.kafka:kafka-clients:0.10.0.1"
*/

object StructuredStreamingConsumer {
  // Stock Quotes Schema creation
  case class StockQuote(exchange: String,
                        ticker: String,
                        lastTradeDate: Long,
                        open: Float,
                        high: Float,
                        low: Float,
                        close: Float,
                        volume: Float)

  def main(args: Array[String]) {

    val spark = SparkSession
      .builder()
      .appName("StructuredStreamingConsumer")
      .enableHiveSupport()
      .getOrCreate()

    val sqlContext = spark.sqlContext
    sqlContext.setConf("hive.server2.thrift.port","10016")

    HiveThriftServer2.startWithContext(sqlContext)

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

    /* Stock Quotes Schema creation conventional method
    val schema = StructType(Seq(
   StructField("exchange", StringType, true),
   StructField("ticker", StringType, true),
   StructField("lastTradeDate", TimestampType, true),
   StructField("open", FloatType, true),
   StructField("high", FloatType, true),
   StructField("low", FloatType, true),
   StructField("close", FloatType, true),
   StructField("volume", FloatType, true)
 ))*/


     //Stock Quotes Schema creation DSL method
    val schema = new StructType()
    .add("exchange", StringType)
    .add("ticker", StringType)
    .add("lastTradeDate", LongType)
    .add("open", FloatType)
    .add("high", FloatType)
    .add("low", FloatType)
    .add("close", FloatType)
    .add("volume", FloatType)



    //val schema = Encoders.product[StockQuote].schema
    //val stockQuoteTag = typeTag[StockQuote];
    //val tt = typeTag[StockQuote];
    //val schema = Encoders.product[StockQuote]

    // Use Spark DSL to create the quotes base data frame

    val quotesDF = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "devbox.hdpdev.verax.ca:9092")
      .option("subscribe", "market-quotes")
      .option("startingOffsets", "earliest")
      .load()
      .withColumn("quote", from_json(col("value").cast("string"), schema))
      .select(col("quote.exchange"), col("quote.ticker"), (col("quote.lastTradeDate")/1000).cast(TimestampType).alias("lastTradedTs"),
        col("quote.open"), col("quote.high"), col("quote.low"), col("quote.close"), col("quote.volume")
      )

    //spark.sqlContext

    quotesDF.createOrReplaceTempView("quotes")

    /*if (quotesDF.count() > 1) {
      val baseQueryDF = spark.sql("select exchange, ticker, lastTradeDate, high, low, open, close, volume from quotes").write.mode("append")
        .saveAsTable("quotes")
      System.out.println(baseQueryDF)
    }*/
    spark.sql("select exchange, ticker, lastTradedTs, high, low, open, close, volume from quotes").writeStream.format("console").start()

    val stocks2017DS = quotesDF.filter("year(lastTradedTs)==2017")
    // Use Spark DSL to create the quotes analysis data frame

    val slidingWindowDS = stocks2017DS.groupBy(window(col("lastTradedTs"), "1 day", "60 minutes"))
      .agg(sum("volume").as("trade_volume").cast(FloatType))// window duration and slide duration specified

    printWindow(slidingWindowDS, "trade_volume")

    val tumblingWindowDS = stocks2017DS.groupBy(window(col("lastTradedTs"), "1 day"))
      .agg(sum("volume").as("trade_volume").cast(FloatType)) // Only Window duration specified

    printWindow(tumblingWindowDS, "trade_volume")

    // Use Spark DSL to stream the data frame to its target. In this case to the console


    val dumpQuery = quotesDF.writeStream.format("console").start()

    val quotesAnalysis = quotesDF.groupBy("ticker").sum("volume")
    val aggQuery = quotesAnalysis.
      writeStream.
      outputMode(OutputMode.Complete).
      format("console").
      start()

}

  def printWindow(windowDF: DataFrame, aggCol: String) = {
    windowDF.sort("window.start").select("window.start", "window.end", s"$aggCol")
      .writeStream
      .queryName("aggQuery")
      .outputMode(OutputMode.Complete)
      .format("console")
      .start()
  }

}
