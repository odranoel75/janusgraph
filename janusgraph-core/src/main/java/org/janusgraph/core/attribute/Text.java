// Copyright 2017 JanusGraph Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.janusgraph.core.attribute;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.janusgraph.graphdb.query.JanusGraphPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Comparison relations for text objects. These comparisons are based on a tokenized representation
 * of the text, i.e. the text is considered as a set of word tokens.
 *
 * @author Matthias Broecheler (me@matthiasb.com)
 */

public enum Text implements JanusGraphPredicate
{

    /**
     * Whether the text contains a given term as a token in the text (case insensitive)
     */
    CONTAINS
        {
            @Override public boolean test(Object value, Object condition)
            {
                this.preevaluate(value, condition);
                return value != null && evaluateRaw(value.toString(), (String) condition);
            }

            @Override public boolean evaluateRaw(String value, String terms)
            {
                Set<String> tokens = Sets.newHashSet(tokenize(value.toLowerCase()));
                terms = terms.trim();
                List<String> tokenTerms = tokenize(terms.toLowerCase());
                if (!terms.isEmpty() && tokenTerms.isEmpty())
                    return false;
                for (String term : tokenTerms)
                {
                    if (!tokens.contains(term))
                        return false;
                }
                return true;
            }

            @Override public boolean isValidCondition(Object condition)
            {
                return condition != null && condition instanceof String && StringUtils.isNotBlank((String) condition);
            }

            @Override public String toString()
            {
                return "textContains";
            }
        },

    /**
     * Whether the text does not contain a given term as a token in the text (case insensitive)
     */
    NOT_CONTAINS
        {
            @Override public boolean test(Object value, Object condition)
            {
                this.preevaluate(value, condition);
                return value != null && evaluateRaw(value.toString(), (String) condition);
            }

            @Override public boolean evaluateRaw(String value, String terms)
            {
                Set<String> tokens = Sets.newHashSet(tokenize(value.toLowerCase()));
                terms = terms.trim();
                List<String> tokenTerms = tokenize(terms.toLowerCase());
                if (!terms.isEmpty() && tokenTerms.isEmpty())
                    return true;
                for (String term : tokenTerms)
                {
                    if (!tokens.contains(term))
                        return true;
                }
                return false;
            }

            @Override public boolean isValidCondition(Object condition)
            {
                return condition != null && condition instanceof String && StringUtils.isNotBlank((String) condition);
            }

            @Override public String toString()
            {
                return "notTextContains";
            }
        },

    /**
     * Whether the text contains a token that starts with a given term (case insensitive)
     */
    CONTAINS_PREFIX
        {
            @Override public boolean test(Object value, Object condition)
            {
                this.preevaluate(value, condition);
                return value != null && evaluateRaw(value.toString(), (String) condition);
            }

            @Override public boolean evaluateRaw(String value, String prefix)
            {
                for (String token : tokenize(value.toLowerCase()))
                {
                    if (PREFIX.evaluateRaw(token, prefix.toLowerCase()))
                        return true;
                }
                return false;
            }

            @Override public boolean isValidCondition(Object condition)
            {
                return condition != null && condition instanceof String;
            }

            @Override public String toString()
            {
                return "textContainsPrefix";
            }

        },

    /**
     * Whether the text contains a token that starts with a given term (case insensitive)
     */
    NOT_CONTAINS_PREFIX
        {
            @Override public boolean test(Object value, Object condition)
            {
                this.preevaluate(value, condition);
                return value != null && evaluateRaw(value.toString(), (String) condition);
            }

            @Override public boolean evaluateRaw(String value, String prefix)
            {
                for (String token : tokenize(value.toLowerCase()))
                {
                    if (NOT_PREFIX.evaluateRaw(token, prefix.toLowerCase()))
                        return true;
                }
                return false;
            }

            @Override public boolean isValidCondition(Object condition)
            {
                return condition != null && condition instanceof String;
            }

            @Override public String toString()
            {
                return "textContainsPrefix";
            }

        },

