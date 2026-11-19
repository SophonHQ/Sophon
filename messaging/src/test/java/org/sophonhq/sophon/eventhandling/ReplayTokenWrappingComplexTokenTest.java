/*
 * Copyright (c) 2010-2025. Axon Framework
 * Modifications Copyright (c) 2026. SophonHQ
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sophonhq.sophon.eventhandling;

import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class for validating the behavior of {@link ReplayToken} when wrapping complex token types.
 * <p>
 * This test validates replay detection across various nested token scenarios:
 * <ul>
 *   <li>{@link GapAwareTrackingToken} - with gaps, filled gaps, and various positions relative to reset</li>
 *   <li>{@link MultiSourceTrackingToken} - multi-source scenarios where each source has independent gap positions</li>
 *   <li>{@link MergedTrackingToken} - merged segments with gaps in lower/upper segments and nested structures</li>
 * </ul>
 *
 * @author Mateusz Nowak
 * @since axon-4.12.3
 */
class ReplayTokenWrappingComplexTokenTest {

    @Nested
    class GapAwareTrackingTokenAdvancedTo {

        @Test
        void whenBeforeResetPositionWithoutGapsThenReplay() {
            // given
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, emptySet());
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // when
            TrackingToken newToken = GapAwareTrackingToken.newInstance(5, emptySet());
            TrackingToken result = replayToken.advancedTo(newToken);

