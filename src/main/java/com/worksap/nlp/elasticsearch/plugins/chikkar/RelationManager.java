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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class RelationManager implements Serializable {
    RelationMatrix relationMatrix;

    public RelationManager() {
        this.relationMatrix = new RelationMatrix();
    }

    public RelationMatrix getRelationMatrix() {
        return this.relationMatrix;
    }

    class RelationMatrix implements Serializable {
        List<LinkedList<Integer>> matrix = new ArrayList<>();
        List<String> semanticTags = new ArrayList<>();

        void add(int a, int b, String semanticTag) {
            while (matrix.size() < a + 1) {
                matrix.add(new LinkedList<>());
                semanticTags.add(semanticTag);
            }
            LinkedList<Integer> aRelation = matrix.get(a);
            semanticTags.set(a, semanticTag);
            delete(a, b);
            aRelation.addFirst(b);
        }

        boolean delete(int a, int b) {
            if (matrix.size() < a + 1)
                return false;
            LinkedList<Integer> aRelation = matrix.get(a);
            return aRelation.removeIf(e -> e == b);
        }

        String getSemanticTag(int i) {
            if (semanticTags.size() <= i)
                return null;
            return semanticTags.get(i);
        }

        LinkedList<Integer> getRelations(int i) {
            if (matrix.size() <= i)
                return new LinkedList<>();
            return matrix.get(i);
        }
    }
}
