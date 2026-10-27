package com.flaketax.pact;

import java.util.ArrayList;
import java.util.List;

public final class Split {
    private Split() {}

    public static List<Long> evenly(long total, int people) {
        if (total < 0 || people <= 0) throw new IllegalArgumentException("Invalid split");
        long each = total / people;
        long extra = total % people;
        List<Long> shares = new ArrayList<>();
        for (int i = 0; i < people; i++) {
            shares.add(each + (i < extra ? 1 : 0));
        }
        return shares;
    }
}