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
package org.icgc.dcc.etl2.job.join.task;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;

import org.apache.spark.api.java.JavaRDD;
import org.icgc.dcc.etl2.core.job.FileType;
import org.icgc.dcc.etl2.core.task.GenericTask;
import org.icgc.dcc.etl2.core.task.TaskContext;
import org.icgc.dcc.etl2.job.join.function.CombinePrimaryMeta;
import org.icgc.dcc.etl2.job.join.function.KeyAnalysisIdAnalyzedSampleIdField;

import com.fasterxml.jackson.databind.node.ObjectNode;

@RequiredArgsConstructor
public class PrimaryMetaJoinTask extends GenericTask {

  private static final String META_FILE_TYPE_SUFFIX = "_M";
  private static final String OUTPUT_FILE_TYPE_SUFFIX = "_JOINED";
  private static final String PRIMARY_FILE_TYPE_REGEX = "_P$";

  @NonNull
  private final FileType primaryFileType;

  @Override
  public void execute(TaskContext taskContext) {
    val primary = parsePrimary(primaryFileType, taskContext);
    val meta = parseMeta(resolveMetaFileType(primaryFileType), taskContext);
    val output = join(primary, meta);

    writeOutput(taskContext, output, resolveOutputFileType(primaryFileType));
  }

  private static JavaRDD<ObjectNode> join(JavaRDD<ObjectNode> primary, JavaRDD<ObjectNode> meta) {
    val keyFunction = new KeyAnalysisIdAnalyzedSampleIdField();

    return primary
        .mapToPair(keyFunction)
        .join(meta
            .mapToPair(keyFunction))
        .map(new CombinePrimaryMeta());
  }

  private JavaRDD<ObjectNode> parsePrimary(FileType primaryFileType, TaskContext taskContext) {
    return readInput(taskContext, primaryFileType);
  }

  private JavaRDD<ObjectNode> parseMeta(FileType metaFileType, TaskContext taskContext) {
    return readInput(taskContext, metaFileType);
  }

  private static FileType resolveOutputFileType(FileType primaryFileType) {
    return resolveFileType(primaryFileType, OUTPUT_FILE_TYPE_SUFFIX);
  }

  private static FileType resolveMetaFileType(FileType primaryFileType) {
    return resolveFileType(primaryFileType, META_FILE_TYPE_SUFFIX);
  }

  private static FileType resolveFileType(FileType primaryFileType, String outputFileTypeSuffix) {
    val outputName = primaryFileType.name().replaceAll(PRIMARY_FILE_TYPE_REGEX, outputFileTypeSuffix);

    return FileType.getFileType(outputName);
  }

}
