/*-
 * #%L
 * java-diff-utils
 * %%
 * Copyright (C) 2009 - 2017 java-diff-utils
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 * #L%
 */
package com.bernaferrari.diffutils.diffs.text

import com.bernaferrari.diffutils.diffs.DiffUtils
import com.bernaferrari.diffutils.diffs.algorithm.DiffException
import com.bernaferrari.diffutils.diffs.patch.*
import com.bernaferrari.diffutils.diffs.text.DiffRow.Tag
import java.util.*
import java.util.regex.Pattern

/**
 * This class for generating DiffRows for side-by-sidy view. You can customize the way of generating. For example, show
 * inline diffs on not, ignoring white spaces or/and blank lines and so on. All parameters for generating are optional.
 * If you do not specify them, the class will use the default values.
 *
 * These values are: showInlineDiffs = false; ignoreWhiteSpaces = true; ignoreBlankLines = true; ...
 *
 * For instantiating the DiffRowGenerator you should use the its builder. Like in example  `
 * DiffRowGenerator generator = new DiffRowGenerator.Builder().showInlineDiffs(true).
 * ignoreWhiteSpaces(true).columnWidth(100).build();
` *
 */
class DiffRowGenerator private constructor(builder: Builder) {
    private val columnWidth: Int
    private val equalizer: (String, String) -> Boolean
    private val ignoreWhiteSpaces: Boolean
    private val inlineDiffSplitter: (String) -> List<String>
    private val mergeOriginalRevised: Boolean
    private val newTag: (Boolean) -> String
    private val oldTag: (Boolean) -> String
    private val reportLinesUnchanged: Boolean

    private val showInlineDiffs: Boolean

    init {
        showInlineDiffs = builder.showInlineDiffs
        ignoreWhiteSpaces = builder.ignoreWhiteSpaces
        oldTag = builder.oldTag
        newTag = builder.newTag
        columnWidth = builder.columnWidth
        mergeOriginalRevised = builder.mergeOriginalRevised
        inlineDiffSplitter = builder.inlineDiffSplitter
        equalizer = if (ignoreWhiteSpaces) IGNORE_WHITESPACE_EQUALIZER else DEFAULT_EQUALIZER
        reportLinesUnchanged = builder.reportLinesUnchanged

        Objects.requireNonNull(inlineDiffSplitter)
    }

    /**
     * Get the DiffRows describing the difference between original and revised texts using the given patch. Useful for
     * displaying side-by-side diff.
     *
     * @param original the original text
     * @param revised the revised text
     * @return the DiffRows between original and revised texts
     */
    @Throws(DiffException::class)
    fun generateDiffRows(original: List<String>, revised: List<String>): List<DiffRow> {
        return generateDiffRows(original, DiffUtils.diff(original, revised, equalizer))
    }

    /**
     * Generates the DiffRows describing the difference between original and revised texts using the given patch. Useful
     * for displaying side-by-side diff.
     *
     * @param original the original text
     * @param revised the revised text
     * @param patch the given patch
     * @return the DiffRows between original and revised texts
     */
    @Throws(DiffException::class)
    fun generateDiffRows(original: List<String>, patch: Patch<String>): List<DiffRow> {
        val diffRows = ArrayList<DiffRow>()
        var endPos = 0
        val deltaList = patch.getDeltas()
        for (i in deltaList.indices) {
            val delta = deltaList[i]
            val orig = delta.source
            val rev = delta.target

            for (line in original.subList(endPos, orig.position)) {
                diffRows.add(buildDiffRow(Tag.EQUAL, line, line))
            }

            // Inserted DiffRow
            if (delta is InsertDelta<*>) {
                endPos = orig.last() + 1
                for (line in rev.lines) {
                    diffRows.add(buildDiffRow(Tag.INSERT, "", line))
                }
                continue
            }

            // Deleted DiffRow
            if (delta is DeleteDelta<*>) {
                endPos = orig.last() + 1
                for (line in orig.lines) {
                    diffRows.add(buildDiffRow(Tag.DELETE, line, ""))
                }
                continue
            }

            if (showInlineDiffs) {
                diffRows.addAll(generateInlineDiffs(delta))
            } else {
                for (j in 0 until Math.max(orig.size(), rev.size())) {
                    diffRows.add(
                        buildDiffRow(
                            Tag.CHANGE,
                            if (orig.lines.size > j) orig.lines.get(j) else "",
                            if (rev.lines.size > j) rev.lines.get(j) else ""
                        )
                    )
                }
            }
            endPos = orig.last() + 1
        }

        // Copy the final matching chunk if any.
        for (line in original.subList(endPos, original.size)) {
            diffRows.add(buildDiffRow(Tag.EQUAL, line, line))
        }
        return diffRows
    }

