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
package org.icgc.dcc.release.job.summarize.function;

import static org.icgc.dcc.common.core.model.FieldNames.GENE_DONORS;
import static org.icgc.dcc.release.core.util.ObjectNodes.createObject;
import static org.icgc.dcc.release.core.util.Tuples.tuple;
import lombok.val;

import org.apache.spark.api.java.function.PairFunction;

import scala.Tuple2;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Optional;

public final class MergeGeneSummaries implements PairFunction<Tuple2<String, Tuple2<Optional<ObjectNode>,
    Optional<ObjectNode>>>, String, ObjectNode> {

  @Override
  public Tuple2<String, ObjectNode> call(Tuple2<String, Tuple2<Optional<ObjectNode>, Optional<ObjectNode>>> tuple)
      throws Exception {
    val merged = createObject();
    val donors = merged.withArray(GENE_DONORS);
    val geneStatsTuple = tuple._2;

    if (geneStatsTuple._1.isPresent()) {
      addDonors(donors, geneStatsTuple._1.get());
    }

    if (geneStatsTuple._2.isPresent()) {
      addDonors(donors, geneStatsTuple._2.get());
    }

    val geneId = tuple._1;

    return tuple(geneId, merged);
  }

  private static void addDonors(ArrayNode donorsArray, ObjectNode objectNode) {
    val donors = objectNode.withArray(GENE_DONORS);
    donorsArray.addAll(donors);
  }

}