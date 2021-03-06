import java.io.File

import au.com.bytecode.opencsv.CSVParser
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs._
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.types.{StringType, StructField, StructType}
import org.apache.spark.{SparkConf, SparkContext}

import org.apache.spark.sql.{SQLContext, Row, DataFrame}

import scala.RuntimeException

//case class CrimeType(primaryType: String, label:String)
case class CrimeType(primaryType: String)
case class Beat(id: String, label:String)


object GenerateCSVFiles {

  def merge(srcPath: String, dstPath: String, header: String): Unit =  {
    val hadoopConfig = new Configuration()
    val hdfs = FileSystem.get(hadoopConfig)
    MyFileUtil.copyMergeWithHeader(hdfs, new Path(srcPath), hdfs, new Path(dstPath), false, hadoopConfig, header)
  }

  def main(args: Array[String]) {
    var crimeFile = args(0)

//    if(crimeFile == null || !new File(crimeFile).exists()) {
//      throw new RuntimeException("Cannot find CSV file [" + crimeFile + "]")
//    }

    println("Using %s".format(crimeFile))

    val conf = new SparkConf().setAppName("Chicago Crime Dataset")
    val sc = new SparkContext(conf)
    val sqlContext = new SQLContext(sc)
    import sqlContext.implicits._

    sqlContext.load("com.databricks.spark.csv", Map("path" -> crimeFile, "header" -> "true")).registerTempTable("crimes")

//    val crimeTypes = sqlContext.sql("select * from crimes").map(row => CrimeType(row.get(5).toString.trim))


//    val crimeTypes = sqlContext.sql("select `Primary Type` as primaryType, 'CrimeType' AS label from crimes").map {
//      case Row(primaryType: String, label: String) => Row(primaryType.trim, label)
//    }

    //    val schema = StructType(Seq("primaryType", "label").map(fieldName => StructField(fieldName, StringType, nullable = true)))
    //    sqlContext.createDataFrame(crimeTypes, schema).distinct.save (tmpFile, "com.databricks.spark.csv")

//    sqlContext.

//    val rows = sqlContext.sql("select `Primary Type` as primaryType FROM crimes LIMIT 10")
//
//    rows.map { case Row(primaryType: String) => Row(primaryType.trim) }
//
//
//
//
//
//    createFile(
//      rows.map { case Row(primaryType: String) => CrimeType(primaryType.trim) }.toDF(),
//      "/tmp/crimeTypes.csv",
//      "crimeType:ID(CrimeType)")


    val rows: Array[Row] = sqlContext.sql("select `FBI Code` AS fbiCode, COUNT(*) AS times FROM crimes GROUP BY `FBI Code` ORDER BY " +
      "times DESC").collect()
    rows.foreach(println)

//    createFile(sqlContext.sql("select `Primary Type` as primaryType, 'CrimeType' AS label from crimes")
//      .map { case Row(primaryType: String, label: String) => CrimeType(primaryType.trim, label) }.toDF(),
//      "tmp/crimeTypes.csv", "crimeType:ID(CrimeType),:LABEL")
//
//    createFile(sqlContext.createDataFrame(sqlContext
//      .sql("select `Beat` as beat, 'Beat' AS label from crimes")
//      .map { case Row(beat: String, label: String) => Beat(beat, label) }),
//      "tmp/beats.csv", "id:ID(Beat),:LABEL")
//
//    val crimeData = sc.textFile(crimeFile).cache()
//    val withoutHeader: RDD[String] = dropHeader(crimeData)
//
//    generateFile("tmp/primaryTypes.csv", withoutHeader,
//      columns => Array(columns(5).trim(), "CrimeType"),
//      "crimeType:ID(CrimeType),:LABEL")
//
//    generateFile("tmp/beats.csv", withoutHeader,
//      columns => Array(columns(10), "Beat"),
//      "id:ID(Beat),:LABEL")
//
//    generateFile("tmp/locations.csv", withoutHeader,
//      columns => Array("\"" + columns(7) + "\"", "Location"),
//      "id:ID(Location)," + ":LABEL")
//
//    generateFile("tmp/crimes.csv", withoutHeader,
//      columns => Array(columns(0),"Crime", columns(2), columns(6), columns(1), columns(8), columns(9), columns(14)),
//      "id:ID(Crime),:LABEL,date,description,caseNumber,arrest:Boolean,domestic:Boolean,fbiCode", distinct = false)
//
//    generateFile("tmp/dates.csv", withoutHeader,
//      columns => {
//        val parts = columns(2).split(" ")(0).split("/")
//        Array(parts.mkString(""), "Date", parts(0), parts(1), parts(2))
//      },
//      "id:ID(Date),:LABEL,month:int,day:int,year:int", distinct = true)
//
//    generateFile("tmp/crimesBeats.csv", withoutHeader,
//      columns => Array(columns(0),columns(10).trim(), "ON_BEAT"),
//      ":START_ID(Crime),:END_ID(Beat),:TYPE")
//
//    generateFile("tmp/crimesPrimaryTypes.csv", withoutHeader,
//      columns => Array(columns(0),columns(5).trim(),
//      "CRIME_TYPE"), ":START_ID(Crime),:END_ID(CrimeType),:TYPE")
//
//    generateFile("tmp/crimesLocations.csv", withoutHeader,
//      columns => Array(columns(0),"\"" + columns(7) + "\"", "COMMITTED_IN"),
//      ":START_ID(Crime),:END_ID(Location),:TYPE")
//
//    generateFile("tmp/crimesDates.csv", withoutHeader,
//      columns =>  {
//        val parts = columns(2).split(" ")(0).split("/")
//        Array(columns(0), parts.mkString(""), "ON_DATE")
//      },
//      ":START_ID(Crime),:END_ID(Date),:TYPE")
  }

  private def createFile(df: DataFrame, file: String, header: String): Unit = {
    FileUtil.fullyDelete(new File(file))
    val tmpFile = "tmp/" + System.currentTimeMillis() + "-" + file
    df.distinct.save(tmpFile, "com.databricks.spark.csv")
    merge(tmpFile, file, header)
  }

  def generateFile(file: String, withoutHeader: RDD[String],
                   fn: Array[String] => Array[String], header: String ,
                   distinct:Boolean = true, separator: String = ",") = {
    FileUtil.fullyDelete(new File(file))

    val tmpFile = "tmp/" + System.currentTimeMillis() + "-" + file
    val rows: RDD[String] = withoutHeader.mapPartitions(lines => {
      val parser = new CSVParser(',')
      lines.map(line => {
        val columns = parser.parseLine(line)
        fn(columns).mkString(separator)
      })
    })

    if (distinct) rows.distinct() saveAsTextFile tmpFile
    else rows.saveAsTextFile(tmpFile)

    merge(tmpFile, file, header)
  }

  // http://mail-archives.apache.org/mod_mbox/spark-user/201404.mbox/%3CCAEYYnxYuEaie518ODdn-fR7VvD39d71=CgB_Dxw_4COVXgmYYQ@mail.gmail.com%3E
  def dropHeader(data: RDD[String]): RDD[String] = {
    data.mapPartitionsWithIndex((idx, lines) => {
      if (idx == 0) {
        lines.drop(1)
      }
      lines
    })
  }


}
