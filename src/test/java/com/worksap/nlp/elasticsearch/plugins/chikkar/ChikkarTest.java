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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.worksap.nlp.sudachi.Morpheme;

public class ChikkarTest {

    static Analyzer analyzer;
    static Chikkar chikkar;
    static Chikkar chikkar2;
    static Chikkar chikkar3;
    static Chikkar chikkar4;

    @ClassRule
    public static TemporaryFolder tempFolder = new TemporaryFolder();

    @BeforeClass
    public static void setUpBeforeClass() throws IOException {
        TokenizerFactory tokenizer = new WhitespaceTokenizerFactory();
        List<CharFilterFactory> charFilters = new ArrayList<>();
        List<TokenFilterFactory> tokenFilters = new ArrayList<>();

        analyzer = new CustomAnalyzer(tokenizer, charFilters.toArray(new CharFilterFactory[0]),
                tokenFilters.stream().map(TokenFilterFactory::getSynonymFilter).toArray(TokenFilterFactory[]::new));

        String nameTest = "test.txt";
        String nameOverWrite = "testOverwrite.txt";
        String nameDirected = "directed.txt";
        String nameMerge = "synonymMergeA.txt";

        tempFolder.create();
        final String tempPath = tempFolder.getRoot().getAbsolutePath();
        Path pathTest = Paths.get(tempPath, nameTest);
        Path pathTestOverWrite = Paths.get(tempPath, nameOverWrite);
        Path pathDirected = Paths.get(tempPath, nameDirected);
        Path pathMerge = Paths.get(tempPath, nameMerge);

        Files.copy(ChikkarTest.class.getResourceAsStream("/" + nameTest), pathTest);
        Files.copy(ChikkarTest.class.getResourceAsStream("/" + nameOverWrite), pathTestOverWrite);
        Files.copy(ChikkarTest.class.getResourceAsStream("/" + nameDirected), pathDirected);
        Files.copy(ChikkarTest.class.getResourceAsStream("/" + nameMerge), pathMerge);

        chikkar = new Chikkar(analyzer);
        chikkar.loadDictionary(pathTest);

        chikkar2 = new Chikkar(analyzer);
        chikkar2.loadDictionary(pathDirected);

        chikkar3 = new Chikkar(analyzer);
        chikkar3.loadDictionary(pathTest);
        chikkar3.loadDictionary(pathTestOverWrite);

        chikkar4 = new Chikkar(analyzer);
        chikkar4.loadDictionary(pathMerge);
    }

    @Test
    public void testGetWithMerge() {
        List<String> rtn = chikkar.get("A");
        List<String> expectList = Arrays.asList("A", "B", "C", "D", "E", "F", "G");
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar.get("B");
        expectList = Arrays.asList("A", "B", "C");
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar.get("C");
        expectList = Arrays.asList("A", "B", "C", "F");
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar.get("D");
        expectList = Arrays.asList("A", "D", "E");
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar.get("E");
        expectList = Arrays.asList("A", "D", "E");
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar.get("F");
        expectList = Arrays.asList("A", "C", "F", "G");
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar.get("G");
        expectList = Arrays.asList("A", "F", "G");
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar4.get("曖昧");
        expectList = Arrays.asList("曖昧", "不明確", "あやふや", "あいまい");
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);
    }

    @Test
    public void testGetWithDirect() {
        List<String> rtn = chikkar.get("AA");
        List<String> expectList = Arrays.asList("BB", "CC", "DD", "FF");
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar.get("BB");
        expectList = Arrays.asList("CC", "DD");
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar.get("CC");
        expectList = Arrays.asList("DD", "EE");
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar.get("DD");
        expectList = Arrays.asList("DD", "EE");
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar.get("EE");
        expectList = Collections.emptyList();
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar.get("FF");
        expectList = Collections.emptyList();
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar2.get("A");
        Collections.sort(rtn);
        assertEquals(Arrays.asList("A", "B", "C", "D"), rtn);

        rtn = chikkar2.get("B");
        Collections.sort(rtn);
        assertEquals(Arrays.asList("A", "B", "C"), rtn);

        rtn = chikkar2.get("C");
        Collections.sort(rtn);
        assertEquals(Arrays.asList("A", "B", "C", "D"), rtn);

        rtn = chikkar2.get("D");
        Collections.sort(rtn);
        assertEquals(Collections.emptyList(), rtn);

        rtn = chikkar2.get("ABC");
        Collections.sort(rtn);
        assertEquals(Arrays.asList("DEF"), rtn);
    }

    @Test
    public void testGetWithOverwrite() {
        List<String> rtn = chikkar3.get("A");
        List<String> expectList = Arrays.asList("A", "D", "G");
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar3.get("B");
        expectList = Arrays.asList("A", "B", "C");
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar3.get("C");
        expectList = Arrays.asList("A", "B", "C", "F");
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar3.get("D");
        expectList = Arrays.asList("A", "D", "G");
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar3.get("E");
        expectList = Arrays.asList("A", "D", "E");
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar3.get("F");
        expectList = Arrays.asList("A", "C", "F", "G");
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar3.get("G");
        expectList = Arrays.asList("A", "D", "G");
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar3.get("AA");
        expectList = Arrays.asList("CC", "DD");
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar3.get("BB");
        expectList = Arrays.asList("CC", "DD");
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar3.get("CC");
        expectList = Arrays.asList("DD", "EE");
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar3.get("DD");
        expectList = Arrays.asList("DD", "EE");
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar3.get("EE");
        expectList = Collections.emptyList();
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar3.get("FF");
        expectList = Collections.emptyList();
        Collections.sort(rtn);
        Collections.sort(expectList);
        assertEquals(expectList, rtn);
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

        List<String> rtn = chikkar4.find(morphemeList, 0, 2);
        Collections.sort(rtn);

        List<String> expectList = Arrays.asList("粗筋", "荒筋", "あらすじ");
        Collections.sort(expectList);
        assertEquals(expectList, rtn);
    }

    @Test
    public void testFindWithPosition() {
        List<String> rtn = chikkar4.find("概略のあいまい", 3, 7);
        Collections.sort(rtn);
        List<String> expectList = Arrays.asList("曖昧", "あいまい");
        Collections.sort(expectList);
        assertEquals(expectList, rtn);

        rtn = chikkar4.find("概略の曖昧", 3, 5);
        Collections.sort(rtn);
        expectList = Arrays.asList("曖昧", "不明確", "あやふや", "あいまい");
        Collections.sort(expectList);
        assertEquals(expectList, rtn);
    }

    @Test
    public void testFind() {
        List<String> rtn = chikkar4.find("概略のあいまい");
        Collections.sort(rtn);

        List<String> expectList = Arrays.asList("粗筋", "概略", "大略", "概要", "大要", "要約", "要旨", "梗概");
        Collections.sort(expectList);
        assertEquals(expectList, rtn);
    }

    static class WhitespaceTokenizerFactory implements TokenizerFactory {
        @Override
        public String name() {
            return "WhitespaceTokenizer";
        }

        @Override
        public Tokenizer create() {
            return new WhitespaceTokenizer();
        }
    }

}
