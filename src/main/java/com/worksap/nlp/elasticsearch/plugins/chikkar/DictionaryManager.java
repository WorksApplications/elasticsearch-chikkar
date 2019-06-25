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

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.lang.Character.isDigit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.CharsRefBuilder;

import com.worksap.nlp.elasticsearch.plugins.analysis.ChikkarSynonymMap;

/**
 * DictionaryManager functions as a dictionary repository, which stores and
 * returns dictionaries. The class is defined as a singleton instance so that
 * all Chikkar instances have access to the same repository.
 *
 * @author zhao_ya@worksap.co.jp &amp; xiao_w@worksap.co.jp &amp;
 *         liu_to@worksap.co.jp
 */

class DictionaryManager implements Serializable {
    private static final Logger log = LogManager.getLogger(DictionaryManager.class);

    EntryManager entryMgn;

    private static DictionaryManager dictMgn;

    enum LoadType {
        ADD, MERGE, CANCEL, DIRECTED, SKIP
    }

    class LoadResult {
        LoadType loadType;
        String semanticTag;

        public LoadResult(LoadType loadType, String semanticTag) {
            this.loadType = loadType;
            this.semanticTag = semanticTag;
        }

        public LoadType getLoadType() {
            return this.loadType;
        }

        public String getSemanticTag() {
            return this.semanticTag;
        }
    }

    public DictionaryManager() {
        entryMgn = new EntryManager();
    }

    /**
     * Get singleton instance of DictionaryManager.
     *
     * @return The singleton instance of DictionaryManager
     */
    public static DictionaryManager getInstance() {
        if (dictMgn == null) {
            dictMgn = new DictionaryManager();
        }
        return dictMgn;
    }

    /**
     * Returns a result for a query based on the relation defined in this
     * dictionary. Since a phrase may have more than one phrases that satisfy the
     * relation, a {@code List<String>} is returned.
     *
     * @param relationMgn
     *            A RelationManager instance which stores the relation per user. As
     *            different users can use different dictionaries and this Class is
     *            singleton instance, the relation should be user level
     * @param query
     *            the head word.
     * @param pos
     *            partOfSpeech, value can be null.
     * @param pronun
     *            pronunciation, value can be null.
     * @param semanticTag
     *            semantic tag, value can be null.
     * @return {@code List<String>} List of phrases that satisfies the relation.
     */
    public List<String> findRelation(RelationManager relationMgn, String query, String pos, String pronun,
            String semanticTag) {
        List<String> rtn = new ArrayList<>();
        if (!entryMgn.retrieveEntry(query, pos, pronun, semanticTag).isEmpty()) {
            int u = entryMgn.retrieveEntry(query, pos, pronun, semanticTag).get(0);
            rtn = entryMgn.getWordsFromId(relationMgn.getRelationMatrix().getRelations(u));
        }
        rtn.add(query);
        return rtn;
    }

    public String getDefaultSemanticTag(String query, String pos, String pronun) {
        String semanticTag = null;
        if (!entryMgn.retrieveEntry(query, pos, pronun, null).isEmpty()) {
            int u = entryMgn.retrieveEntry(query, pos, pronun, null).get(0);
            String word = entryMgn.getWordsFromId(u).get(0);
            semanticTag = entryMgn.getEntries(word).get(0).getSemanticTag();
        }
        return semanticTag;
    }

    public String findLongestWordWithRelation(String input, int start, int end) {
        return entryMgn.findLongestWordWithRelation(input, start, end);
    }

    /**
     * Get all words stored in dictionary in dictionary order
     *
     * @return List of words
     */
    public List<String> getSortedKeys() {
        return entryMgn.getSortedKeys();
    }

    /**
     * Get all synonym id of given query word. A {@code List<Integer>} is returned.
     *
     * @param relationMgn
     *            A RelationManager instance which stores the relation per user. As
     *            different users can use different dictionaries and this Class is
     *            singleton instance, the relation should be user level
     * @param query
     *            the head word.
     * @param pos
     *            partOfSpeech, value can be null.
     * @param pronun
     *            pronunciation, value can be null.
     * @param semanticTag
     *            semantic tag, value can be null.
     * @return List of all synonym id
     */
    public List<Integer> findSynonymId(RelationManager relationMgn, String query, String pos, String pronun,
            String semanticTag) {
        List<Integer> rtn = new ArrayList<>();
        if (!entryMgn.retrieveEntry(query, pos, pronun, semanticTag).isEmpty()) {
            int u = entryMgn.retrieveEntry(query, pos, pronun, semanticTag).get(0);
            rtn.addAll(relationMgn.getRelationMatrix().getRelations(u));
        }
        return rtn;
    }

    /**
     * Get all words which have the specified id.
     *
     * @param id
     *            An integer which stands for the word id.
     * @return List of words which have the specified id.
     */
    public List<String> getWordsFromId(int id) {
        return entryMgn.getWordsFromId(id);
    }

