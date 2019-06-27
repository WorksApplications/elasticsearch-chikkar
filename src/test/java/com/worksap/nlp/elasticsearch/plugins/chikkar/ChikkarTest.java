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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.elasticsearch.index.analysis.CharFilterFactory;
import org.elasticsearch.index.analysis.CustomAnalyzer;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.index.analysis.TokenizerFactory;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.nustaq.serialization.FSTConfiguration;
import org.nustaq.serialization.FSTObjectInput;
import org.nustaq.serialization.FSTObjectOutput;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.worksap.nlp.sudachi.Morpheme;

public class ChikkarTest {

    static Analyzer analyzer;
    static Chikkar chikkar;
    static Chikkar chikkar2;
    static Chikkar chikkar3;
    static Chikkar chikkarSer;

    static List<String> expectList = new ArrayList<>();

    @ClassRule
    public static TemporaryFolder tempFolder = new TemporaryFolder();

    static final String[][] groups = { { "曖昧", "不明確", "あやふや", "あいまい" },
            { "粗筋", "概略", "大略", "概要", "大要", "要約", "要旨", "梗概" }, { "粗筋", "荒筋", "あらすじ" } };

    static final String[][] test = { { "A", "B", "D", "E", "G", }, { "B", "A", "C", "F", "G" }, { "C", "B", "G" },
            { "D", "A", "E", "F", "G" }, { "E", "A", "D", "F", "G" }, { "F", "D", "E", "G", "B" },
            { "G", "A", "D", "F", "E", "B", "C" },

            // index 7 (org)
            { "A", "K", "L", "O", "M", "C", "H", "X" }, { "K", "A", "L", "O", "M" }, { "L", "K", "A", "O", "M" },
            { "O", "K", "A", "L", "M" }, { "C" }, { "X" }, { "H" }, { "J", "A", "I" }, { "I" },

            // index 16 (Wiki)
            { "X", "Z", "W" }, { "Y", "Z", "W" }, { "Z" }, { "W" } };

    @BeforeClass
    public static void setUpBeforeClass() throws IOException, ClassNotFoundException {
        TokenizerFactory tokenizer = new WhitespaceTokenizerFactory();
        List<CharFilterFactory> charFilters = new ArrayList<>();
        List<TokenFilterFactory> tokenFilters = new ArrayList<>();

        analyzer = new CustomAnalyzer("synonyms", tokenizer, charFilters.toArray(new CharFilterFactory[0]),
                tokenFilters.stream().map(TokenFilterFactory::getSynonymFilter).toArray(TokenFilterFactory[]::new));

        String nameA1 = "synonymMergeA1.txt";
        String nameA2 = "synonymMergeA2.txt";
        String nameTest = "test.txt";
        String nameDirected = "directed.txt";

        tempFolder.create();
        final String tempPath = tempFolder.getRoot().getAbsolutePath();
        Path pathA1 = Paths.get(tempPath, nameA1);
        Path pathA2 = Paths.get(tempPath, nameA2);
        Path pathTest = Paths.get(tempPath, nameTest);
        Path pathDirected = Paths.get(tempPath, nameDirected);

        Files.copy(ChikkarTest.class.getResourceAsStream("/" + nameA1), pathA1);
        Files.copy(ChikkarTest.class.getResourceAsStream("/" + nameA2), pathA2);
        Files.copy(ChikkarTest.class.getResourceAsStream("/" + nameTest), pathTest);
        Files.copy(ChikkarTest.class.getResourceAsStream("/" + nameDirected), pathDirected);

        chikkar = new Chikkar(analyzer);
        chikkar.loadDictionary(pathA1.toString());
        chikkar.loadDictionary(pathA2.toString());
        chikkar.loadDictionary(pathTest.toString());

        chikkar2 = new Chikkar(analyzer, true);
        chikkar2.loadDictionary(pathDirected.toString());

        chikkar3 = new Chikkar(analyzer, false);
        chikkar3.loadDictionary(pathDirected.toString());

        Path binPath = Paths.get(tempPath, "chikkarDict.bin");
        final FSTConfiguration conf = FSTConfiguration.createDefaultConfiguration();

        try (FileOutputStream fileOut = new FileOutputStream(binPath.toFile());
                FSTObjectOutput out = conf.getObjectOutput(fileOut)) {
            chikkar.dumpToStream(out);
        }

        try (FileInputStream fileIn = new FileInputStream(binPath.toFile());
                FSTObjectInput in = conf.getObjectInput(fileIn)) {
            chikkarSer = new Chikkar(analyzer, in);
        }
    }

