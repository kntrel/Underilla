package com.kntrel.mc.underilla.config.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.kntrel.mc.underilla.config.dsl.Slice;
import org.junit.jupiter.api.Test;

class SliceTest {

    @Test
    void exposesOnlyItsRangeWithRelativeIndices() {
        Slice slice = Slice.of("hidden:visible:tail", 7, 14);

        assertEquals(7, slice.length());
        assertEquals('v', slice.charAt(0));
        assertEquals('e', slice.charAt(6));
        assertEquals("visible", slice.toString());
        assertThrows(IndexOutOfBoundsException.class, () -> slice.charAt(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> slice.charAt(7));
    }

    @Test
    void supportsWholeStringsSuffixesAndEmptyViews() {
        assertEquals("source", Slice.of("source").toString());
        assertEquals("rce", Slice.of("source", 3).toString());
        assertEquals("", Slice.of("source", 6).toString());
        assertEquals("", Slice.of("source", 2, 2).toString());
        assertEquals(0, Slice.of("").length());
        assertThrows(IndexOutOfBoundsException.class, () -> Slice.of("").charAt(0));
    }

    @Test
    void nestedViewsCannotEscapeTheParentRange() {
        Slice parent = Slice.of("prefix:visible:suffix", 7, 14);
        Slice child = parent.subSlice(1, 6);

        assertEquals("isibl", child.toString());
        assertEquals("sib", child.subSequence(1, 4).toString());
        assertEquals("", child.subSlice(5, 5).toString());
        assertEquals("visible", parent.toString());
        assertThrows(IndexOutOfBoundsException.class, () -> parent.subSlice(-1, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> parent.subSequence(0, 8));
        assertThrows(IndexOutOfBoundsException.class, () -> parent.subSlice(4, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> child.charAt(5));
    }

    @Test
    void iterationHasIndependentCursorsAndIsReadOnly() {
        Slice slice = Slice.of("hidden:abc:tail", 7, 10);
        Iterator<Character> first = slice.iterator();
        Iterator<Character> second = slice.iterator();

        assertEquals('a', first.next());
        assertEquals('b', first.next());
        assertEquals('a', second.next());
        assertEquals('a', slice.charAt(0));
        assertEquals('c', first.next());
        assertFalse(first.hasNext());
        assertThrows(NoSuchElementException.class, first::next);
        assertThrows(UnsupportedOperationException.class, second::remove);

        StringBuilder result = new StringBuilder();
        for (char character : slice) {
            result.append(character);
        }
        assertEquals("abc", result.toString());
        assertFalse(Slice.of("").iterator().hasNext());
        assertThrows(NoSuchElementException.class, () -> Slice.of("").iterator().next());
    }

    @Test
    void usesStringIndicesForSupplementaryCharacters() {
        Slice slice = Slice.of("prefix:\uD83D\uDE00:tail", 7, 9);

        assertEquals(2, slice.length());
        assertEquals('\uD83D', slice.charAt(0));
        assertEquals('\uDE00', slice.charAt(1));
        assertEquals("\uD83D\uDE00", slice.toString());
    }

    @Test
    void rejectsInvalidSourcesAndRanges() {
        assertThrows(NullPointerException.class, () -> Slice.of(null));
        assertThrows(NullPointerException.class, () -> Slice.of(null, 0));
        assertThrows(NullPointerException.class, () -> Slice.of(null, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> Slice.of("abc", -1));
        assertThrows(IndexOutOfBoundsException.class, () -> Slice.of("abc", 4));
        assertThrows(IndexOutOfBoundsException.class, () -> Slice.of("abc", 0, 4));
        assertThrows(IndexOutOfBoundsException.class, () -> Slice.of("abc", 2, 1));
    }
}
