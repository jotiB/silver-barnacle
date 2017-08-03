import org.apache.spark.sql.{Encoders, SparkSession}
import org.apache.spark.sql.functions.from_json
import org.apache.spark.sql.functions.col
import org.apache.spark.sql.streaming.OutputMode
import org.apache.spark.sql.streaming.Trigger

object StructuredStreamingConsumer {
  val spark = SparkSession
    .builder()
    .appName("StructuredStreamingConsumer")
    .enableHiveSupport()
    .getOrCreate()

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

  /* Stock Quotes Schema creation convention method
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

  val quotes = spark
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

  // Use Spark DSL to create the quotes analysis data frame

  val quotesAnalysis = quotes.groupBy("exchange","ticker" ).sum("volume")

  // Use Spark DSL to stream the data frame to its target. In this case to the console

  //val resultDF = df.select(from_json(col("value").cast("string"), stockQuotesSchema)).as("quote")
  val dumpQuery = quotes.writeStream
    .format("console")
    .start()

  val aggQuery = quotesAnalysis.
    writeStream.
    queryName("aggQuery").
    outputMode(OutputMode.Complete).
    format("console").
    start()
}
