package com.flaketax;

import com.flaketax.pact.Split;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SplitTest {
    @Test void keepsRemainder() {
        assertEquals(List.of(101L, 100L, 100L), Split.evenly(301, 3));
    }
    @Test void sumsExactly() {
        List<Long> paid = Split.evenly(1000, 7);
        assertEquals(1000L, paid.stream().mapToLong(Long::longValue).sum());
        assertEquals(7, paid.size());
    }
    @Test void rejectsBadCount() {
        assertThrows(IllegalArgumentException.class, () -> Split.evenly(100, 0));
    }
}