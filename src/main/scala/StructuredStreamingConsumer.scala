import org.apache.spark.sql.{DataFrame, Encoders, SparkSession}
import org.apache.spark.sql.functions.from_json
import org.apache.spark.sql.functions.col
import org.apache.spark.sql.streaming.OutputMode
import org.apache.spark.sql.functions._
import org.apache.spark.sql.hive.thriftserver._

object StructuredStreamingConsumer {

  def main(args: Array[String]) {

    val spark = SparkSession
      .builder()
      .appName("StructuredStreamingConsumer")
      .enableHiveSupport()
      .getOrCreate()
    val sqlContext = spark.sqlContext
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


    /* Stock Quotes Schema creation DSL method
    val stockQuotesSchema = new StructType()
    .add("exchange", StringType)
    .add("ticker", StringType)
    .add("lastTradeDate", TimestampType)
    .add("open", FloatType)
    .add("high", FloatType)
    .add("low", FloatType)
    .add("close", FloatType)
    .add("volume", FloatType)*/

    // Stock Quotes Schema creation best practice method
    case class StockQuote(exchange: String,
                          ticker: String,
                          lastTradeDate: java.sql.Timestamp,
                          open: Float,
                          high: Float,
                          low: Float,
                          close: Float,
                          volume: Float)

    val schema = Encoders.product[StockQuote].schema

    // Use Spark DSL to create the quotes base data frame

    val quotesDF = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "developmentBox.hadoop.verax.ca:9092")
      .option("subscribe", "stock-quotes")
      .option("startingOffsets", "earliest")
      .load()
      .withColumn("quote", from_json(col("value").cast("string"), schema))
      .select(col("quote.exchange"), col("quote.ticker"), col("quote.lastTradeDate"),
        col("quote.open"), col("quote.high"), col("quote.low"), col("quote.close"), col("quote.volume")
      )

    //spark.sqlContext

    quotesDF.createOrReplaceTempView("quotes")

    /*if (quotesDF.count() > 1) {
      val baseQueryDF = spark.sql("select exchange, ticker, lastTradeDate, high, low, open, close, volume from quotes").write.mode("append")
        .saveAsTable("quotes")
      System.out.println(baseQueryDF)
    }*/

    spark.sql("select exchange, ticker, lastTradeDate, high, low, open, close, volume from quotes")
      .show()

    val stocks2017DS = quotesDF.filter("year(lastTradeDate)==2017")
    // Use Spark DSL to create the quotes analysis data frame



    val slidingWindowDS = stocks2017DS.groupBy(window(col("quote.lastTradeDate"), "3 minutes", "30 seconds"))
      .agg(sum("volume").as("trade_volume"))// window duration and slide duration specified

    printWindow(slidingWindowDS, "trade_volume")

    val tumblingWindowDS = stocks2017DS.groupBy(window(col("quote.lastTradeDate"), "1 day"))
      .agg(sum("volume").as("trade_volume")) // Only Window duration specified

    printWindow(tumblingWindowDS, "trade_volume")

    // Use Spark DSL to stream the data frame to its target. In this case to the console


    val dumpQuery = quotesDF.writeStream.format("console").start()

    val quotesAnalysis = quotesDF.groupBy("ticker" ).sum("volume")
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
