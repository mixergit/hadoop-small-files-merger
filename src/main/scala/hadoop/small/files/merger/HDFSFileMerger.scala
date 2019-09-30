/*
 * This Scala source file was generated by the Gradle 'init' task.
 */
package hadoop.small.files.merger

import java.util.Calendar

import com.databricks.spark.avro._
import hadoop.small.files.merger.utils.{CommandLineArgs, CommandLineArgsParser, HDFSUtils}
import org.apache.spark.sql.SparkSession


object HDFSFileMerger extends App {
  val hdfsUtils = new HDFSUtils()
  val commandLine = new CommandLineArgsParser(hdfsUtils)

  commandLine
    .parser
    .parse(args, new CommandLineArgs) match {
    case Some(config) => {

      val sparkSession: SparkSession = SparkSession.builder().getOrCreate()
      if (config.format.equalsIgnoreCase("avro")) {
        val schema = commandLine.getSchemaString(config)
        if (config.fromDate != null && config.toDate != null && config.directory.nonEmpty) {
          println(s"From Date: ${config.fromDate}, To: ${config.toDate}")
          (config.fromDate.getTimeInMillis to config.toDate.getTimeInMillis)
            .by(86400000)
            .foreach(dayEpoch => {
              val date = Calendar.getInstance()
              date.setTimeInMillis(dayEpoch)
              val fullDirectoryPath = commandLine.getDatePartitionedPath(config, date)
              mergeAvroDirectory(sparkSession, hdfsUtils, config, fullDirectoryPath, schema)
            })
        } else if (config.directory.nonEmpty && hdfsUtils.exists(config.directory)) {
          mergeAvroDirectory(sparkSession, hdfsUtils, config, config.directory, schema)
        } else {
          commandLine.parser.renderTwoColumnsUsage
        }

      }

    }
    case _ => commandLine.parser.renderTwoColumnsUsage
  }


  def mergeAvroDirectory(sparkSession: SparkSession, hdfsUtils: HDFSUtils, commandLineArgs: CommandLineArgs, directoryPath: String, schema: String): Unit = {

    if (hdfsUtils.exists(directoryPath)) {
      val directorySize = hdfsUtils.getDirectorySize(directoryPath)
      val blockSize = commandLineArgs.blockSize
      val partitionSize = if (directorySize <= blockSize) 1 else Math.ceil(directorySize / blockSize).toInt
      println(s"Directory Size: ${directorySize}")
      println(s"Number of Partitions: ${partitionSize}")
      println(directoryPath)

      sparkSession
        .read
        .option("avroSchema", schema)
        .avro(directoryPath)
        .repartition(partitionSize)
        .write
        .avro(s"${directoryPath}_merged")

      if (hdfsUtils.renameDir(directoryPath, s"${directoryPath}_bak")) {
        println("Source Directory renamed")
        if (hdfsUtils.renameDir(s"${directoryPath}_merged", directoryPath)) {
          println("Merged Directory renamed")
          if (hdfsUtils.moveToTrash(s"${directoryPath}_bak")) {
            println(s"Moved ${directoryPath}_bak to trash")
          }
        }
      }


    }
  }


}
