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

package com.worksap.nlp.elasticsearch.plugins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.worksap.nlp.elasticsearch.plugins.analysis.ChikkarSynonymGraphTokenFilterFactory;
import com.worksap.nlp.elasticsearch.plugins.analysis.ChikkarSynonymTokenFilterFactory;
import com.worksap.nlp.elasticsearch.plugins.analysis.ModSynonymTokenFilterFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.CharFilterFactory;
import org.elasticsearch.index.analysis.CustomAnalyzer;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.index.analysis.TokenizerFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AnalysisChikkarPluginTest {

    private Analyzer analyzer;
    private TokenFilterFactory chikkarSynonymGraphFactoryA; // for user A
    private TokenFilterFactory chikkarSynonymGraphFactoryB; // for user B
    private TokenFilterFactory chikkarSynonymGraphFactoryC; // for user C
    private TokenFilterFactory chikkarSynonymGraphFactoryD; // for user D

    private TokenFilterFactory chikkarSynonymFactoryA; // for user A
    private TokenFilterFactory chikkarSynonymFactoryB; // for user B
    private TokenFilterFactory chikkarSynonymFactoryC; // for user C
    private TokenFilterFactory chikkarSynonymFactoryD; // for user D

    private TokenFilterFactory chikkarDirectedSynonymFactory;

    private TokenFilterFactory modSynonymFactoryD;

    private TokenFilterFactory modSynonymFactoryRef;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setUp() throws IOException {
        TokenizerFactory tokenizer = new WhitespaceTokenizerFactory();
        List<CharFilterFactory> charFilters = new ArrayList<>();
        List<TokenFilterFactory> tokenFilters = new ArrayList<>();

        analyzer = new CustomAnalyzer(tokenizer, charFilters.toArray(new CharFilterFactory[0]),
                tokenFilters.stream().map(TokenFilterFactory::getSynonymFilter).toArray(TokenFilterFactory[]::new));

        String[] dicts = { "synonymMergeA.txt", "synonymB.txt", "synonymC.txt", "synonymMultiToken.txt",
                "synonymDirected.txt", "test.txt" };

        tempFolder.create();
        Path configPath = Paths.get(tempFolder.getRoot().getAbsolutePath());

        for (String dict : dicts) {
            Files.copy(getClass().getResourceAsStream("/" + dict), configPath.resolve(dict));
        }

        chikkarSynonymGraphFactoryA = createChikkarSynonymGraphFactory(configPath, "synonymMergeA.txt", "id1",
                tokenizer, charFilters, tokenFilters);
        chikkarSynonymGraphFactoryB = createChikkarSynonymGraphFactory(configPath, "synonymB.txt", "id2", tokenizer,
                charFilters, tokenFilters);
        chikkarSynonymGraphFactoryC = createChikkarSynonymGraphFactory(configPath, "synonymC.txt", "id3", tokenizer,
                charFilters, tokenFilters);
        chikkarSynonymGraphFactoryD = createChikkarSynonymGraphFactory(configPath, "synonymMultiToken.txt", "id4",
                tokenizer, charFilters, tokenFilters);

        chikkarSynonymFactoryA = createChikkarSynonymFactory(configPath, "synonymMergeA.txt", "id5", tokenizer,
                charFilters, tokenFilters);
        chikkarSynonymFactoryB = createChikkarSynonymFactory(configPath, "synonymB.txt", "id6", tokenizer, charFilters,
                tokenFilters);
        chikkarSynonymFactoryC = createChikkarSynonymFactory(configPath, "synonymC.txt", "id7", tokenizer, charFilters,
                tokenFilters);
        chikkarSynonymFactoryD = createChikkarSynonymFactory(configPath, "synonymMultiToken.txt", "id8", tokenizer,
                charFilters, tokenFilters);

        chikkarDirectedSynonymFactory = createChikkarSynonymFactory(configPath, "synonymDirected.txt", "id9", tokenizer,
                charFilters, tokenFilters);

        modSynonymFactoryD = createSynonymFactory(configPath, "synonymMultiToken.txt", tokenizer, charFilters,
                tokenFilters);

        modSynonymFactoryRef = createSynonymFactory(configPath, "synonymDirected.txt", tokenizer, charFilters,
                tokenFilters);
    }

    @Test
    public void testGetSingleTokenSynonymsByUsingChikkarSynonymGraphTokenFilterInUserA() throws Exception {
        /*
         * 曖昧,不明確,あやふや,あいまい 粗筋,概略,大略,概要,大要,要約,要旨,梗概 粗筋,荒筋,あらすじ
         *
         * All words without space inside will be treated as single-token synonym as the
         * test case use org.apache.lucene.analysis.core.WhitespaceTokenizer to tokenize
         * query for simplicity.
         */

        // case 1
        String query = "曖昧";
        List<TokenAttribute> expected = Arrays.asList(new TokenAttribute("曖昧", "word", 0, 0, 2, 1),
                new TokenAttribute("不明確", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("あやふや", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("あいまい", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        List<TokenAttribute> result = parseSynonyms(chikkarSynonymGraphFactoryA, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 2
        query = "不明確";
        expected = Arrays.asList(new TokenAttribute("曖昧", "SYNONYM", 0, 0, 3, 1),
                new TokenAttribute("不明確", "word", 0, 0, 3, 1), new TokenAttribute("あやふや", "SYNONYM", 0, 0, 3, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryA, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 3
        query = "あやふや";
        expected = Arrays.asList(new TokenAttribute("曖昧", "SYNONYM", 0, 0, 4, 1),
                new TokenAttribute("不明確", "SYNONYM", 0, 0, 4, 1), new TokenAttribute("あやふや", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryA, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 4
        query = "あいまい";
        expected = Arrays.asList(new TokenAttribute("曖昧", "SYNONYM", 0, 0, 4, 1),
                new TokenAttribute("あいまい", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryA, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 5
        query = "粗筋";
        expected = Arrays.asList(new TokenAttribute("粗筋", "word", 0, 0, 2, 1),
                new TokenAttribute("概略", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("大略", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("概要", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("大要", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("要約", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("要旨", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("梗概", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("荒筋", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("あらすじ", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryA, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 6
        query = "概要";
        expected = Arrays.asList(new TokenAttribute("粗筋", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("概略", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("大略", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("概要", "word", 0, 0, 2, 1), new TokenAttribute("大要", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("要約", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("要旨", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("梗概", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryA, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 7
        query = "梗概";
        expected = Arrays.asList(new TokenAttribute("粗筋", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("概略", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("大略", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("概要", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("大要", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("要約", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("要旨", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("梗概", "word", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryA, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 8
        query = "荒筋";
        expected = Arrays.asList(new TokenAttribute("粗筋", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("荒筋", "word", 0, 0, 2, 1), new TokenAttribute("あらすじ", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryA, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 9
        query = "あらすじ";
        expected = Arrays.asList(new TokenAttribute("粗筋", "SYNONYM", 0, 0, 4, 1),
                new TokenAttribute("荒筋", "SYNONYM", 0, 0, 4, 1), new TokenAttribute("あらすじ", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryA, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 10
        query = "首相";
        expected = Arrays.asList(new TokenAttribute("首相", "word", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryA, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 11
        query = "総理大臣";
        expected = Arrays.asList(new TokenAttribute("総理大臣", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryA, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);
    }

    @Test
    public void testGetSingleTokenSynonymsByUsingChikkarSynonymTokenFilterInUserA() throws Exception {
        /*
         * 曖昧,不明確,あやふや,あいまい 粗筋,概略,大略,概要,大要,要約,要旨,梗概 粗筋,荒筋,あらすじ
         *
         * All words without space inside will be treated as single-token synonym as the
         * test case use org.apache.lucene.analysis.core.WhitespaceTokenizer to tokenize
         * query for simplicity.
         */

        // case 1
        String query = "曖昧";
        List<TokenAttribute> expected = Arrays.asList(new TokenAttribute("曖昧", "word", 0, 0, 2, 1),
                new TokenAttribute("不明確", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("あやふや", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("あいまい", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        List<TokenAttribute> result = parseSynonyms(chikkarSynonymFactoryA, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 2
        query = "不明確";
        expected = Arrays.asList(new TokenAttribute("曖昧", "SYNONYM", 0, 0, 3, 1),
                new TokenAttribute("不明確", "word", 0, 0, 3, 1), new TokenAttribute("あやふや", "SYNONYM", 0, 0, 3, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryA, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 3
        query = "あやふや";
        expected = Arrays.asList(new TokenAttribute("曖昧", "SYNONYM", 0, 0, 4, 1),
                new TokenAttribute("不明確", "SYNONYM", 0, 0, 4, 1), new TokenAttribute("あやふや", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryA, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 4
        query = "あいまい";
        expected = Arrays.asList(new TokenAttribute("曖昧", "SYNONYM", 0, 0, 4, 1),
                new TokenAttribute("あいまい", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryA, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 5
        query = "粗筋";
        expected = Arrays.asList(new TokenAttribute("粗筋", "word", 0, 0, 2, 1),
                new TokenAttribute("概略", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("大略", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("概要", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("大要", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("要約", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("要旨", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("梗概", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("荒筋", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("あらすじ", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryA, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 6
        query = "概要";
        expected = Arrays.asList(new TokenAttribute("粗筋", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("概略", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("大略", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("概要", "word", 0, 0, 2, 1), new TokenAttribute("大要", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("要約", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("要旨", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("梗概", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryA, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 7
        query = "梗概";
        expected = Arrays.asList(new TokenAttribute("粗筋", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("概略", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("大略", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("概要", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("大要", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("要約", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("要旨", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("梗概", "word", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryA, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 8
        query = "荒筋";
        expected = Arrays.asList(new TokenAttribute("粗筋", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("荒筋", "word", 0, 0, 2, 1), new TokenAttribute("あらすじ", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryA, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 9
        query = "あらすじ";
        expected = Arrays.asList(new TokenAttribute("粗筋", "SYNONYM", 0, 0, 4, 1),
                new TokenAttribute("荒筋", "SYNONYM", 0, 0, 4, 1), new TokenAttribute("あらすじ", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryA, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 10
        query = "首相";
        expected = Arrays.asList(new TokenAttribute("首相", "word", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryA, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 11
        query = "総理大臣";
        expected = Arrays.asList(new TokenAttribute("総理大臣", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryA, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);
    }

    @Test
    public void testGetSingleTokenSynonymsByUsingChikkarSynonymGraphTokenFilterInUserB() throws Exception {
        /*
         * 曖昧,不明確,あやふや 粗筋,概略,大略 首相,総理大臣
         *
         * All words without space inside will be treated as single-token synonym as the
         * test case use org.apache.lucene.analysis.core.WhitespaceTokenizer to tokenize
         * query for simplicity.
         */

        // case 1
        String query = "曖昧";
        List<TokenAttribute> expected = Arrays.asList(new TokenAttribute("曖昧", "word", 0, 0, 2, 1),
                new TokenAttribute("不明確", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("あやふや", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        List<TokenAttribute> result = parseSynonyms(chikkarSynonymGraphFactoryB, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 2
        query = "不明確";
        expected = Arrays.asList(new TokenAttribute("曖昧", "SYNONYM", 0, 0, 3, 1),
                new TokenAttribute("不明確", "word", 0, 0, 3, 1), new TokenAttribute("あやふや", "SYNONYM", 0, 0, 3, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryB, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 3
        query = "あやふや";
        expected = Arrays.asList(new TokenAttribute("曖昧", "SYNONYM", 0, 0, 4, 1),
                new TokenAttribute("不明確", "SYNONYM", 0, 0, 4, 1), new TokenAttribute("あやふや", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryB, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 4
        query = "あいまい";
        expected = Arrays.asList(new TokenAttribute("あいまい", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryB, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 5
        query = "粗筋";
        expected = Arrays.asList(new TokenAttribute("粗筋", "word", 0, 0, 2, 1),
                new TokenAttribute("概略", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("大略", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryB, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 6
        query = "概略";
        expected = Arrays.asList(new TokenAttribute("粗筋", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("概略", "word", 0, 0, 2, 1), new TokenAttribute("大略", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryB, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 7
        query = "大略";
        expected = Arrays.asList(new TokenAttribute("粗筋", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("概略", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("大略", "word", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryB, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 8
        query = "概要";
        expected = Arrays.asList(new TokenAttribute("概要", "word", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryB, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 9
        query = "あらすじ";
        expected = Arrays.asList(new TokenAttribute("あらすじ", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryB, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 10
        query = "首相";
        expected = Arrays.asList(new TokenAttribute("首相", "word", 0, 0, 2, 1),
                new TokenAttribute("総理大臣", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryB, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 11
        query = "総理大臣";
        expected = Arrays.asList(new TokenAttribute("首相", "SYNONYM", 0, 0, 4, 1),
                new TokenAttribute("総理大臣", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryB, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);
    }

    @Test
    public void testGetSingleTokenSynonymsByUsingChikkarSynonymTokenFilterInUserB() throws Exception {
        /*
         * 曖昧,不明確,あやふや 粗筋,概略,大略 首相,総理大臣
         *
         * All words without space inside will be treated as single-token synonym as the
         * test case use org.apache.lucene.analysis.core.WhitespaceTokenizer to tokenize
         * query for simplicity.
         */

        // case 1
        String query = "曖昧";
        List<TokenAttribute> expected = Arrays.asList(new TokenAttribute("曖昧", "word", 0, 0, 2, 1),
                new TokenAttribute("不明確", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("あやふや", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        List<TokenAttribute> result = parseSynonyms(chikkarSynonymFactoryB, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 2
        query = "不明確";
        expected = Arrays.asList(new TokenAttribute("曖昧", "SYNONYM", 0, 0, 3, 1),
                new TokenAttribute("不明確", "word", 0, 0, 3, 1), new TokenAttribute("あやふや", "SYNONYM", 0, 0, 3, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryB, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 3
        query = "あやふや";
        expected = Arrays.asList(new TokenAttribute("曖昧", "SYNONYM", 0, 0, 4, 1),
                new TokenAttribute("不明確", "SYNONYM", 0, 0, 4, 1), new TokenAttribute("あやふや", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryB, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 4
        query = "あいまい";
        expected = Arrays.asList(new TokenAttribute("あいまい", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryB, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 5
        query = "粗筋";
        expected = Arrays.asList(new TokenAttribute("粗筋", "word", 0, 0, 2, 1),
                new TokenAttribute("概略", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("大略", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryB, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 6
        query = "概略";
        expected = Arrays.asList(new TokenAttribute("粗筋", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("概略", "word", 0, 0, 2, 1), new TokenAttribute("大略", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryB, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 7
        query = "大略";
        expected = Arrays.asList(new TokenAttribute("粗筋", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("概略", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("大略", "word", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryB, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 8
        query = "概要";
        expected = Arrays.asList(new TokenAttribute("概要", "word", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryB, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 9
        query = "あらすじ";
        expected = Arrays.asList(new TokenAttribute("あらすじ", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryB, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 10
        query = "首相";
        expected = Arrays.asList(new TokenAttribute("首相", "word", 0, 0, 2, 1),
                new TokenAttribute("総理大臣", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryB, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 11
        query = "総理大臣";
        expected = Arrays.asList(new TokenAttribute("首相", "SYNONYM", 0, 0, 4, 1),
                new TokenAttribute("総理大臣", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryB, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);
    }

    @Test
    public void testGetSingleTokenSynonymsByUsingChikkarSynonymGraphTokenFilterInUserC() throws Exception {
        /*
         * 概要,大要,要約,要旨,梗概
         *
         * All words without space inside will be treated as single-token synonym as the
         * test case use org.apache.lucene.analysis.core.WhitespaceTokenizer to tokenize
         * query for simplicity.
         */

        // case 1
        String query = "概要";
        List<TokenAttribute> expected = Arrays.asList(new TokenAttribute("概要", "word", 0, 0, 2, 1),
                new TokenAttribute("大要", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("要約", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("要旨", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("梗概", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        List<TokenAttribute> result = parseSynonyms(chikkarSynonymGraphFactoryC, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 2
        query = "大要";
        expected = Arrays.asList(new TokenAttribute("概要", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("大要", "word", 0, 0, 2, 1), new TokenAttribute("要約", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("要旨", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("梗概", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryC, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 3
        query = "梗概";
        expected = Arrays.asList(new TokenAttribute("概要", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("大要", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("要約", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("要旨", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("梗概", "word", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryC, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 4
        query = "粗筋";
        expected = Arrays.asList(new TokenAttribute("粗筋", "word", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryC, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 5
        query = "概略";
        expected = Arrays.asList(new TokenAttribute("概略", "word", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryC, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 6
        query = "あらすじ";
        expected = Arrays.asList(new TokenAttribute("あらすじ", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryC, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 7
        query = "曖昧";
        expected = Arrays.asList(new TokenAttribute("曖昧", "word", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryC, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 8
        query = "あやふや";
        expected = Arrays.asList(new TokenAttribute("あやふや", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryC, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 9
        query = "首相";
        expected = Arrays.asList(new TokenAttribute("首相", "word", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryC, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 10
        query = "総理大臣";
        expected = Arrays.asList(new TokenAttribute("総理大臣", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryC, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);
    }

    @Test
    public void testGetSingleTokenSynonymsByUsingChikkarSynonymTokenFilterInUserC() throws Exception {
        /*
         * 概要,大要,要約,要旨,梗概
         *
         * All words without space inside will be treated as single-token synonym as the
         * test case use org.apache.lucene.analysis.core.WhitespaceTokenizer to tokenize
         * query for simplicity.
         */

        // case 1
        String query = "概要";
        List<TokenAttribute> expected = Arrays.asList(new TokenAttribute("概要", "word", 0, 0, 2, 1),
                new TokenAttribute("大要", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("要約", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("要旨", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("梗概", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        List<TokenAttribute> result = parseSynonyms(chikkarSynonymFactoryC, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 2
        query = "大要";
        expected = Arrays.asList(new TokenAttribute("概要", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("大要", "word", 0, 0, 2, 1), new TokenAttribute("要約", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("要旨", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("梗概", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryC, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 3
        query = "梗概";
        expected = Arrays.asList(new TokenAttribute("概要", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("大要", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("要約", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("要旨", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("梗概", "word", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryC, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 4
        query = "粗筋";
        expected = Arrays.asList(new TokenAttribute("粗筋", "word", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryC, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 5
        query = "概略";
        expected = Arrays.asList(new TokenAttribute("概略", "word", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryC, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 6
        query = "あらすじ";
        expected = Arrays.asList(new TokenAttribute("あらすじ", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryC, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 7
        query = "曖昧";
        expected = Arrays.asList(new TokenAttribute("曖昧", "word", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryC, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 8
        query = "あやふや";
        expected = Arrays.asList(new TokenAttribute("あやふや", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryC, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 9
        query = "首相";
        expected = Arrays.asList(new TokenAttribute("首相", "word", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryC, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 10
        query = "総理大臣";
        expected = Arrays.asList(new TokenAttribute("総理大臣", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryC, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);
    }

    @Test
    public void testGetMultiTokenSynonymsByUsingChikkarSynonymGraphTokenFilter() throws Exception {
        /*
         * 曖昧,不 明確,あやふや 首相,総理,総理 大臣,内閣 総理 大臣
         *
         * Multi-token synonyms is divided by space, i.e. "不 明確", "総理 大臣", "内閣 総理 大臣",
         * as the test case use org.apache.lucene.analysis.core.WhitespaceTokenizer to
         * tokenize query for simplicity.
         */

        // case 1
        String query = "曖昧";
        List<TokenAttribute> expected = Arrays.asList(new TokenAttribute("曖昧", "word", 0, 0, 2, 2),
                new TokenAttribute("不", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("明確", "SYNONYM", 1, 0, 2, 1),
                new TokenAttribute("あやふや", "SYNONYM", 0, 0, 2, 2));
        Collections.sort(expected);
        List<TokenAttribute> result = parseSynonyms(chikkarSynonymGraphFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 2
        query = "不 明確";
        expected = Arrays.asList(new TokenAttribute("曖昧", "SYNONYM", 0, 0, 4, 2),
                new TokenAttribute("不", "word", 0, 0, 1, 1), new TokenAttribute("明確", "word", 1, 2, 4, 1),
                new TokenAttribute("あやふや", "SYNONYM", 0, 0, 4, 2));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 3
        query = "不明確";
        expected = Arrays.asList(new TokenAttribute("不明確", "word", 0, 0, 3, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 4
        query = "あやふや";
        expected = Arrays.asList(new TokenAttribute("曖昧", "SYNONYM", 0, 0, 4, 2),
                new TokenAttribute("不", "SYNONYM", 0, 0, 4, 1), new TokenAttribute("明確", "SYNONYM", 1, 0, 4, 1),
                new TokenAttribute("あやふや", "word", 0, 0, 4, 2));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 5
        query = "首相";
        expected = Arrays.asList(new TokenAttribute("首相", "word", 0, 0, 2, 4),
                new TokenAttribute("総理", "SYNONYM", 0, 0, 2, 4), new TokenAttribute("総理", "SYNONYM", 0, 0, 2, 3),
                new TokenAttribute("大臣", "SYNONYM", 3, 0, 2, 1), new TokenAttribute("内閣", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("総理", "SYNONYM", 1, 0, 2, 1), new TokenAttribute("大臣", "SYNONYM", 2, 0, 2, 2));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 6
        query = "総理";
        expected = Arrays.asList(new TokenAttribute("首相", "SYNONYM", 0, 0, 2, 4),
                new TokenAttribute("総理", "word", 0, 0, 2, 4), new TokenAttribute("総理", "SYNONYM", 0, 0, 2, 3),
                new TokenAttribute("大臣", "SYNONYM", 3, 0, 2, 1), new TokenAttribute("内閣", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("総理", "SYNONYM", 1, 0, 2, 1), new TokenAttribute("大臣", "SYNONYM", 2, 0, 2, 2));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 7
        query = "総理 大臣";
        expected = Arrays.asList(new TokenAttribute("首相", "SYNONYM", 0, 0, 5, 4),
                new TokenAttribute("総理", "SYNONYM", 0, 0, 5, 4), new TokenAttribute("総理", "word", 0, 0, 2, 3),
                new TokenAttribute("大臣", "word", 3, 3, 5, 1), new TokenAttribute("内閣", "SYNONYM", 0, 0, 5, 1),
                new TokenAttribute("総理", "SYNONYM", 1, 0, 5, 1), new TokenAttribute("大臣", "SYNONYM", 2, 0, 5, 2));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 8
        query = "総理大臣";
        expected = Arrays.asList(new TokenAttribute("総理大臣", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 9
        query = "内閣 総理 大臣";
        expected = Arrays.asList(new TokenAttribute("首相", "SYNONYM", 0, 0, 8, 4),
                new TokenAttribute("総理", "SYNONYM", 0, 0, 8, 4), new TokenAttribute("総理", "SYNONYM", 0, 0, 8, 1),
                new TokenAttribute("大臣", "SYNONYM", 1, 0, 8, 3), new TokenAttribute("内閣", "word", 0, 0, 2, 2),
                new TokenAttribute("総理", "word", 2, 3, 5, 1), new TokenAttribute("大臣", "word", 3, 6, 8, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 10
        query = "内閣総理大臣";
        expected = Arrays.asList(new TokenAttribute("内閣総理大臣", "word", 0, 0, 6, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymGraphFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);
    }

    @Test
    public void testGetMultiTokenSynonymsByUsingModSynonymTokenFilter() throws Exception {
        /*
         * 曖昧,不 明確,あやふや 首相,総理,総理 大臣,内閣 総理 大臣
         *
         * Multi-token synonyms is divided by space, i.e. "不 明確", "総理 大臣", "内閣 総理 大臣",
         * as the test case use org.apache.lucene.analysis.core.WhitespaceTokenizer to
         * tokenize query for simplicity.
         */

        // case 1
        String query = "曖昧";
        List<TokenAttribute> expected = Arrays.asList(new TokenAttribute("曖昧", "word", 0, 0, 2, 1),
                new TokenAttribute("不", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("明確", "SYNONYM", 1, 0, 2, 1),
                new TokenAttribute("あやふや", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        List<TokenAttribute> result = parseSynonyms(modSynonymFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 2
        query = "不 明確";
        expected = Arrays.asList(new TokenAttribute("不", "word", 0, 0, 1, 1),
                new TokenAttribute("明確", "word", 1, 2, 4, 1), new TokenAttribute("曖昧", "SYNONYM", 0, 0, 4, 2),
                new TokenAttribute("あやふや", "SYNONYM", 0, 0, 4, 2));
        Collections.sort(expected);
        result = parseSynonyms(modSynonymFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 3
        query = "不明確";
        expected = Arrays.asList(new TokenAttribute("不明確", "word", 0, 0, 3, 1));
        Collections.sort(expected);
        result = parseSynonyms(modSynonymFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 4
        query = "あやふや";
        expected = Arrays.asList(new TokenAttribute("曖昧", "SYNONYM", 0, 0, 4, 1),
                new TokenAttribute("不", "SYNONYM", 0, 0, 4, 1), new TokenAttribute("明確", "SYNONYM", 1, 0, 4, 1),
                new TokenAttribute("あやふや", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(modSynonymFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 5
        query = "首相";
        expected = Arrays.asList(new TokenAttribute("首相", "word", 0, 0, 2, 1),
                new TokenAttribute("総理", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("総理", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("大臣", "SYNONYM", 1, 0, 2, 1), new TokenAttribute("内閣", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("総理", "SYNONYM", 1, 0, 2, 1), new TokenAttribute("大臣", "SYNONYM", 2, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(modSynonymFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 6
        query = "総理";
        expected = Arrays.asList(new TokenAttribute("首相", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("総理", "word", 0, 0, 2, 1), new TokenAttribute("総理", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("大臣", "SYNONYM", 1, 0, 2, 1), new TokenAttribute("内閣", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("総理", "SYNONYM", 1, 0, 2, 1), new TokenAttribute("大臣", "SYNONYM", 2, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(modSynonymFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 7
        query = "総理 大臣";
        expected = Arrays.asList(new TokenAttribute("首相", "SYNONYM", 0, 0, 5, 2),
                new TokenAttribute("総理", "SYNONYM", 0, 0, 5, 2), new TokenAttribute("総理", "word", 0, 0, 2, 1),
                new TokenAttribute("大臣", "word", 1, 3, 5, 1), new TokenAttribute("内閣", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("総理", "SYNONYM", 1, 3, 5, 1), new TokenAttribute("大臣", "SYNONYM", 2, 3, 5, 1));
        Collections.sort(expected);
        result = parseSynonyms(modSynonymFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 8
        query = "総理大臣";
        expected = Arrays.asList(new TokenAttribute("総理大臣", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(modSynonymFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 9
        query = "内閣 総理 大臣";
        expected = Arrays.asList(new TokenAttribute("首相", "SYNONYM", 0, 0, 8, 3),
                new TokenAttribute("総理", "SYNONYM", 0, 0, 8, 3), new TokenAttribute("総理", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("大臣", "SYNONYM", 1, 3, 5, 1), new TokenAttribute("内閣", "word", 0, 0, 2, 1),
                new TokenAttribute("総理", "word", 1, 3, 5, 1), new TokenAttribute("大臣", "word", 2, 6, 8, 1));
        Collections.sort(expected);
        result = parseSynonyms(modSynonymFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 10
        query = "内閣総理大臣";
        expected = Arrays.asList(new TokenAttribute("内閣総理大臣", "word", 0, 0, 6, 1));
        Collections.sort(expected);
        result = parseSynonyms(modSynonymFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);
    }

    @Test
    public void testModSynonymTokenFilterForReference() throws Exception {
        String query = "添える";
        List<TokenAttribute> result = parseSynonyms(modSynonymFactoryRef, query);
        Collections.sort(result);
        for (TokenAttribute t : result)
            System.out.println(t);
    }

    @Test
    public void testGetMultiTokenSynonymsByUsingChikkarSynonymTokenFilter() throws Exception {
        /*
         * 曖昧,不 明確,あやふや 首相,総理,総理 大臣,内閣 総理 大臣
         *
         * Multi-token synonyms is divided by space, i.e. "不 明確", "総理 大臣", "内閣 総理 大臣",
         * as the test case use org.apache.lucene.analysis.core.WhitespaceTokenizer to
         * tokenize query for simplicity.
         */

        // case 1
        String query = "曖昧";
        List<TokenAttribute> expected = Arrays.asList(new TokenAttribute("曖昧", "word", 0, 0, 2, 1),
                new TokenAttribute("不", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("明確", "SYNONYM", 1, 0, 2, 1),
                new TokenAttribute("あやふや", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        List<TokenAttribute> result = parseSynonyms(chikkarSynonymFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 2
        query = "不 明確";
        expected = Arrays.asList(new TokenAttribute("不", "word", 0, 0, 1, 1),
                new TokenAttribute("明確", "word", 1, 2, 4, 1), new TokenAttribute("曖昧", "SYNONYM", 0, 0, 4, 2),
                new TokenAttribute("あやふや", "SYNONYM", 0, 0, 4, 2));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 3
        query = "不明確";
        expected = Arrays.asList(new TokenAttribute("不明確", "word", 0, 0, 3, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 4
        query = "あやふや";
        expected = Arrays.asList(new TokenAttribute("曖昧", "SYNONYM", 0, 0, 4, 1),
                new TokenAttribute("不", "SYNONYM", 0, 0, 4, 1), new TokenAttribute("明確", "SYNONYM", 1, 0, 4, 1),
                new TokenAttribute("あやふや", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 5
        query = "首相";
        expected = Arrays.asList(new TokenAttribute("首相", "word", 0, 0, 2, 1),
                new TokenAttribute("総理", "SYNONYM", 0, 0, 2, 1), new TokenAttribute("総理", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("大臣", "SYNONYM", 1, 0, 2, 1), new TokenAttribute("内閣", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("総理", "SYNONYM", 1, 0, 2, 1), new TokenAttribute("大臣", "SYNONYM", 2, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 6
        query = "総理";
        expected = Arrays.asList(new TokenAttribute("首相", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("総理", "word", 0, 0, 2, 1), new TokenAttribute("総理", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("大臣", "SYNONYM", 1, 0, 2, 1), new TokenAttribute("内閣", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("総理", "SYNONYM", 1, 0, 2, 1), new TokenAttribute("大臣", "SYNONYM", 2, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 7
        query = "総理 大臣";
        expected = Arrays.asList(new TokenAttribute("首相", "SYNONYM", 0, 0, 5, 2),
                new TokenAttribute("総理", "SYNONYM", 0, 0, 5, 2), new TokenAttribute("総理", "word", 0, 0, 2, 1),
                new TokenAttribute("大臣", "word", 1, 3, 5, 1), new TokenAttribute("内閣", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("総理", "SYNONYM", 1, 3, 5, 1), new TokenAttribute("大臣", "SYNONYM", 2, 3, 5, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 8
        query = "総理大臣";
        expected = Arrays.asList(new TokenAttribute("総理大臣", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 9
        query = "内閣 総理 大臣";
        expected = Arrays.asList(new TokenAttribute("首相", "SYNONYM", 0, 0, 8, 3),
                new TokenAttribute("総理", "SYNONYM", 0, 0, 8, 3), new TokenAttribute("総理", "SYNONYM", 0, 0, 2, 1),
                new TokenAttribute("大臣", "SYNONYM", 1, 3, 5, 1), new TokenAttribute("内閣", "word", 0, 0, 2, 1),
                new TokenAttribute("総理", "word", 1, 3, 5, 1), new TokenAttribute("大臣", "word", 2, 6, 8, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 10
        query = "内閣総理大臣";
        expected = Arrays.asList(new TokenAttribute("内閣総理大臣", "word", 0, 0, 6, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarSynonymFactoryD, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);
    }

    @Test
    public void testGetDirectedSynonymsByUsingChikkarSynonymTokenFilter() throws Exception {
        /*
         * 添付 => 添付, 添える 生命保険 => 生命保険, 生保
         */

        // case 1
        String query = "添付";
        List<TokenAttribute> expected = Arrays.asList(new TokenAttribute("添付", "word", 0, 0, 2, 1),
                new TokenAttribute("添える", "SYNONYM", 0, 0, 2, 1));
        Collections.sort(expected);
        List<TokenAttribute> result = parseSynonyms(chikkarDirectedSynonymFactory, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 2
        query = "生命保険";
        expected = Arrays.asList(new TokenAttribute("生保", "SYNONYM", 0, 0, 4, 1),
                new TokenAttribute("生命保険", "word", 0, 0, 4, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarDirectedSynonymFactory, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 3
        query = "添える";
        expected = Arrays.asList(new TokenAttribute("添える", "word", 0, 0, 3, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarDirectedSynonymFactory, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);

        // case 4
        query = "生保";
        expected = Arrays.asList(new TokenAttribute("生保", "word", 0, 0, 2, 1));
        Collections.sort(expected);
        result = parseSynonyms(chikkarDirectedSynonymFactory, query);
        Collections.sort(result);
        assertSynonymsEquals(expected, result);
    }

    class WhitespaceTokenizerFactory implements TokenizerFactory {
        @Override
        public String name() {
            return "WhitespaceTokenizer";
        }

        @Override
        public Tokenizer create() {
            return new WhitespaceTokenizer();
        }
    }

    class TokenAttribute implements Comparable<TokenAttribute> {
        public String term;
        public String type;
        public int position;
        public int startOffset;
        public int endOffset;
        public int posLength;

        public TokenAttribute(String term, String type, int position, int startOffset, int endOffset, int posLength) {
            this.term = term;
            this.type = type;
            this.position = position;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.posLength = posLength;
        }

        public String toString() {
            return String.format(
                    "Term: %s, Type: %s, Position: %d, StartOffset: %d, EndOffset: %d, " + "PositionLength: %d", term,
                    type, position, startOffset, endOffset, posLength);
        }

        @Override
        public int compareTo(TokenAttribute obj) {
            return this.toString().compareTo(obj.toString());
        }
    }

    TokenFilterFactory createChikkarSynonymGraphFactory(Path configPath, String dictPath, String dictId,
            TokenizerFactory tokenizer, List<CharFilterFactory> charFilters, List<TokenFilterFactory> tokenFilters)
            throws IOException {
        Index index = mock(Index.class);
        when(index.getName()).thenReturn("test");

        IndexSettings indexSettings = mock(IndexSettings.class);
        when(indexSettings.getIndex()).thenReturn(index);

        Environment env = mock(Environment.class);
        when(env.configFile()).thenReturn(configPath);

        Settings settings = Settings.builder().put("version", "8.0.0").put("system_dict_id", dictId)
                .put("system_dict", dictPath).build();

        when(indexSettings.getSettings()).thenReturn(settings);

        AnalysisChikkarPlugin plugin = new AnalysisChikkarPlugin();
        TokenFilterFactory factory = plugin.getTokenFilters().get(AnalysisChikkarPlugin.SYNONYM_GRAPH_FILTER_NAME)
                .get(indexSettings, env, "plugins", settings);

        assertTrue(factory instanceof ChikkarSynonymGraphTokenFilterFactory);

        return factory.getChainAwareTokenFilterFactory(tokenizer, charFilters, tokenFilters, null);
    }

    TokenFilterFactory createChikkarSynonymFactory(Path configPath, String dictPath, String dictId,
            TokenizerFactory tokenizer, List<CharFilterFactory> charFilters, List<TokenFilterFactory> tokenFilters)
            throws IOException {
        Index index = mock(Index.class);
        when(index.getName()).thenReturn("test");

        IndexSettings indexSettings = mock(IndexSettings.class);
        when(indexSettings.getIndex()).thenReturn(index);

        Environment env = mock(Environment.class);
        when(env.configFile()).thenReturn(configPath);

        Settings settings = Settings.builder().put("version", "8.0.0").put("system_dict_id", dictId)
                .put("system_dict", dictPath).build();

        when(indexSettings.getSettings()).thenReturn(settings);

        AnalysisChikkarPlugin plugin = new AnalysisChikkarPlugin();
        TokenFilterFactory factory = plugin.getTokenFilters().get(AnalysisChikkarPlugin.SYNONYM_FILTER_NAME)
                .get(indexSettings, env, "plugins", settings);

        assertTrue(factory instanceof ChikkarSynonymTokenFilterFactory);

        return factory.getChainAwareTokenFilterFactory(tokenizer, charFilters, tokenFilters, null);
    }

    TokenFilterFactory createSynonymFactory(Path configPath, String dictPath, TokenizerFactory tokenizer,
            List<CharFilterFactory> charFilters, List<TokenFilterFactory> tokenFilters) {
        Index index = mock(Index.class);
        when(index.getName()).thenReturn("test");

        IndexSettings indexSettings = mock(IndexSettings.class);
        when(indexSettings.getIndex()).thenReturn(index);

        Environment env = mock(Environment.class);
        when(env.configFile()).thenReturn(configPath);

        Settings settings = Settings.builder().put("version", "8.0.0").put("synonyms_path", dictPath).build();

        when(indexSettings.getSettings()).thenReturn(settings);

        TokenFilterFactory factory = new ModSynonymTokenFilterFactory(indexSettings, env, "mod_synonym", settings);

        return factory.getChainAwareTokenFilterFactory(tokenizer, charFilters, tokenFilters, null);
    }

    void assertSynonymsEquals(List<TokenAttribute> expected, List<TokenAttribute> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < actual.size(); i++) {
            TokenAttribute exp = expected.get(i);
            TokenAttribute act = actual.get(i);
            assertEquals(exp.term, act.term);
            assertEquals(exp.type, act.type);
            assertEquals(exp.position, act.position);
            assertEquals(exp.startOffset, act.startOffset);
            assertEquals(exp.endOffset, act.endOffset);
            assertEquals(exp.posLength, act.posLength);
        }
    }

    List<TokenAttribute> parseSynonyms(TokenFilterFactory tokenFilter, String text) throws IOException {
        TokenStream stream = tokenFilter.create(analyzer.tokenStream("", text));

        CharTermAttribute term = stream.getAttribute(CharTermAttribute.class);
        PositionIncrementAttribute posIncr = stream.getAttribute(PositionIncrementAttribute.class);
        OffsetAttribute offset = stream.getAttribute(OffsetAttribute.class);
        TypeAttribute type = stream.getAttribute(TypeAttribute.class);
        PositionLengthAttribute posLen = stream.getAttribute(PositionLengthAttribute.class);

        int lastPosition = -1;
        List<TokenAttribute> ta = new ArrayList<>();

        stream.reset();
        while (stream.incrementToken()) {
            lastPosition += posIncr.getPositionIncrement();
            ta.add(new TokenAttribute(term.toString(), type.type(), lastPosition, offset.startOffset(),
                    offset.endOffset(), posLen.getPositionLength()));
        }
        stream.end();
        stream.close();

        return ta;
    }

}