    /**
     * Whether the text contains a token that ends with a given term (case insensitive)
     */
    CONTAINS_SUFFIX
        {
            @Override public boolean test(Object value, Object condition)
            {
                this.preevaluate(value, condition);
                return value != null && evaluateRaw(value.toString(), (String) condition);
            }

            @Override public boolean evaluateRaw(String value, String prefix)
            {
                for (String token : tokenize(value.toLowerCase()))
                {
                    if (SUFFIX.evaluateRaw(token, prefix.toLowerCase()))
                        return true;
                }
                return false;
            }

            @Override public boolean isValidCondition(Object condition)
            {
                return condition != null && condition instanceof String;
            }

            @Override public String toString()
            {
                return "textContainsSuffix";
            }

        },

    /**
     * Whether the text contains a token that starts with a given term (case insensitive)
     */
    NOT_CONTAINS_SUFFIX
        {
            @Override public boolean test(Object value, Object condition)
            {
                this.preevaluate(value, condition);
                return value != null && evaluateRaw(value.toString(), (String) condition);
            }

            @Override public boolean evaluateRaw(String value, String suffix)
            {
                for (String token : tokenize(value.toLowerCase()))
                {
                    if (NOT_SUFFIX.evaluateRaw(token, suffix.toLowerCase()))
                        return true;
                }
                return false;
            }

            @Override public boolean isValidCondition(Object condition)
            {
                return condition != null && condition instanceof String;
            }

            @Override public String toString()
            {
                return "notTextContainsSuffix";
            }

        },

    /**
     * Whether the text contains a token that matches a regular expression
     */
    CONTAINS_REGEX
        {
            @Override public boolean test(Object value, Object condition)
            {
                this.preevaluate(value, condition);
                return value != null && evaluateRaw(value.toString(), (String) condition);
            }

        @Override
        public boolean evaluateRaw(String value, String regex) {
            // LPPM - CDMP-1745 - attempt to fix regex inconsistencies in queries where containsRegex is used repeatedly in the
            // same statement (e.g. inside union())
//            for (String token : tokenize(value.toLowerCase())) {
            for (String token : tokenize(value)) {
                if (REGEX.evaluateRaw(token,regex)) return true;
            }
            return false;
        }

            @Override public boolean isValidCondition(Object condition)
            {
                return condition != null && condition instanceof String && StringUtils.isNotBlank(condition.toString());
            }

            @Override public String toString()
            {
                return "textContainsRegex";
            }

        },

    /**
     * Whether the text starts with a given prefix (case sensitive)
     */
    PREFIX
        {
            @Override public boolean test(Object value, Object condition)
            {
                this.preevaluate(value, condition);
                return value != null && evaluateRaw(value.toString(), (String) condition);
            }

            @Override public boolean evaluateRaw(String value, String prefix)
            {
                return value.startsWith(prefix.trim());
            }

            @Override public boolean isValidCondition(Object condition)
            {
                return condition != null && condition instanceof String;
            }

            @Override public String toString()
            {
                return "textPrefix";
            }

        },

    /**
     * Whether the text does not start with a given prefix (case sensitive)
     */
    NOT_PREFIX
        {
            @Override public boolean test(Object value, Object condition)
            {
                this.preevaluate(value, condition);
                return value != null && evaluateRaw(value.toString(), (String) condition);
            }

            @Override public boolean evaluateRaw(String value, String prefix)
            {
                return !value.startsWith(prefix.trim());
            }

            @Override public boolean isValidCondition(Object condition)
            {
                return condition != null && condition instanceof String;
            }

            @Override public String toString()
            {
                return "notTextPrefix";
            }

        },

