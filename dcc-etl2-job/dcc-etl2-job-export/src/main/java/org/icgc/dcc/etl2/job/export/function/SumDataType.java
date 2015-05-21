package org.icgc.dcc.etl2.job.export.function;

import java.util.Map;
import java.util.TreeMap;

import org.apache.hadoop.hbase.KeyValue;
import org.apache.spark.api.java.function.Function2;

import scala.Tuple3;

public class SumDataType
    implements
    Function2<Tuple3<Map<byte[], KeyValue[]>, Long, Integer>, Tuple3<Map<byte[], KeyValue[]>, Long, Integer>, Tuple3<Map<byte[], KeyValue[]>, Long, Integer>> {

  @Override
  public Tuple3<Map<byte[], KeyValue[]>, Long, Integer> call(Tuple3<Map<byte[], KeyValue[]>, Long, Integer> tuple1,
      Tuple3<Map<byte[], KeyValue[]>, Long, Integer> tuple2) throws Exception {
    Map<byte[], KeyValue[]> data = new TreeMap<byte[], KeyValue[]>();
    data.putAll(tuple1._1());
    data.putAll(tuple2._1());
    long totalSize = tuple1._2() + tuple2._2();
    int sum = tuple1._3() + tuple2._3();

    return new Tuple3<Map<byte[], KeyValue[]>, Long, Integer>(data, totalSize, sum);
  }

}