    @Test
    public void testGetWithMerge() {
        for (int i = 0; i < 7; i++) {
            expectList.clear();
            List<String> rtn = chikkar.get(test[i][0], null, null);
            Collections.sort(rtn);
            expectList.addAll(Arrays.asList(test[i]));
            Collections.sort(expectList);
            assertEquals(expectList, rtn);
        }
    }

    @Test
    public void testGetWithDirect() {
        for (int i = 16; i < 20; i++) {
            expectList.clear();
            List<String> rtn = chikkar.get(test[i][0], null, null, "(Wiki)");
            Collections.sort(rtn);
            expectList.addAll(Arrays.asList(test[i]));
            Collections.sort(expectList);
            assertEquals(expectList, rtn);
        }
    }

    @Test
    public void testGetWithRestrictMode() {
        /**
         * A,B,C A>>D C>>D
         */

        // restrict mode on
        List<String> rtn = chikkar2.get("A", null, null, null);
        Collections.sort(rtn);
        assertEquals(Arrays.asList("A", "B", "C"), rtn);

        rtn = chikkar2.get("B", null, null, null);
        Collections.sort(rtn);
        assertEquals(Arrays.asList("A", "B", "C"), rtn);

        rtn = chikkar2.get("C", null, null, null);
        Collections.sort(rtn);
        assertEquals(Arrays.asList("A", "B", "C"), rtn);

        rtn = chikkar2.get("D", null, null, null);
        Collections.sort(rtn);
        assertEquals(Arrays.asList("D"), rtn);

        // restrict mode off
        rtn = chikkar3.get("A", null, null, null);
        Collections.sort(rtn);
        assertEquals(Arrays.asList("A", "B", "C", "D"), rtn);

        rtn = chikkar3.get("B", null, null, null);
        Collections.sort(rtn);
        assertEquals(Arrays.asList("A", "B", "C"), rtn);

        rtn = chikkar3.get("C", null, null, null);
        Collections.sort(rtn);
        assertEquals(Arrays.asList("A", "B", "C", "D"), rtn);

        rtn = chikkar3.get("D", null, null, null);
        Collections.sort(rtn);
        assertEquals(Arrays.asList("D"), rtn);
    }

    @Test
    public void testGetWithMergeTag() {
        for (int i = 7; i < 16; i++) {
            expectList.clear();
            List<String> rtn = chikkar.get(test[i][0], null, null, "(org)");
            Collections.sort(rtn);
            expectList.addAll(Arrays.asList(test[i]));
            Collections.sort(expectList);
            assertEquals(expectList, rtn);
        }
    }

    @Test
    public void testGet() {
        for (int i = 0; i < groups.length; ++i) {
            for (int j = 0; j < groups[i].length; ++j) {
                List<String> rtn = chikkar.get(groups[i][j]);
                Collections.sort(rtn);

                if (j == 0 && i != 0) {
                    expectList.clear();
                    expectList.addAll(Arrays.asList(groups[1]));
                    expectList.add("あらすじ");
                    expectList.add("荒筋");
                    Collections.sort(expectList);
                    assertEquals(expectList, rtn);
                    continue;
                }

                expectList.clear();
                expectList.addAll(Arrays.asList(groups[i]));
                Collections.sort(expectList);
                assertEquals(expectList, rtn);
            }
        }
    }