    /**
     * Whether the text starts with a given prefix (case sensitive)
     */
    SUFFIX
        {
            @Override public boolean test(Object value, Object condition)
            {
                this.preevaluate(value, condition);
                return value != null && evaluateRaw(value.toString(), (String) condition);
            }

            @Override public boolean evaluateRaw(String value, String suffix)
            {
                return value.endsWith(suffix.trim());
            }

            @Override public boolean isValidCondition(Object condition)
            {
                return condition != null && condition instanceof String;
            }

            @Override public String toString()
            {
                return "textSuffix";
            }

        },

    /**
     * Whether the text does not start with a given prefix (case sensitive)
     */
    NOT_SUFFIX
        {
            @Override public boolean test(Object value, Object condition)
            {
                this.preevaluate(value, condition);
                return value != null && evaluateRaw(value.toString(), (String) condition);
            }

            @Override public boolean evaluateRaw(String value, String suffix)
            {
                return !value.endsWith(suffix.trim());
            }

            @Override public boolean isValidCondition(Object condition)
            {
                return condition != null && condition instanceof String;
            }

            @Override public String toString()
            {
                return "notTextPrefix";
            }

        },

    /**
     * Whether the text matches a regular expression (case sensitive)
     */
    REGEX
        {
            @Override public boolean test(Object value, Object condition)
            {
                this.preevaluate(value, condition);
                return value != null && evaluateRaw(value.toString(), (String) condition);
            }

            public boolean evaluateRaw(String value, String regex)
            {

                // LPPM - CDMP-1745 -  use the regexp automaton to make sure that the regex string is the same regardless
                // of whether we run this in elastic search or locally.
                Automaton regExpAutomaton = (new RegExp(regex)).toAutomaton();
                return Operations.run(regExpAutomaton, value);

                //            return value.matches(regex);
            }

            @Override public boolean isValidCondition(Object condition)
            {
                return condition != null && condition instanceof String && StringUtils.isNotBlank(condition.toString());
            }

            @Override public String toString()
            {
                return "textRegex";
            }

        },

    /**
     * Whether the text is at X Lenvenstein of a token (case sensitive)
     * with X=:
     * - 0 for strings of one or two characters
     * - 1 for strings of three, four or five characters
     * - 2 for strings of more than five characters
     */
    FUZZY
        {
            @Override public boolean test(Object value, Object condition)
            {
                this.preevaluate(value, condition);
                return value != null && evaluateRaw(value.toString(), (String) condition);
            }

            @Override public boolean evaluateRaw(String value, String term)
            {
                return isFuzzy(term.trim(), value.trim());
            }

            @Override public boolean isValidCondition(Object condition)
            {
                return condition != null && condition instanceof String && StringUtils.isNotBlank(condition.toString());
            }

            @Override public String toString()
            {
                return "textFuzzy";
            }

        },

    /**
     * Whether the text contains a token is at X Lenvenstein of a token (case insensitive)
     * with X=:
     * - 0 for strings of one or two characters
     * - 1 for strings of three, four or five characters
     * - 2 for strings of more than five characters
     */
    CONTAINS_FUZZY
        {
            @Override public boolean test(Object value, Object condition)
            {
                this.preevaluate(value, condition);
                return value != null && evaluateRaw(value.toString(), (String) condition);
            }

            @Override public boolean evaluateRaw(String value, String term)
            {
                for (String token : tokenize(value.toLowerCase()))
                {
                    if (isFuzzy(term.toLowerCase(), token))
                        return true;
                }
                return false;
            }

            @Override public boolean isValidCondition(Object condition)
            {
                return condition != null && condition instanceof String && StringUtils.isNotBlank(condition.toString());
            }

            @Override public String toString()
            {
                return "textContainsFuzzy";
            }

        };

    /**
     * Whether {@code term} is at X Lenvenstein of a {@code value}
     * with X=:
     * - 0 for strings of one or two characters
     * - 1 for strings of three, four or five characters
     * - 2 for strings of more than five characters
     *
     * @param value
     * @param term
     * @return true if {@code term} is similar to {@code value}
     */
    private static boolean isFuzzy(String term, String value)
    {
        int distance;
        term = term.trim();
        if (term.length() < 3)
        {
            distance = 0;
        }
        else if (term.length() < 6)
        {
            distance = 1;
        }
        else
        {
            distance = 2;
        }
        return LevenshteinDistance.getDefaultInstance().apply(value, term) <= distance;
    }

