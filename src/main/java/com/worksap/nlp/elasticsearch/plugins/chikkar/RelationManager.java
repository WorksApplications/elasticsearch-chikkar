/*
 * Copyright (c) 2019 Works Applications Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.worksap.nlp.elasticsearch.plugins.chikkar;

import java.io.Serializable;
import java.util.*;

public class RelationManager implements Serializable {
    RelationMatrix relationMatrix;

    public static RelationManager clone(RelationManager obj) {
        RelationManager newObj = new RelationManager();
        newObj.relationMatrix = obj.relationMatrix.createCopy();
        return newObj;
    }

    public RelationManager() {
        this.relationMatrix = new RelationMatrix();
    }

    public RelationMatrix getRelationMatrix() {
        return this.relationMatrix;
    }

    class RelationMatrix implements Serializable {
        private List<LinkedList<Integer>> matrix = new ArrayList<>();
        private Map<Integer, Integer> dictTag = new HashMap<>();

        RelationMatrix createCopy() {
            RelationMatrix obj = new RelationMatrix();
            for (LinkedList<Integer> ll : this.matrix) {
                obj.matrix.add((LinkedList<Integer>) ll.clone());
            }
            for (Map.Entry<Integer, Integer> entry : this.dictTag.entrySet()) {
                obj.dictTag.put(entry.getKey(), entry.getValue());
            }
            return obj;
        }

        void add(int a, int b, int dictId) {
            while (matrix.size() < a + 1) {
                matrix.add(new LinkedList<>());
            }
            LinkedList<Integer> aRelation = matrix.get(a);
            if (dictTag.containsKey(a)) {
                if (dictTag.get(a) != dictId) {
                    aRelation.clear();
                    dictTag.put(a, dictId);
                }
            } else {
                dictTag.put(a, dictId);
            }
            delete(a, b);
            aRelation.addFirst(b);
        }

        boolean delete(int a, int b) {
            if (matrix.size() < a + 1)
                return false;
            LinkedList<Integer> aRelation = matrix.get(a);
            return aRelation.removeIf(e -> e == b);
        }

        LinkedList<Integer> getRelations(int i) {
            if (matrix.size() <= i)
                return new LinkedList<>();
            return matrix.get(i);
        }
    }
}