    private fun buildDiffRow(type: Tag, orgline: String, newline: String): DiffRow {
        if (reportLinesUnchanged) {
            return DiffRow(type, orgline, newline)
        } else {
            var wrapOrg = preprocessLine(orgline)
            if (Tag.DELETE == type) {
                if (mergeOriginalRevised || showInlineDiffs) {
                    wrapOrg = oldTag.invoke(true) + wrapOrg + oldTag.invoke(false)
                }
            }
            var wrapNew = preprocessLine(newline)
            if (Tag.INSERT == type) {
                if (mergeOriginalRevised) {
                    wrapOrg = newTag.invoke(true) + wrapNew + newTag.invoke(false)
                } else if (showInlineDiffs) {
                    wrapNew = newTag.invoke(true) + wrapNew + newTag.invoke(false)
                }
            }
            return DiffRow(type, wrapOrg, wrapNew)
        }
    }

    private fun buildDiffRowWithoutNormalizing(
        type: Tag,
        orgline: String,
        newline: String
    ): DiffRow {
        return DiffRow(
            type,
            StringUtils.wrapText(orgline, columnWidth),
            StringUtils.wrapText(newline, columnWidth)
        )
    }

    /**
     * Add the inline diffs for given delta
     *
     * @param delta the given delta
     */
    @Throws(DiffException::class)
    private fun generateInlineDiffs(delta: AbstractDelta<String>): List<DiffRow> {
        val orig = StringUtils.normalize(delta.source.lines)
        val rev = StringUtils.normalize(delta.target.lines)
        val origList: MutableList<String>
        val revList: MutableList<String>
        val joinedOrig = orig.joinToString("\n")
        val joinedRev = rev.joinToString("\n")

        origList = inlineDiffSplitter.invoke(joinedOrig).toMutableList()
        revList = inlineDiffSplitter.invoke(joinedRev).toMutableList()

        val inlineDeltas = DiffUtils.diff(origList, revList).getDeltas().asReversed()

        for (inlineDelta in inlineDeltas) {
            val inlineOrig = inlineDelta.source
            val inlineRev = inlineDelta.target
            if (inlineDelta is DeleteDelta<*>) {
                wrapInTag(
                    origList, inlineOrig.position, inlineOrig
                        .position + inlineOrig.size(), oldTag
                )
            } else if (inlineDelta is InsertDelta<*>) {
                if (mergeOriginalRevised) {
                    origList.addAll(
                        inlineOrig.position,
                        revList.subList(
                            inlineRev.position,
                            inlineRev.position + inlineRev.size()
                        )
                    )
                    wrapInTag(
                        origList,
                        inlineOrig.position,
                        inlineOrig.position + inlineRev.size(),
                        newTag
                    )
                } else {
                    wrapInTag(
                        revList,
                        inlineRev.position,
                        inlineRev.position + inlineRev.size(),
                        newTag
                    )
                }
            } else if (inlineDelta is ChangeDelta<*>) {
                if (mergeOriginalRevised) {
                    origList.addAll(
                        inlineOrig.position + inlineOrig.size(),
                        revList.subList(
                            inlineRev.position,
                            inlineRev.position + inlineRev.size()
                        )
                    )
                    wrapInTag(
                        origList,
                        inlineOrig.position + inlineOrig.size(),
                        inlineOrig.position + inlineOrig.size()
                                + inlineRev.size(),
                        newTag
                    )
                } else {
                    wrapInTag(
                        revList,
                        inlineRev.position,
                        inlineRev.position + inlineRev.size(),
                        newTag
                    )
                }
                wrapInTag(
                    origList, inlineOrig.position, inlineOrig
                        .position + inlineOrig.size(), oldTag
                )
            }
        }
        val origResult = StringBuilder()
        val revResult = StringBuilder()
        for (character in origList) {
            origResult.append(character)
        }
        for (character in revList) {
            revResult.append(character)
        }

        val original =
            Arrays.asList(*origResult.toString().split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
        val revised =
            Arrays.asList(*revResult.toString().split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
        val diffRows = ArrayList<DiffRow>()
        for (j in 0 until Math.max(original.size, revised.size)) {
            diffRows.add(
                buildDiffRowWithoutNormalizing(
                    Tag.CHANGE,
                    if (original.size > j) original[j] else "",
                    if (revised.size > j) revised[j] else ""
                )
            )
        }
        return diffRows
    }

    private fun preprocessLine(line: String): String {
        return if (columnWidth == 0) {
            StringUtils.normalize(line)
        } else {
            StringUtils.wrapText(StringUtils.normalize(line), columnWidth)
        }
    }

    /**
     * This class used for building the DiffRowGenerator.
     *
     * @author dmitry
     */
    class Builder {

        var showInlineDiffs = false
        var ignoreWhiteSpaces = false

        var oldTag = { f: Boolean -> if (f) "<span class=\"editOldInline\">" else "</span>" }
        var newTag = { f: Boolean -> if (f) "<span class=\"editNewInline\">" else "</span>" }

        var columnWidth = 0
        var mergeOriginalRevised = false
        var reportLinesUnchanged = false
        var inlineDiffSplitter = SPLITTER_BY_CHARACTER

        /**
         * Show inline diffs in generating diff rows or not.
         *
         * @param val the value to set. Default: false.
         * @return builder with configured showInlineDiff parameter
         */
        fun showInlineDiffs(`val`: Boolean): Builder {
            showInlineDiffs = `val`
            return this
        }

        /**
         * Ignore white spaces in generating diff rows or not.
         *
         * @param val the value to set. Default: true.
         * @return builder with configured ignoreWhiteSpaces parameter
         */
        fun ignoreWhiteSpaces(`val`: Boolean): Builder {
            ignoreWhiteSpaces = `val`
            return this
        }

        /**
         * Give the originial old and new text lines to Diffrow without any additional processing.
         *
         * @param val the value to set. Default: false.
         * @return builder with configured reportLinesUnWrapped parameter
         */
        fun reportLinesUnchanged(`val`: Boolean): Builder {
            reportLinesUnchanged = `val`
            return this
        }

        /**
         * Generator for Old-Text-Tags.
         *
         * @param tag the tag to set. Without angle brackets. Default: span.
         * @return builder with configured ignoreBlankLines parameter
         */
        fun oldTag(generator: (Boolean) -> String): Builder {
            this.oldTag = generator
            return this
        }

        /**
         * Generator for New-Text-Tags.
         *
         * @param generator
         * @return
         */
        fun newTag(generator: (Boolean) -> String): Builder {
            this.newTag = generator
            return this
        }

        /**
         * Set the column with of generated lines of original and revised texts.
         *
         * @param width the width to set. Making it < 0 doesn't have any sense. Default 80. @return builder with config
         * ured ignoreBlankLines parameter
         */
        fun columnWidth(width: Int): Builder {
            if (width >= 0) {
                columnWidth = width
            }
            return this
        }

        /**
         * Build the DiffRowGenerator. If some parameters is not set, the default values are used.
         *
         * @return the customized DiffRowGenerator
         */
        fun build(): DiffRowGenerator {
            return DiffRowGenerator(this)
        }

        /**
         * Merge the complete result within the original text. This makes sense for one line display.
         *
         * @param mergeOriginalRevised
         * @return
         */
        fun mergeOriginalRevised(mergeOriginalRevised: Boolean): Builder {
            this.mergeOriginalRevised = mergeOriginalRevised
            return this
        }

        /**
         * Per default each character is separatly processed. This variant introduces processing by word, which should
         * deliver no in word changes.
         */
        fun inlineDiffByWord(inlineDiffByWord: Boolean): Builder {
            inlineDiffSplitter = if (inlineDiffByWord) SPLITTER_BY_WORD else SPLITTER_BY_CHARACTER
            return this
        }

        fun inlineDiffBySplitter(inlineDiffSplitter: (String) -> List<String>): Builder {
            this.inlineDiffSplitter = inlineDiffSplitter
            return this
        }
    }

    companion object {

        val SPLIT_BY_WORD_PATTERN = Pattern.compile("\\s+|[,.\\[\\](){}/\\\\*+\\-#]")

        val IGNORE_WHITESPACE_EQUALIZER = { original: String, revised: String ->
            original.trim { it <= ' ' }.replace(
                "\\s+".toRegex(),
                " "
            ) == revised.trim { it <= ' ' }.replace("\\s+".toRegex(), " ")
        }

        val DEFAULT_EQUALIZER = { obj1: String, obj2: String -> obj1 == obj2 }

        /**
         * Splitting lines by character to achieve char by char diff checking.
         */
        val SPLITTER_BY_CHARACTER = { line: String ->
            val list = ArrayList<String>(line.length)
            for (character in line.toCharArray()) {
                list.add(character.toString())
            }
            list.toList()
        }

        /**
         * Splitting lines by word to achieve word by word diff checking.
         */
        val SPLITTER_BY_WORD =
            { line: String -> splitStringPreserveDelimiter(line, SPLIT_BY_WORD_PATTERN) }

        val WHITESPACE_PATTERN: Pattern = Pattern.compile("\\s+")

        fun create(): Builder {
            return Builder()
        }

        private fun adjustWhitespace(raw: String): String {
            return WHITESPACE_PATTERN.matcher(raw.trim { it <= ' ' }).replaceAll(" ")
        }

        internal fun splitStringPreserveDelimiter(
            str: String?,
            SPLIT_PATTERN: Pattern
        ): List<String> {
            val list = ArrayList<String>()
            if (str != null) {
                val matcher = SPLIT_PATTERN.matcher(str)
                var pos = 0
                while (matcher.find()) {
                    if (pos < matcher.start()) {
                        list.add(str.substring(pos, matcher.start()))
                    }
                    list.add(matcher.group())
                    pos = matcher.end()
                }
                if (pos < str.length) {
                    list.add(str.substring(pos))
                }
            }
            return list
        }

        /**
         * Wrap the elements in the sequence with the given tag
         *
         * @param startPosition the position from which tag should start. The counting start from a zero.
         * @param endPosition the position before which tag should should be closed.
         * @param tag the tag name without angle brackets, just a word
         * @param cssClass the optional css class
         */
        internal fun wrapInTag(
            sequence: MutableList<String>, startPosition: Int,
            endPosition: Int, tagGenerator: (Boolean) -> String
        ) {
            var endPos = endPosition

            while (endPos >= startPosition) {

                //search position for end tag
                while (endPos > startPosition) {
                    if ("\n" != sequence[endPos - 1]) {
                        break
                    }
                    endPos--
                }

                if (endPos == startPosition) {
                    break
                }

                sequence.add(endPos, tagGenerator.invoke(false))
                endPos--

                //search position for end tag
                while (endPos > startPosition) {
                    if ("\n" == sequence[endPos - 1]) {
                        break
                    }
                    endPos--
                }

                sequence.add(endPos, tagGenerator.invoke(true))
                endPos--
            }

            //        sequence.add(endPosition, tagGenerator.apply(false));
            //        sequence.add(startPosition, tagGenerator.apply(true));
        }
    }
}