    private static final Logger log = LoggerFactory.getLogger(Text.class);

    public void preevaluate(Object value, Object condition)
    {
        Preconditions.checkArgument(this.isValidCondition(condition), "Invalid condition provided: %s", condition);
        if (!(value instanceof String))
            log.debug("Value not a string: " + value);
    }

    abstract boolean evaluateRaw(String value, String condition);

    private static final int MIN_TOKEN_LENGTH = 1;

    public static List<String> tokenize(String str)
    {
        final ArrayList<String> tokens = new ArrayList<>();
        int previous = 0;
        for (int p = 0; p < str.length(); p++)
        {
            if (!Character.isLetterOrDigit(str.charAt(p)))
            {
                if (p > previous + MIN_TOKEN_LENGTH)
                    tokens.add(str.substring(previous, p));
                previous = p + 1;
            }
        }
        if (previous + MIN_TOKEN_LENGTH < str.length())
            tokens.add(str.substring(previous, str.length()));
        return tokens;
    }

    @Override public boolean isValidValueType(Class<?> clazz)
    {
        Preconditions.checkNotNull(clazz);
        return clazz.equals(String.class);
    }

    @Override public boolean hasNegation()
    {
        return false;
    }

    @Override public JanusGraphPredicate negate()
    {
        throw new UnsupportedOperationException();
    }

    @Override public boolean isQNF()
    {
        return true;
    }

    //////////////// statics
    public final static Set<Text> HAS_SUFFIX = Collections
        .unmodifiableSet(EnumSet.of(SUFFIX, CONTAINS_SUFFIX, NOT_SUFFIX, NOT_CONTAINS_SUFFIX));

    public final static Set<Text> HAS_CONTAINS = Collections.unmodifiableSet(EnumSet
        .of(CONTAINS, CONTAINS_PREFIX, CONTAINS_SUFFIX, CONTAINS_REGEX, CONTAINS_FUZZY, NOT_CONTAINS,
            NOT_CONTAINS_PREFIX, NOT_CONTAINS_SUFFIX));

    public static <V> P<V> textContains(final V value)
    {
        return new P(Text.CONTAINS, value);
    }

    public static <V> P<V> textContainsPrefix(final V value)
    {
        return new P(Text.CONTAINS_PREFIX, value);
    }

    public static <V> P<V> textContainsSuffix(final V value)
    {
        return new P(Text.CONTAINS_SUFFIX, value);
    }

    public static <V> P<V> textContainsRegex(final V value)
    {
        return new P(Text.CONTAINS_REGEX, value);
    }

    public static <V> P<V> notTextContains(final V value)
    {
        return new P(Text.NOT_CONTAINS, value);
    }

    public static <V> P<V> notTextContainsSuffix(final V value)
    {
        return new P(Text.NOT_CONTAINS_SUFFIX, value);
    }

    public static <V> P<V> notTextPrefix(final V value)
    {
        return new P(Text.NOT_PREFIX, value);
    }

    public static <V> P<V> notTextSuffix(final V value)
    {
        return new P(Text.NOT_SUFFIX, value);
    }

    public static <V> P<V> textPrefix(final V value)
    {
        return new P(Text.PREFIX, value);
    }

    public static <V> P<V> textSuffix(final V value)
    {
        return new P(Text.SUFFIX, value);
    }

    public static <V> P<V> textRegex(final V value)
    {
        return new P(Text.REGEX, value);
    }

    public static <V> P<V> textContainsFuzzy(final V value)
    {
        return new P(Text.CONTAINS_FUZZY, value);
    }

    public static <V> P<V> textFuzzy(final V value)
    {
        return new P(Text.FUZZY, value);
    }
}
