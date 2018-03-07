/*
 * Copyright 2017 Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package io.confluent.ksql.function.udaf.topkdistinct;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.streams.kstream.Merger;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import io.confluent.ksql.function.KsqlAggregateFunction;
import io.confluent.ksql.parser.tree.Expression;
import io.confluent.ksql.util.ArrayUtil;
import io.confluent.ksql.util.KsqlException;

public class TopkDistinctKudaf<T extends Comparable<? super T>>
    extends KsqlAggregateFunction<T, T[]> {

  private final int tkVal;
  private final Class<T> ttClass;
  private final Comparator<T> comparator;

  @SuppressWarnings("unchecked")
  TopkDistinctKudaf(int argIndexInValue,
                    int tkVal,
                    Class<T> ttClass) {
    super(argIndexInValue,
        () -> (T[]) Array.newInstance(ttClass, tkVal),
          SchemaBuilder.array(Schema.FLOAT64_SCHEMA).build(),
          Collections.singletonList(Schema.FLOAT64_SCHEMA)
    );

    this.tkVal = tkVal;
    this.ttClass = ttClass;
    this.comparator = (v1, v2) -> {
      if (v1 == null && v2 == null) {
        return 0;
      }
      if (v1 == null) {
        return 1;
      }
      if (v2 == null) {
        return -1;
      }

      return Comparator.<T>reverseOrder().compare(v1, v2);
    };
  }

  @Override
  public T[] aggregate(T currentVal, T[] currentAggVal) {
    if (currentVal == null) {
      return currentAggVal;
    }
    if (ArrayUtil.containsValue(currentVal, currentAggVal)) {
      return currentAggVal;
    }
    int nullIndex = ArrayUtil.getNullIndex(currentAggVal);
    if (nullIndex != -1) {
      currentAggVal[nullIndex] = currentVal;
      Arrays.sort(currentAggVal, comparator);
      return currentAggVal;
    }

    final T last = currentAggVal[currentAggVal.length - 1];
    if (currentVal.compareTo(last) <= 0) {
      return currentAggVal;
    }

    currentAggVal[currentAggVal.length - 1] = currentVal;
    Arrays.sort(currentAggVal, comparator);
    return currentAggVal;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Merger<String, T[]> getMerger() {
    return (aggKey, aggOne, aggTwo) -> {

      int
          nullIndex1 =
          ArrayUtil.getNullIndex(aggOne) == -1 ? tkVal : ArrayUtil.getNullIndex(aggOne);
      int
          nullIndex2 =
          ArrayUtil.getNullIndex(aggTwo) == -1 ? tkVal : ArrayUtil.getNullIndex(aggTwo);
      T[] tempMergeTopkArray = (T[]) Array.newInstance(ttClass, nullIndex1 + nullIndex2);

      System.arraycopy(aggOne, 0, tempMergeTopkArray, 0, nullIndex1);

      int duplicateCount = 0;
      for (int i = nullIndex1; i < nullIndex1 + nullIndex2; i++) {
        if (ArrayUtil.containsValue(aggTwo[i - nullIndex1], aggOne)) {
          duplicateCount++;
        } else {
          tempMergeTopkArray[i - duplicateCount] = aggTwo[i - nullIndex1];
        }
      }
      tempMergeTopkArray = ArrayUtil.getNoNullArray(ttClass, tempMergeTopkArray);
      Arrays.sort(tempMergeTopkArray, comparator);
      if (tempMergeTopkArray.length < tkVal) {
        tempMergeTopkArray = ArrayUtil.padWithNull(ttClass, tempMergeTopkArray, tkVal);
        return tempMergeTopkArray;
      }
      return Arrays.copyOf(tempMergeTopkArray, tkVal);
    };
  }

  @Override
  public KsqlAggregateFunction<T, T[]> getInstance(Map<String, Integer> expressionNames,
                                                   List<Expression> functionArguments) {
    if (functionArguments.size() != 2) {
      throw new KsqlException(String.format("Invalid parameter count. Need 2 args, got %d arg(s)"
                                            + ".", functionArguments.size()));
    }

    final int udafIndex = expressionNames.get(functionArguments.get(0).toString());
    final int tkValFromArg = Integer.parseInt(functionArguments.get(1).toString());
    return new TopkDistinctKudaf<>(udafIndex, tkValFromArg, ttClass);
  }
}