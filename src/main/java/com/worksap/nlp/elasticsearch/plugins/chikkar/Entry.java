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

class Entry implements Serializable {
    int id;
    String pronunciation;
    String partOfSpeech;
    // TODO semanticTag is temprary solution to distinguish polyseme
    // only merge synonym with the same semantic tag.
    String semanticTag;

    /**
     * Constructor with argument. When construcitng an Entry, we only need to
     * provide pronunciation and partOfSpeech, semantic tag. The id is assigned by
     * ourself, and it depends on the number of existing entries. The id is set when
     * it's added into the EntryManager.
     *
     * @param pronunciation
     *            pronunciation of the entry
     * @param partOfSpeech
     *            partOfSpeech tag of the entry
     * @param semanticTag
     *            semantic tag of the entry
     */
    public Entry(String pronunciation, String partOfSpeech, String semanticTag) {
        this.pronunciation = pronunciation;
        this.partOfSpeech = partOfSpeech;
        // TODO: change POS to length-pos-length-pos
        this.semanticTag = semanticTag;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public String getPronunciation() {
        return pronunciation;
    }

    public String getPartOfSpeech() {
        return partOfSpeech;
    }

    public String getSemanticTag() {
        return semanticTag;
    }
}