    /**
     * Parse one dictionary line
     *
     * @param analyzer
     *            An analyzer instance which is used to analyze the entries in
     *            dictionary.
     * @param line
     *            The line to parse.
     * @param basewords
     *            A list contains base words of the line
     * @param relatives
     *            A list contains relative words of the line
     * @param restrictMode
     *            if true, then ignore all directed synonyms when parsing dictionary
     *            line
     * @return LoadResult return load type and semantic tag.
     */
    LoadResult loadDictionaryLine(Analyzer analyzer, String line, ArrayList<Integer> basewords,
            ArrayList<Integer> relatives, boolean restrictMode) {
        // TODO parse dictionary file, may need modification if dictionary format change
        // use semantic tag to help merge
        String semanticTag = null;

        // SKIP
        if (line.length() < 1)
            return new LoadResult(LoadType.SKIP, semanticTag);
        if (line.charAt(0) == '!' && line.charAt(1) == '!')
            return new LoadResult(LoadType.SKIP, semanticTag);
        if (isDigit(line.charAt(0)))
            return new LoadResult(LoadType.SKIP, semanticTag);

        // DIRECTED
        String[] directions = {};
        if (line.split(">>").length > 1 || line.split("=>").length > 1 || line.split("<<").length > 1
                || line.split("<=").length > 1) {
            if (restrictMode)
                return new LoadResult(LoadType.SKIP, semanticTag);
            boolean left = true;
            if (line.split(">>").length > 1)
                directions = line.split(">>");
            if (line.split("=>").length > 1)
                directions = line.split("=>");
            if (line.split("<<").length > 1) {
                left = false;
                directions = line.split("<<");
            }
            if (line.split("<=").length > 1) {
                left = false;
                directions = line.split("<=");
            }

            String[] last = directions[directions.length - 1].split(",");
            semanticTag = last[last.length - 1].charAt(0) == '(' ? last[last.length - 1] : null;

            for (int i = 0; i < directions.length; i++) {
                String[] temp = directions[i].split(",");
                for (int j = 0; j < temp.length; j++) {
                    String word = temp[j].trim();
                    if (word.charAt(0) != '(') {
                        List<String> resList = analyze(analyzer, word);
                        for (String res : resList) {
                            if (entryMgn.retrieveEntry(res, null, null, semanticTag).isEmpty()) {
                                entryMgn.insertEntry(res, new Entry(null, null, semanticTag));
                            }
                            if (left && i == 0) {
                                basewords.add(entryMgn.retrieveEntry(res, null, null, semanticTag).get(0));
                            } else if (!left && i == directions.length - 1) {
                                basewords.add(entryMgn.retrieveEntry(res, null, null, semanticTag).get(0));
                            } else {
                                relatives.add(entryMgn.retrieveEntry(res, null, null, semanticTag).get(0));
                            }
                        }
                    }
                }
            }
            return new LoadResult(LoadType.DIRECTED, semanticTag);
        } else {
            // MERGE, CANCEL, ADD
            String content = line;
            LoadType loadType = LoadType.ADD;
            if (line.charAt(0) == '*' || line.charAt(0) == '!') {
                content = line.substring(1);
                if (line.charAt(0) == '*')
                    loadType = LoadType.MERGE;
                if (line.charAt(0) == '!')
                    loadType = LoadType.CANCEL;
            }
            String[] words = content.split(",");
            semanticTag = fillWordsToIds(analyzer, words, basewords, relatives);
            return new LoadResult(loadType, semanticTag);
        }
    }

    String fillWordsToIds(Analyzer analyzer, String[] words, ArrayList<Integer> basewords,
            ArrayList<Integer> relatives) {
        String semanticTag = words[words.length - 1].charAt(0) == '(' ? words[words.length - 1] : null;
        for (int i = 0; i < words.length; i++) {
            String word = words[i].trim();
            if (word.charAt(0) != '(') {
                List<String> resList = analyze(analyzer, word);
                for (String res : resList) {
                    if (entryMgn.retrieveEntry(res, null, null, semanticTag).isEmpty()) {
                        entryMgn.insertEntry(res, new Entry(null, null, semanticTag));
                    }
                    if (i == 0) {
                        basewords.add(entryMgn.retrieveEntry(res, null, null, semanticTag).get(0));
                    } else {
                        relatives.add(entryMgn.retrieveEntry(res, null, null, semanticTag).get(0));
                    }
                }
            }
        }
        return semanticTag;
    }