            // then
            assertThat(result).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(result)).as("Event before reset position should be a replay").isTrue();
        }

        @Test
        void whenAtResetPositionWithoutGapsThenReplay() {
            // given
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, emptySet());
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // when
            replayToken = (ReplayToken) replayToken.advancedTo(GapAwareTrackingToken.newInstance(9, emptySet()));
            TrackingToken newToken = GapAwareTrackingToken.newInstance(10, emptySet());
            TrackingToken result = replayToken.advancedTo(newToken);

            // then
            assertThat(result).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(result)).as("Event at exact reset position should be a replay").isTrue();
        }

        @Test
        void whenAfterResetPositionWithoutGapsThenNotReplay() {
            // given
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, emptySet());
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // when
            TrackingToken newToken = GapAwareTrackingToken.newInstance(11, emptySet());
            TrackingToken result = replayToken.advancedTo(newToken);

            // then
            assertThat(ReplayToken.isReplay(result)).as("Event after reset position should not be a replay").isFalse();
        }

        @Test
        void whenAtGapThenNotReplay() {
            // given
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // when
            TrackingToken newToken = GapAwareTrackingToken.newInstance(7, emptySet());
            TrackingToken result = replayToken.advancedTo(newToken);

            // then
            assertThat(result).as("Should still be in replay mode").isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(result)).as("Event at gap position should NOT be replay").isFalse();
        }

        @Test
        void whenAtGapAndWithAnotherGapThenNotReplay() {
            // given
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));
            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);

            // when
            TrackingToken newToken = GapAwareTrackingToken.newInstance(8, setOf(7L));
            TrackingToken result = ((ReplayToken) currentToken).advancedTo(newToken);

            // then
            assertThat(ReplayToken.isReplay(result)).isFalse();
        }

        @Test
        void whenAfterGapBeforeResetAndNotFilledGapsThenReplay() {
            // given
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // when
            TrackingToken newToken = GapAwareTrackingToken.newInstance(9, setOf(7L, 8L));
            TrackingToken result = replayToken.advancedTo(newToken);

            // then
            assertThat(result).as("Should still be in replay mode").isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(result)).as("Event after gap but before reset should be replay").isTrue();
        }

        @Test
        void whenAfterGapBeforeResetAndFilledGapsThenReplay() {
            // given
            // tokenAtReset at index 10 with gaps at 7,8
            // Before reset, processor saw: 0,1,2,3,4,5,6,9,10 (NOT 7,8)
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));
            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);

            // when
            // Now event 9 arrives - currentToken has NO gaps (they were filled)
            TrackingToken newToken = GapAwareTrackingToken.newInstance(9, emptySet());
            TrackingToken result = ((ReplayToken) currentToken).advancedTo(newToken);

            // then
            assertThat(result).as("Should still be in replay mode").isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(result))
                    .as("Event 9 was processed before reset, should be marked as replay even though gaps were filled")
                    .isTrue();
        }

        @Test
        void givenStartPositionWhenAfterGapBeforeResetAndFilledGapsThenReplay() {
            // given
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));
            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset,
                                                                       new GapAwareTrackingToken(0L, emptyList()));

            // when
            TrackingToken newToken = GapAwareTrackingToken.newInstance(9, emptySet());
            TrackingToken result = ((ReplayToken) currentToken).advancedTo(newToken);

            // then
            assertThat(result).as("Should still be in replay mode").isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(result))
                    .as("Event 9 was processed before reset, should be marked as replay even though gaps were filled")
                    .isTrue();
        }

        @Test
        void whenAtResetPositionAndFilledGapsThenReplay() {
            // given
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));

            // when
            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);
            for (int i = 0; i <= 9; i++) {
                currentToken = ((ReplayToken) currentToken).advancedTo(
                        GapAwareTrackingToken.newInstance(i, emptySet())
                );
            }
            TrackingToken newToken = GapAwareTrackingToken.newInstance(10, emptySet());
            TrackingToken result = ((ReplayToken) currentToken).advancedTo(newToken);

            // then
            assertThat(ReplayToken.isReplay(result)).isTrue();
            assertThat(result)
                    .as("Should still be in replay mode at reset index - event 10 was processed before reset")
                    .isInstanceOf(ReplayToken.class);
        }

        @Test
        void whenAfterResetPositionAndFilledGapsThenNotReplay() {
            // given
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));

            // when
            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);
            for (int i = 0; i <= 10; i++) {
                currentToken = ((ReplayToken) currentToken).advancedTo(
                        GapAwareTrackingToken.newInstance(i, emptySet())
                );
            }
            TrackingToken newToken = GapAwareTrackingToken.newInstance(11, emptySet());
            TrackingToken result = ((ReplayToken) currentToken).advancedTo(newToken);

            // then
            assertThat(ReplayToken.isReplay(result))
                    .as("Event 11 was never processed before reset, should not be a replay")
                    .isFalse();
        }

        @Test
        void whenAfterResetPositionAndNotFilledGapsThenNotReplay() {
            // given
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));
            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);

            // when
            TrackingToken newToken = GapAwareTrackingToken.newInstance(11, setOf(7L, 8L));
            TrackingToken result = ((ReplayToken) currentToken).advancedTo(newToken);

            // then
            assertThat(ReplayToken.isReplay(result))
                    .as("Event 11 was never processed before reset, should not be a replay")
                    .isFalse();
        }

        @Test
        void whenBeforeResetPositionAndNotFilledGapsThenReplay() {
            // given
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));

            // when
            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);
            for (int i = 0; i <= 7; i++) {
                currentToken = ((ReplayToken) currentToken).advancedTo(
                        GapAwareTrackingToken.newInstance(i, emptySet())
                );
            }
            TrackingToken newToken = GapAwareTrackingToken.newInstance(9, setOf(8L));
            TrackingToken result = ((ReplayToken) currentToken).advancedTo(newToken);

            // then
            assertThat(result).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(result))
                    .as("Event 9 was processed before reset, should be replay even with partial gap fill")
                    .isTrue();
        }

        @Test
        void whenAtGapJustBeforeResetPositionThenNotReplay() {
            // given
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(9L));
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // when - gap 9 gets filled during replay
            TrackingToken token9 = GapAwareTrackingToken.newInstance(9, emptySet());
            TrackingToken resultAt9 = replayToken.advancedTo(token9);

            // then
            assertThat(resultAt9).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(resultAt9))
                    .as("Event 9 was a gap before reset, should NOT be marked as replay")
                    .isFalse();

            // when
            TrackingToken token10 = GapAwareTrackingToken.newInstance(10, emptySet());
            TrackingToken resultAt10 = ((ReplayToken) resultAt9).advancedTo(token10);

            // then
            assertThat(resultAt10).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(resultAt10))
                    .as("Event 10 was processed before reset, should be marked as replay")
                    .isTrue();
        }

        @Test
        void multipleDisjointGapsInTokenAtReset() {
            // given
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(15, setOf(3L, 4L, 9L, 10L));
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // when-then
            TrackingToken result2 = replayToken.advancedTo(GapAwareTrackingToken.newInstance(2, emptySet()));
            assertThat(ReplayToken.isReplay(result2)).as("Event 2 was processed, should be replay").isTrue();

            // when-then
            TrackingToken result3 = ((ReplayToken) result2).advancedTo(GapAwareTrackingToken.newInstance(3,
                                                                                                         emptySet()));
            assertThat(ReplayToken.isReplay(result3)).as("Event 3 was a gap, should NOT be replay").isFalse();

            // when-then
            TrackingToken result5 = ((ReplayToken) result3).advancedTo(
                    GapAwareTrackingToken.newInstance(5, setOf(4L)));
            assertThat(ReplayToken.isReplay(result5)).as("Event 5 was processed, should be replay").isTrue();
        }

        @Test
        void whenAtResetAndGapsNotFilledDuringReplayThenReplay() {
            // given
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(5, setOf(3L));
            GapAwareTrackingToken currentTokenWithGap = GapAwareTrackingToken.newInstance(4, setOf(3L));
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset,
                                                                                  currentTokenWithGap,
                                                                                  null);

            // when
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(5, setOf(3L));
            TrackingToken result = replayToken.advancedTo(newToken);

            // then
            assertThat(result).as("Should still be in replay mode").isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(result)).as("Event 5 was processed before reset").isTrue();
        }

        @Test
        void whenGapAtStartPositionThenNotReplay() {
            // given
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(5, setOf(0L));
            GapAwareTrackingToken currentToken = GapAwareTrackingToken.newInstance(0, emptySet());

            // when
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, currentToken);

            // then
            assertThat(replayToken).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(replayToken))
                    .as("Event 0 was a gap, should NOT be marked as replay")
                    .isFalse();
        }

        @Test
        void whenBetweenGapsThenReplay() {
            // given
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(3L, 7L));
            GapAwareTrackingToken currentToken = GapAwareTrackingToken.newInstance(4, emptySet());
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, currentToken, null);

            // when
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(5, emptySet());
            TrackingToken result = replayToken.advancedTo(newToken);

            // then
            assertThat(result).as("Should still be in replay mode").isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(result))
                    .as("Event 5 was processed before reset, should be marked as replay")
                    .isTrue();
        }
    }

    @Nested
    class MultiSourceTrackingTokenAdvancedTo {

        /**
         * Test with MultiSourceTrackingToken scenario.
         * <p>
         * During reset:
         * <ul>
         *     <li>A: Index 6, Gaps [1]</li>
         *     <li>B: Index 4, Gaps [1]</li>
         * </ul>
         * <p>
         * During replay the new token:
         * <ul>
         *     <li>A: Index 2, Gaps []</li>
         *     <li>B: Index 1, Gaps []</li>
         * </ul>
         * <p>
         * Source B's newToken is at index 1, which IS in the gaps of tokenAtReset for B. This means event 1 for source
         * B was NOT processed before reset - it's a new event. Therefore, this should NOT be marked as a replay.
         */
        @Test
        void whenOneSourceAtGapThenNotReplay() {
            // given
            // MultiSourceTrackingToken at reset with gaps
            Map<String, TrackingToken> tokensAtReset = new HashMap<>();
            tokensAtReset.put("A", GapAwareTrackingToken.newInstance(6, Collections.singleton(1L)));
            tokensAtReset.put("B", GapAwareTrackingToken.newInstance(4, Collections.singleton(1L)));
            MultiSourceTrackingToken multiTokenAtReset = new MultiSourceTrackingToken(tokensAtReset);

            // and
            // Create replay token starting from the beginning
            TrackingToken replayToken = ReplayToken.createReplayToken(multiTokenAtReset, null);
            assertThat(replayToken).isInstanceOf(ReplayToken.class);

            // when
            // advancing during replay with a token where B is at a gap position
            Map<String, TrackingToken> newTokens = new HashMap<>();
            newTokens.put(
                    "A",
                    GapAwareTrackingToken.newInstance(2, emptySet())
            ); // Index 2 was processed before (not in gaps)
            newTokens.put(
                    "B",
                    GapAwareTrackingToken.newInstance(1, emptySet())
            ); // Index 1 was NOT processed before (in gaps!)
            MultiSourceTrackingToken newMultiToken = new MultiSourceTrackingToken(newTokens);

            TrackingToken advancedToken = ((ReplayToken) replayToken).advancedTo(newMultiToken);

            // then
            // Should still be in replay MODE (not exited yet), but this specific event is NOT a replay
            // because source B's event at index 1 was never processed before reset
            assertThat(advancedToken)
                    .as("Should still be a ReplayToken since we haven't caught up to reset position")
                    .isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(advancedToken))
                    .as("Should NOT be marked as replay - source B's event at index 1 was a gap (never processed before reset)")
                    .isFalse();
        }

        @Test
        void whenBothSourcesNotAtGapThenReplay() {
            // given
            Map<String, TrackingToken> tokensAtReset = new HashMap<>();
            tokensAtReset.put("A", GapAwareTrackingToken.newInstance(6, Collections.singleton(1L)));
            tokensAtReset.put("B", GapAwareTrackingToken.newInstance(4, Collections.singleton(1L)));
            MultiSourceTrackingToken multiTokenAtReset = new MultiSourceTrackingToken(tokensAtReset);

            // and
            TrackingToken replayToken = ReplayToken.createReplayToken(multiTokenAtReset, null);
            assertThat(replayToken).isInstanceOf(ReplayToken.class);

            // when
            Map<String, TrackingToken> newTokens = new HashMap<>();
            newTokens.put(
                    "A",
                    GapAwareTrackingToken.newInstance(2, emptySet())
            ); // Index 2 was processed before (not in gaps)
            newTokens.put(
                    "B",
                    GapAwareTrackingToken.newInstance(2, emptySet())
            ); // Index 2 was processed before (not in gaps)
            MultiSourceTrackingToken newMultiToken = new MultiSourceTrackingToken(newTokens);

            TrackingToken advancedToken = ((ReplayToken) replayToken).advancedTo(newMultiToken);

            // then
            assertThat(advancedToken)
                    .as("Should still be a ReplayToken since we haven't caught up to reset position")
                    .isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(advancedToken))
                    .as("Should be marked as replay - both source positions were processed before reset")
                    .isTrue();
        }

        @Test
        void whenOneSourceNotAGapButBetweenGapsThenReplay() {
            // given
            Map<String, TrackingToken> tokensAtReset = new HashMap<>();
            tokensAtReset.put("localEventStore",
                              GapAwareTrackingToken.newInstance(11079,
                                                                list(10155,
                                                                     10156,
                                                                     10157,
                                                                     10158,
                                                                     10159,
                                                                     10160,
                                                                     10161,
                                                                     10162,
                                                                     10163,
                                                                     10164,
                                                                     10165,
                                                                     10166,
                                                                     10167,
                                                                     10168,
                                                                     10169,
                                                                     10170,
                                                                     10171,
                                                                     10172,
                                                                     10173,
                                                                     10174,
                                                                     10175,
                                                                     10176,
                                                                     10177,
                                                                     10178,
                                                                     10179,
                                                                     10180,
                                                                     10198,
                                                                     10199,
                                                                     10200,
                                                                     10201,
                                                                     10202,
                                                                     10203,
                                                                     10204,
                                                                     10205,
                                                                     10206,
                                                                     10207,
                                                                     10208,
                                                                     10209,
                                                                     10210,
                                                                     10211,
                                                                     10212,
                                                                     10213,
                                                                     10214,
                                                                     10215,
                                                                     10216,
                                                                     10217,
                                                                     10218,
                                                                     10219,
                                                                     10220,
                                                                     10221,
                                                                     10222,
                                                                     10223,
                                                                     10224,
                                                                     10225,
                                                                     10226,
                                                                     10227,
                                                                     10228)));
            tokensAtReset.put("globalEventStore",
                              GapAwareTrackingToken.newInstance(38341,
                                                                list(36941,
                                                                     36942,
                                                                     36943,
                                                                     36944,
                                                                     36945,
                                                                     36946,
                                                                     36947,
                                                                     36948,
                                                                     36949,
                                                                     36950,
                                                                     36951,
                                                                     36952,
                                                                     36953,
                                                                     36954,
                                                                     36955,
                                                                     36956,
                                                                     36957,
                                                                     36958,
                                                                     36959,
                                                                     36960,
                                                                     36961,
                                                                     36962,
                                                                     36963,
                                                                     36964,
                                                                     36965,
                                                                     36966,
                                                                     36967,
                                                                     36968,
                                                                     36969,
                                                                     36970,
                                                                     37157,
                                                                     37158,
                                                                     37159,
                                                                     37160,
                                                                     37161,
                                                                     37162,
                                                                     37163,
                                                                     37164,
                                                                     37165,
                                                                     37166,
                                                                     37167,
                                                                     37168,
                                                                     37169,
                                                                     37170,
                                                                     37171,
                                                                     37172,
                                                                     37173,
                                                                     37174,
                                                                     37175,
                                                                     37176,
                                                                     37177,
                                                                     37178,
                                                                     37179,
                                                                     37180,
                                                                     37181,
                                                                     37182,
                                                                     37183,
                                                                     37184,
                                                                     37295,
                                                                     37296,
                                                                     37297,
                                                                     37298,
                                                                     37299,
                                                                     37300,
                                                                     37301,
                                                                     37302,
                                                                     37303,
                                                                     37304,
                                                                     37305,
                                                                     37306,
                                                                     37307,
                                                                     37308,
                                                                     37309,
                                                                     37310,
                                                                     37311,
                                                                     37312,
                                                                     37313,
                                                                     37314,
                                                                     37315,
                                                                     37316,
                                                                     37317,
                                                                     37318,
                                                                     37319,
                                                                     37320,
                                                                     37321,
                                                                     37322,
                                                                     37323,
                                                                     37324,
                                                                     37331,
                                                                     37332,
                                                                     37333,
                                                                     37334,
                                                                     37335,
                                                                     37336,
                                                                     37337,
                                                                     37338,
                                                                     37339,
                                                                     37340,
                                                                     37341,
                                                                     37342,
                                                                     37343,
                                                                     37344,
                                                                     37345,
                                                                     37346,
                                                                     37347,
                                                                     37348,
                                                                     37349,
                                                                     37350,
                                                                     37351,
                                                                     37352,
                                                                     37353,
                                                                     37354,
                                                                     37355,
                                                                     37356,
                                                                     37357,
                                                                     37358,
                                                                     37359,
                                                                     37360,
                                                                     37361,
                                                                     37362,
                                                                     37717,
                                                                     37718,
                                                                     37719,
                                                                     37720,
                                                                     37721,
                                                                     37722,
                                                                     37723,
                                                                     37724,
                                                                     37725,
                                                                     37726,
                                                                     37727,
                                                                     37728,
                                                                     37729,
                                                                     37730,
                                                                     37731,
                                                                     37732,
                                                                     37733,
                                                                     37734,
                                                                     37897,
                                                                     37898,
                                                                     37899,
                                                                     37900,
                                                                     37901,
                                                                     37902,
                                                                     37903,
                                                                     37904,
                                                                     37905,
                                                                     37906,
                                                                     37907,
                                                                     37908,
                                                                     37909,
                                                                     37910,
                                                                     37911,
                                                                     37912,
                                                                     37913,
                                                                     37914,
                                                                     37915,
                                                                     37916,
                                                                     37917,
                                                                     37918,
                                                                     37919,
                                                                     37920,
                                                                     37921,
                                                                     37922,
                                                                     37923,
                                                                     37924,
                                                                     37925,
                                                                     37926,
                                                                     37927)));
            MultiSourceTrackingToken multiTokenAtReset = new MultiSourceTrackingToken(tokensAtReset);

            // and
            Map<String, TrackingToken> startPosition = new HashMap<>();
            startPosition.put("localEventStore", GapAwareTrackingToken.newInstance(9960, emptySet()));
            startPosition.put("globalEventStore", GapAwareTrackingToken.newInstance(37192, emptySet()));
            MultiSourceTrackingToken multiStartPosition = new MultiSourceTrackingToken(startPosition);

            TrackingToken replayToken = ReplayToken.createReplayToken(multiTokenAtReset, multiStartPosition);

            // when
            Map<String, TrackingToken> nextPositions = new HashMap<>();
            nextPositions.put("localEventStore", GapAwareTrackingToken.newInstance(9961, emptySet()));
            nextPositions.put("globalEventStore", GapAwareTrackingToken.newInstance(37192, emptySet()));
            TrackingToken nextToken = new MultiSourceTrackingToken(nextPositions);

            TrackingToken advancedToken = ((ReplayToken) replayToken).advancedTo(nextToken);

            // then
            assertThat(advancedToken).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(advancedToken)).isTrue();
        }

        private List<Long> list(Integer... values) {
            return Stream.of(values)
                         .map(Long::valueOf)
                         .collect(Collectors.toList());
        }
    }

    @Nested
    class MergedTrackingTokenAdvancedToGapAwareTrackingToken {

        @Test
        void isReplayOnMergedTokenWithoutGaps() {
            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    GapAwareTrackingToken.newInstance(5, emptySet()),
                    GapAwareTrackingToken.newInstance(10, emptySet())
            );
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Position 3: before lower segment
            TrackingToken beforeLowerSegment = replayToken.advancedTo(GapAwareTrackingToken.newInstance(3, emptySet()));
            assertThat(beforeLowerSegment).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(beforeLowerSegment)).as("Before lower segment should be replay").isTrue();

            // Position 5: at lower segment
            TrackingToken atLowerSegment = replayToken.advancedTo(GapAwareTrackingToken.newInstance(5, emptySet()));
            assertThat(atLowerSegment).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(atLowerSegment)).as("At lower segment should be replay").isTrue();

            // Position 7: between segments
            TrackingToken betweenSegments = replayToken.advancedTo(GapAwareTrackingToken.newInstance(7, emptySet()));
            assertThat(betweenSegments).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(betweenSegments)).as("Between segments should NOT be replay").isFalse();

            // Position 10: at upper segment
            TrackingToken atUpperSegment = replayToken.advancedTo(GapAwareTrackingToken.newInstance(10, emptySet()));
            assertThat(atUpperSegment).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(atUpperSegment)).as("At upper segment should NOT be replay").isFalse();

            // Position 11: past upper segment - exits replay
            TrackingToken pastUpperSegment = replayToken.advancedTo(GapAwareTrackingToken.newInstance(11, emptySet()));
            assertThat(pastUpperSegment).isInstanceOf(GapAwareTrackingToken.class);
            assertThat(ReplayToken.isReplay(pastUpperSegment)).as("Past upper segment should exit replay").isFalse();
        }

        @Test
        void isReplayOnMergedTokenWithGapInLowerSegment() {
            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    new GapAwareTrackingToken(3, setOf(2L)),
                    new GapAwareTrackingToken(9, emptySet())
            );
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Position 1: before gap at 2, was processed
            TrackingToken beforeGapWasProcessed = replayToken.advancedTo(new GapAwareTrackingToken(1, emptySet()));
            assertThat(beforeGapWasProcessed).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(beforeGapWasProcessed)).as("Before gap should be replay").isTrue();

            // Position 2: at gap position in lower segment
            TrackingToken atGapPosition = replayToken.advancedTo(new GapAwareTrackingToken(2, emptySet()));
            assertThat(atGapPosition).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(atGapPosition)).as("At gap position should NOT be replay").isFalse();

            // Position 3: at lower index, was processed
            TrackingToken atLowerIndex = replayToken.advancedTo(new GapAwareTrackingToken(3, emptySet()));
            assertThat(atLowerIndex).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(atLowerIndex)).as("At lower index should be replay").isTrue();

            // Position 4: past lower segment
            TrackingToken pastLowerSegment = replayToken.advancedTo(new GapAwareTrackingToken(4, emptySet()));
            assertThat(pastLowerSegment).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(pastLowerSegment)).as("Past lower segment should NOT be replay").isFalse();

            // Position 9: at upper index
            TrackingToken atUpperIndex = replayToken.advancedTo(new GapAwareTrackingToken(9, emptySet()));
            assertThat(atUpperIndex).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(atUpperIndex)).as("At upper index should NOT be replay").isFalse();

            // Position 10: past upper, exits replay
            TrackingToken pastUpperExitsReplay = replayToken.advancedTo(new GapAwareTrackingToken(10, emptySet()));
            assertThat(pastUpperExitsReplay).isInstanceOf(GapAwareTrackingToken.class);
            assertThat(ReplayToken.isReplay(pastUpperExitsReplay)).as("Past upper should exit replay").isFalse();
        }

        @Test
        void isReplayOnMergedTokenWithGapInUpperSegment() {
            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    GapAwareTrackingToken.newInstance(5, emptySet()),
                    GapAwareTrackingToken.newInstance(10, setOf(8L))  // gap at 8
            );
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Event at gap position 8 - NOT replay
            TrackingToken resultAt8 = replayToken.advancedTo(GapAwareTrackingToken.newInstance(8, emptySet()));
            assertThat(resultAt8).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(resultAt8)).as("Event at gap position should NOT be replay").isFalse();

            // Event at position 9 (past lower segment) - NOT replay
            TrackingToken resultAt9 = ((ReplayToken) resultAt8).advancedTo(GapAwareTrackingToken.newInstance(9,
                                                                                                             setOf(8L)));
            assertThat(resultAt9).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(resultAt9)).as("Event past lower segment should NOT be replay").isFalse();
        }

        @Test
        void isReplayOnMergedTokenWithGapInBothSegments() {
            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    GapAwareTrackingToken.newInstance(5, setOf(2L)),
                    GapAwareTrackingToken.newInstance(10, setOf(8L))
            );
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Event at lower gap (position 2)
            TrackingToken eventAtLowerGap = replayToken.advancedTo(GapAwareTrackingToken.newInstance(2, emptySet()));
            assertThat(eventAtLowerGap).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(eventAtLowerGap)).as("Event at lower gap should NOT be replay").isFalse();

            // Event at upper gap (position 8)
            TrackingToken eventAtUpperGap = replayToken.advancedTo(GapAwareTrackingToken.newInstance(8, emptySet()));
            assertThat(eventAtUpperGap).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(eventAtUpperGap)).as("Event at upper gap should NOT be replay").isFalse();

            // Event past lower segment (position 7)
            TrackingToken eventPastLowerSegment = replayToken.advancedTo(GapAwareTrackingToken.newInstance(7,
                                                                                                           emptySet()));
            assertThat(eventPastLowerSegment).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(eventPastLowerSegment))
                    .as("Event past lower segment should NOT be replay")
                    .isFalse();
        }

        // ==================== Progressive Replay Through MergedTrackingToken ====================

        /**
         * Tests progressive replay through a MergedTrackingToken, verifying replay status at each step. Key insight:
         * Only events ≤ lower segment index are replays. Events past lower but before upper are NOT replays (lower
         * segment hasn't seen them) but stay in ReplayToken wrapper.
         */
        @Test
        void progressiveReplayThroughMergedToken() {
            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    GapAwareTrackingToken.newInstance(3, setOf(2L)),  // gap at 2, lower at 3
                    GapAwareTrackingToken.newInstance(9, emptySet())  // upper at 9
            );
            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);

            // Position 0: before lower segment, was processed → replay
            currentToken = ((ReplayToken) currentToken).advancedTo(GapAwareTrackingToken.newInstance(0, emptySet()));
            assertThat(ReplayToken.isReplay(currentToken)).as("Position 0 should be replay").isTrue();

            // Position 1: before gap, was processed → replay
            currentToken = ((ReplayToken) currentToken).advancedTo(GapAwareTrackingToken.newInstance(1, emptySet()));
            assertThat(ReplayToken.isReplay(currentToken)).as("Position 1 should be replay").isTrue();

            // Position 2: gap in lower segment → NOT replay
            currentToken = ((ReplayToken) currentToken).advancedTo(GapAwareTrackingToken.newInstance(2, emptySet()));
            assertThat(ReplayToken.isReplay(currentToken)).as("Position 2 (gap) should NOT be replay").isFalse();

            // Position 3: at lower segment index, was processed → replay
            currentToken = ((ReplayToken) currentToken).advancedTo(GapAwareTrackingToken.newInstance(3, emptySet()));
            assertThat(ReplayToken.isReplay(currentToken)).as("Position 3 should be replay").isTrue();

            // Position 5: between segments → NOT replay (lower segment hasn't seen it)
            currentToken = ((ReplayToken) currentToken).advancedTo(GapAwareTrackingToken.newInstance(5, emptySet()));
            assertThat(currentToken).as("Should still be in ReplayToken").isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(currentToken)).as("Position 5 should NOT be replay").isFalse();

            // Position 9: at upper segment → NOT replay (lower segment hasn't seen it)
            currentToken = ((ReplayToken) currentToken).advancedTo(GapAwareTrackingToken.newInstance(9, emptySet()));
            assertThat(currentToken).as("Should still be in ReplayToken").isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(currentToken)).as("Position 9 should NOT be replay").isFalse();

            // Position 10: past upper segment → exits replay
            currentToken = ((ReplayToken) currentToken).advancedTo(GapAwareTrackingToken.newInstance(10, emptySet()));
            assertThat(ReplayToken.isReplay(currentToken)).as("Position 10 should exit replay").isFalse();
        }

        @Test
        void givenSegmentsAtSamePositionWhenAtThatPositionThenReplay() {
            // given
            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    GapAwareTrackingToken.newInstance(10, emptySet()),
                    GapAwareTrackingToken.newInstance(10, emptySet())
            );
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // when
            TrackingToken result = replayToken.advancedTo(GapAwareTrackingToken.newInstance(10, emptySet()));

            // then
            assertThat(result).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(result)).as("Event at shared position should be replay").isTrue();
        }

        @Test
        void givenSegmentsAtSamePositionWhenAfterThatPositionThenNotReplay() {
            // given
            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    GapAwareTrackingToken.newInstance(10, emptySet()),
                    GapAwareTrackingToken.newInstance(10, emptySet())
            );
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // when
            TrackingToken result = replayToken.advancedTo(GapAwareTrackingToken.newInstance(11, emptySet()));

            // then
            assertThat(ReplayToken.isReplay(result)).as("Event past shared position should exit replay").isFalse();
        }

        @Test
        void givenSegmentsWithSameGapWhenAtThatGapThenNotReplay() {
            // given
            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    GapAwareTrackingToken.newInstance(7, setOf(5L)),
                    GapAwareTrackingToken.newInstance(10, setOf(5L))
            );
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // when
            TrackingToken result = replayToken.advancedTo(GapAwareTrackingToken.newInstance(5, emptySet()));

            // then
            assertThat(result).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(result)).as("Event at shared gap position should NOT be replay").isFalse();
        }

        /**
         * MergedTrackingToken containing another MergedTrackingToken. This can happen with multiple merge operations.
         * The lowerBound of nested MergedToken is still the innermost lowerSegment (3).
         */
        @Test
        void givenNestedMergedTokenWhenAtInnermostLowerSegmentThenReplay() {
            // given
            MergedTrackingToken innerMerged = new MergedTrackingToken(
                    GapAwareTrackingToken.newInstance(3, emptySet()),
                    GapAwareTrackingToken.newInstance(5, emptySet())
            );

            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    innerMerged,
                    GapAwareTrackingToken.newInstance(10, emptySet())
            );

            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // when
            TrackingToken result = replayToken.advancedTo(GapAwareTrackingToken.newInstance(3, emptySet()));

            // then
            assertThat(result).as("Should still be in ReplayToken wrapper").isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(result))
                    .as("Event at position 3 IS replay - innermost lower segment has seen it")
                    .isTrue();
        }

        @Test
        void givenNestedMergedTokenWhenPastInnermostLowerSegmentThenNotReplay() {
            // given
            MergedTrackingToken innerMerged = new MergedTrackingToken(
                    GapAwareTrackingToken.newInstance(3, emptySet()),
                    GapAwareTrackingToken.newInstance(5, emptySet())
            );

            // Outer merged token with inner as lower segment
            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    innerMerged,
                    GapAwareTrackingToken.newInstance(10, emptySet())
            );

            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // when
            TrackingToken result = replayToken.advancedTo(GapAwareTrackingToken.newInstance(7, emptySet()));

            // then
            assertThat(result).as("Should still be in ReplayToken wrapper").isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(result))
                    .as("Event at position 7 is NOT replay - innermost lower segment (3) hasn't seen it")
                    .isFalse();
        }

        @Test
        void givenNestedMergedTokenWhenPastOuterUpperBoundThenNotReplay() {
            // given
            MergedTrackingToken innerMerged = new MergedTrackingToken(
                    GapAwareTrackingToken.newInstance(3, emptySet()),
                    GapAwareTrackingToken.newInstance(5, emptySet())
            );

            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    innerMerged,
                    GapAwareTrackingToken.newInstance(10, emptySet())
            );

            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // when
            TrackingToken result = replayToken.advancedTo(GapAwareTrackingToken.newInstance(11, emptySet()));

            // then
            assertThat(ReplayToken.isReplay(result)).as("Event past outer upper bound should exit replay").isFalse();
        }
    }

    private static Set<Long> setOf(Long... values) {
        return new HashSet<>(Arrays.asList(values));
    }
}