    @Test
    public void testFindListOfMorpheme() {
        Morpheme mor1 = mock(Morpheme.class);
        Morpheme mor2 = mock(Morpheme.class);
        when(mor1.surface()).thenReturn("曖昧");
        when(mor2.surface()).thenReturn("あらすじ");

        List<Morpheme> morphemeList = new ArrayList<>();
        morphemeList.add(mor1);
        morphemeList.add(mor2);

        List<String> rtn = chikkar.find(morphemeList, 0, 2);
        Collections.sort(rtn);

        expectList.clear();
        expectList.addAll(Arrays.asList(groups[2]));
        Collections.sort(expectList);
        assertEquals(expectList, rtn);
    }

    @Test
    public void testFindWithPosition() {
        List<String> rtn = chikkar.find("概略のあいまい", 3, 7);
        Collections.sort(rtn);

        expectList.clear();
        expectList.addAll(Arrays.asList(groups[0]));
        Collections.sort(expectList);
        assertEquals(expectList, rtn);
    }

    @Test
    public void testFind() {
        List<String> rtn = chikkar.find("概略のあいまい");
        Collections.sort(rtn);

        expectList.clear();
        expectList.addAll(Arrays.asList(groups[1]));
        Collections.sort(expectList);
        assertEquals(expectList, rtn);
    }

    @Test
    public void testChikkarSerialization() {
        for (int i = 0; i < 7; i++) {
            List<String> rtn = chikkarSer.get(test[i][0], null, null);
            Collections.sort(rtn);
            expectList.clear();
            expectList.addAll(Arrays.asList(test[i]));
            Collections.sort(expectList);
            assertEquals(expectList, rtn);
        }

        for (int i = 7; i < 16; i++) {
            List<String> rtn = chikkarSer.get(test[i][0], null, null, "(org)");
            Collections.sort(rtn);
            expectList.clear();
            expectList.addAll(Arrays.asList(test[i]));
            Collections.sort(expectList);
            assertEquals(expectList, rtn);
        }

        for (int i = 16; i < 20; i++) {
            List<String> rtn = chikkarSer.get(test[i][0], null, null, "(Wiki)");
            Collections.sort(rtn);
            expectList.clear();
            expectList.addAll(Arrays.asList(test[i]));
            Collections.sort(expectList);
            assertEquals(expectList, rtn);
        }

        for (int i = 0; i < groups.length; ++i) {
            for (int j = 0; j < groups[i].length; ++j) {
                List<String> rtn = chikkarSer.get(groups[i][j]);
                Collections.sort(rtn);

                if (j == 0 && i != 0) {
                    expectList.clear();
                    expectList.addAll(Arrays.asList(groups[1]));
                    expectList.add("あらすじ");
                    expectList.add("荒筋");
                    Collections.sort(expectList);
                    assertEquals(expectList, rtn);
                    continue;
                }

                expectList.clear();
                expectList.addAll(Arrays.asList(groups[i]));
                Collections.sort(expectList);
                assertEquals(expectList, rtn);
            }
        }

        Morpheme mor1 = mock(Morpheme.class);
        Morpheme mor2 = mock(Morpheme.class);
        when(mor1.surface()).thenReturn("曖昧");
        when(mor2.surface()).thenReturn("あらすじ");

        List<Morpheme> morphemeList = new ArrayList<>();
        morphemeList.add(mor1);
        morphemeList.add(mor2);

        List<String> rtn = chikkarSer.find(morphemeList, 0, 2);
        Collections.sort(rtn);

        expectList.clear();
        expectList.addAll(Arrays.asList(groups[2]));
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkarSer.find("概略のあいまい", 3, 7);
        Collections.sort(rtn);

        expectList.clear();
        expectList.addAll(Arrays.asList(groups[0]));
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkarSer.find("概略のあいまい");
        Collections.sort(rtn);

        expectList.clear();
        expectList.addAll(Arrays.asList(groups[1]));
        Collections.sort(expectList);
        assertEquals(expectList, rtn);
    }

    static class WhitespaceTokenizerFactory implements TokenizerFactory {
        @Override
        public Tokenizer create() {
            return new WhitespaceTokenizer();
        }
    }

}