    /**
     * Add new dictionary into DictionaryManager.
     *
     * @param relationMgn
     *            A RelationManager instance which stores the relation per user. As
     *            different users can use different dictionaries and this Class is
     *            singleton instance, the relation should be user level
     * @param dictPath
     *            A dictionary path.
     * @param analyzer
     *            An analyzer instance which is used to analyze the entries in
     *            dictionary. As DictionaryManager is singleton instance, and
     *            different users may use different analyzers(e.g. Japanese or
     *            English), so we need to pass the specific analyzer instance when
     *            addDictionary.
     * @param restrictMode
     *            if true, then ignore all directed synonyms when parsing dictionary
     *            line
     */
    public synchronized void addDictionary(RelationManager relationMgn, String dictPath, Analyzer analyzer,
            boolean restrictMode) {
        // set the default relationSet as sparse matrix
        RelationManager.RelationMatrix relationMatrix = relationMgn.getRelationMatrix();

        try (Stream<String> input = Files.lines(Paths.get(dictPath), StandardCharsets.UTF_8)) {
            input.forEach(line -> {
                ArrayList<Integer> basewords = new ArrayList<>();
                ArrayList<Integer> relatives = new ArrayList<>();
                LoadResult loadResult = loadDictionaryLine(analyzer, line, basewords, relatives, restrictMode);

                switch (loadResult.getLoadType()) {
                case ADD:
                    addLine(basewords, relatives, relationMatrix, loadResult.getSemanticTag());
                    break;
                case MERGE:
                    mergeLine(basewords, relatives, relationMatrix, loadResult.getSemanticTag());
                    break;
                case CANCEL:
                    cancelLine(basewords, relatives, relationMatrix);
                    break;
                case DIRECTED:
                    addDirectedLine(basewords, relatives, relationMatrix, loadResult.getSemanticTag());
                    break;
                case SKIP:
                    break;
                }
            });
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    /**
     * Analyzes the text with the analyzer and separates by
     * {@link ChikkarSynonymMap#WORD_SEPARATOR}.
     *
     * @param analyzer
     *            An analyzer instance which is used to analyze the input text
     * @param text
     *            input text
     * @return List of analyzed result text
     */
    List<String> analyze(Analyzer analyzer, String text) {
        CharsRefBuilder reuse = new CharsRefBuilder();
        reuse.clear();

        List<String> tlist = new ArrayList<>();

        try {
            TokenStream ts = analyzer.tokenStream("", text);
            CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
            PositionLengthAttribute posLen = ts.addAttribute(PositionLengthAttribute.class);
            ts.reset();

            while (ts.incrementToken()) {
                int length = termAtt.length();
                if (length == 0) {
                    return tlist;
                }

                if (posLen.getPositionLength() > 1) {
                    // skip duplicate tokens with coarse granularity when sudachi / kuromoji is set
                    // to search mode
                    if (length == text.length()) {
                        tlist.add(text);
                    }
                    continue;
                }

                reuse.grow(reuse.length() + length + 1); /* current + word + separator */
                int end = reuse.length();
                if (reuse.length() > 0) {
                    reuse.setCharAt(end++, ChikkarSynonymMap.WORD_SEPARATOR);
                    reuse.setLength(reuse.length() + 1);
                }
                System.arraycopy(termAtt.buffer(), 0, reuse.chars(), end, length);
                reuse.setLength(reuse.length() + length);
            }

            ts.end();
            ts.close();
        } catch (IOException e) {
            return tlist;
        }

        if (reuse.length() == 0) {
            return tlist;
        }
        CharsRef ref = reuse.get();
        tlist.add(new String(ref.chars, ref.offset, ref.length));
        return tlist;
    }

    void addLine(ArrayList<Integer> basewords, ArrayList<Integer> relatives,
            RelationManager.RelationMatrix relationMatrix, String semanticTag) {
        ArrayList<Integer> words = new ArrayList<>();
        words.addAll(basewords);
        words.addAll(relatives);
        words.forEach(a -> words.forEach(b -> {
            if (!a.equals(b)) {
                relationMatrix.add(a, b, semanticTag);
            }
        }));
    }

    void mergeLine(ArrayList<Integer> basewords, ArrayList<Integer> relatives,
            RelationManager.RelationMatrix relationMatrix, String semanticTag) {
        ArrayList<Integer> previous = new ArrayList<>();
        basewords.stream()
                .filter(a -> (relationMatrix.getSemanticTag(a) == null && semanticTag == null)
                        || relationMatrix.getSemanticTag(a).equals(semanticTag))
                .forEach(a -> previous.addAll(relationMatrix.getRelations(a)));
        relatives.forEach(a -> previous.forEach(b -> {
            // Todo check if the semantic tag is the same
            relationMatrix.add(a, b, semanticTag);
            relationMatrix.add(b, a, semanticTag);
        }));
        addLine(basewords, relatives, relationMatrix, semanticTag);
    }

    void cancelLine(ArrayList<Integer> basewords, ArrayList<Integer> relatives,
            RelationManager.RelationMatrix relationMatrix) {
        ArrayList<Integer> words = new ArrayList<>();
        words.addAll(basewords);
        words.addAll(relatives);
        words.forEach(a -> words.forEach(b -> {
            if (!a.equals(b)) {
                relationMatrix.delete(a, b);
            }
        }));
    }

    void addDirectedLine(ArrayList<Integer> basewords, ArrayList<Integer> relatives,
            RelationManager.RelationMatrix relationMatrix, String semanticTag) {
        basewords.forEach(a -> relatives.forEach(b -> {
            relationMatrix.add(a, b, semanticTag);
            relationMatrix.delete(b, a);
        }));

        relatives.forEach(a -> relatives.forEach(b -> {
            if (!a.equals(b)) {
                relationMatrix.delete(a, b);
            }
        }));
    }
}
