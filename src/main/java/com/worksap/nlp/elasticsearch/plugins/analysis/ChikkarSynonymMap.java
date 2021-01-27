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

package com.worksap.nlp.elasticsearch.plugins.analysis;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.worksap.nlp.elasticsearch.plugins.chikkar.Chikkar;
import org.apache.lucene.store.ByteArrayDataOutput;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;
import org.apache.lucene.util.IntsRefBuilder;
import org.apache.lucene.util.fst.ByteSequenceOutputs;
import org.apache.lucene.util.fst.FST;
import org.apache.lucene.util.fst.Util;

public class ChikkarSynonymMap {
    // for multiword support, you must separate words with this separator
    public static final char WORD_SEPARATOR = '\0';
    public final Chikkar chikkar;
    public final FST<BytesRef> fst;
    /** maxHorizontalContext: maximum context we need on the tokenstream */
    public final int maxHorizontalContext;

    public ChikkarSynonymMap(Chikkar chikkar, FST<BytesRef> fst, int maxHorizontalContext) {
        this.chikkar = chikkar;
        this.fst = fst;
        this.maxHorizontalContext = maxHorizontalContext;
    }

    public static class Builder {
        private final boolean dedup;

        /**
         * Default constructor, passes {@code dedup=true}.
         */
        public Builder() {
            this(true);
        }

        /**
         * Constructor with argument
         *
         * @param dedup
         *            If dedup is true then identical rules (same input, same output)
         *            will be added only once.
         */
        public Builder(boolean dedup) {
            this.dedup = dedup;
        }

        /**
         * Builds a {@link ChikkarSynonymMap} instance and returns it.
         *
         * @param chikkar
         *            A {@link Chikkar} instance used for building FST
         * @return A {@link ChikkarSynonymMap} instance
         * @throws IOException
         *             throws IOException if fail to build the ChikkarSynonymMap
         */
        public ChikkarSynonymMap build(Chikkar chikkar) throws IOException {
            ByteSequenceOutputs outputs = ByteSequenceOutputs.getSingleton();
            // TODO: are we using the best sharing options?
            org.apache.lucene.util.fst.Builder<BytesRef> builder = new org.apache.lucene.util.fst.Builder<>(
                    FST.INPUT_TYPE.BYTE4, outputs);

            BytesRefBuilder scratch = new BytesRefBuilder();
            ByteArrayDataOutput scratchOutput = new ByteArrayDataOutput();

            final Set<Integer> dedupSet;

            if (dedup) {
                dedupSet = new HashSet<>();
            } else {
                dedupSet = null;
            }

            final byte[] spare = new byte[5];
            List<String> keys = chikkar.getSortedKeys();

            final IntsRefBuilder scratchIntsRef = new IntsRefBuilder();
            final String spliter = String.valueOf(WORD_SEPARATOR);
            int maxHorizontalContext = 0;

            for (String input : keys) {
                maxHorizontalContext = Math.max(maxHorizontalContext, input.split(spliter).length);

                List<Integer> ords = chikkar.getSynonymId(input);
                if (ords.isEmpty()) {
                    continue;
                }

                int numEntries = ords.size();
                // output size, assume the worst case
                int estimatedSize = 5 + numEntries * 5; // numEntries + one ord for each entry

                scratch.grow(estimatedSize);
                scratchOutput.reset(scratch.bytes());

                // now write our output data:
                int count = 0;
                for (int i = 0; i < numEntries; i++) {
                    if (dedupSet != null) {
                        // box once
                        final Integer ent = ords.get(i);
                        if (dedupSet.contains(ent)) {
                            continue;
                        }
                        dedupSet.add(ent);
                    }
                    scratchOutput.writeVInt(ords.get(i));
                    count++;
                }

                final int pos = scratchOutput.getPosition();
                scratchOutput.writeVInt(count << 1);
                final int pos2 = scratchOutput.getPosition();
                final int vIntLen = pos2 - pos;

                // Move the count + includeOrig to the front of the byte[]:
                System.arraycopy(scratch.bytes(), pos, spare, 0, vIntLen);
                System.arraycopy(scratch.bytes(), 0, scratch.bytes(), vIntLen, pos);
                System.arraycopy(spare, 0, scratch.bytes(), 0, vIntLen);

                if (dedupSet != null) {
                    dedupSet.clear();
                }

                scratch.setLength(scratchOutput.getPosition());
                builder.add(Util.toUTF32(input, scratchIntsRef), scratch.toBytesRef());
            }

            FST<BytesRef> fst = builder.finish();
            return new ChikkarSynonymMap(chikkar, fst, maxHorizontalContext);
        }
    }

}
