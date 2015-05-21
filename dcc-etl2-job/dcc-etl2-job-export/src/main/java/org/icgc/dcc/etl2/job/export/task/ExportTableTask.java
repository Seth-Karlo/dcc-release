/*
 * Copyright (c) 2015 The Ontario Institute for Cancer Research. All rights reserved.                             
 *                                                                                                               
 * This program and the accompanying materials are made available under the terms of the GNU Public License v3.0.
 * You should have received a copy of the GNU General Public License along with                                  
 * this program. If not, see <http://www.gnu.org/licenses/>.                                                     
 *                                                                                                               
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY                           
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES                          
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT                           
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,                                
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED                          
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;                               
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER                              
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN                         
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.icgc.dcc.etl2.job.export.task;

import static org.icgc.dcc.etl2.core.util.Stopwatches.createStarted;

import java.util.List;
import java.util.Map;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.icgc.dcc.etl2.core.job.FileType;
import org.icgc.dcc.etl2.core.task.Task;
import org.icgc.dcc.etl2.core.task.TaskContext;
import org.icgc.dcc.etl2.core.task.TaskType;
import org.icgc.dcc.etl2.job.export.function.ExtractStats;
import org.icgc.dcc.etl2.job.export.function.ProcessDataType;
import org.icgc.dcc.etl2.job.export.function.SumDataType;
import org.icgc.dcc.etl2.job.export.model.ExportTable;
import org.icgc.dcc.etl2.job.export.model.type.ClinicalDataType;
import org.icgc.dcc.etl2.job.export.util.HFileManager;
import org.icgc.dcc.etl2.job.export.util.HTableManager;

import scala.Tuple3;

import com.fasterxml.jackson.databind.node.ObjectNode;

@Slf4j
@RequiredArgsConstructor
public class ExportTableTask implements Task {

  /**
   * Constants.
   */
  private static final Path STATIC_OUTPUT_DIR = new Path("/tmp/exporter/tmp/static");

  /**
   * Configuration.
   */
  @NonNull
  private final ExportTable table;
  @NonNull
  private final Configuration conf;

  @Override
  public String getName() {
    return Task.getName(this.getClass(), table.name());
  }

  @Override
  public TaskType getType() {
    return TaskType.FILE_TYPE;
  }

  @Override
  public void execute(@NonNull TaskContext taskContext) {
    val watch = createStarted();
    val fileSystem = taskContext.getFileSystem();
    val sparkContext = taskContext.getSparkContext();

    log.info("Getting input path...");
    val inputPath = getInputPath(taskContext);
    log.info("Got input path '{}'", inputPath);

    log.info("Running the base export process...");
    val baseExportProcessResult =
        new ExportTask(sparkContext, table).process(inputPath, new ClinicalDataType(taskContext.getSparkContext()));
    log.info("Finished running the base export process.");

    log.info("Writing static export output files...");
    exportStatic(fileSystem, sparkContext, inputPath, baseExportProcessResult);
    log.info("Done writing static export output files...");

    log.info("Writing dynamic export output files...");
    exportDynamic(fileSystem, baseExportProcessResult);
    log.info("Done writing dynamic export output files...");

    log.info("Finished exporting table '{}' in {}", table, watch);
  }

  private void exportDynamic(FileSystem fileSystem, JavaRDD<ObjectNode> input) {

    log.info("Preparing export table '{}'...", table);
    val processedInput = prepareData(input);
    log.info("Prepared export table: {}", table);

    log.info("Preparing export table '{}'...", table);
    val hTable = prepareHTable(conf, processedInput, table.name());
    log.info("Prepared export table: {}", table);

    log.info("Processing HFiles...");
    processHFiles(conf, fileSystem, processedInput, hTable);
    log.info("Finished processing HFiles...");
  }

  private JavaPairRDD<String, Tuple3<Map<byte[], KeyValue[]>, Long, Integer>> prepareData(JavaRDD<ObjectNode> input) {
    return input
        .zipWithIndex()
        .mapToPair(new ProcessDataType())
        .reduceByKey(new SumDataType());
  }

  private void exportStatic(FileSystem fileSystem, JavaSparkContext sparkContext, Path inputPath,
      JavaRDD<ObjectNode> input) {
    // TODO write static files.
    val staticOutputFile = STATIC_OUTPUT_DIR.toString() + "/clinical";
    input.coalesce(1, true).saveAsTextFile(staticOutputFile);
  }

  private static Path getInputPath(TaskContext taskContext) {
    // TODO: This is not a real input and should be decomposed to the upstream types
    return new Path(taskContext.getPath(FileType.EXPORT_INPUT));
  }

  @SneakyThrows
  private static HTable prepareHTable(Configuration conf,
      JavaPairRDD<String, Tuple3<Map<byte[], KeyValue[]>, Long, Integer>> input,
      String tableName) {
    log.info("Ensuring table...");
    val manager = new HTableManager(conf);

    if (manager.existsTable(tableName)) {
      log.info("Table exists...");
      return manager.getTable(tableName);
    }

    log.info("Calculating statistics about data...");
    List<Tuple3<String, Long, Integer>> stats = input.map(new ExtractStats()).collect();
    log.info("Calculated statistics");
    stats.stream().forEach(
        stat -> log.info("Donor {} has size {} and {} observation.", stat._1(), stat._2(), stat._3()));
    log.info("preparing table...");
    val htable = manager.ensureTable(tableName, stats);

    log.info("Listing tables...");
    manager.listTables();

    return htable;
  }

  @SneakyThrows
  private static void processHFiles(Configuration conf, FileSystem fileSystem,
      JavaPairRDD<String, Tuple3<Map<byte[], KeyValue[]>, Long, Integer>> processedInput, HTable hTable) {
    val hFileManager = new HFileManager(conf, fileSystem);

    log.info("Writing HFiles...");
    hFileManager.writeHFiles(processedInput, hTable);
    log.info("Wrote HFiles");

    log.info("Loading HFiles...");
    hFileManager.loadHFiles(hTable);
    log.info("Loaded HFiles");
  }

}
