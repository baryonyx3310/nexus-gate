package com.nexusgate.client.gui;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlyphFactoryTest {
    private static String signature(GlyphFactory.Glyph g) {
        StringBuilder sb = new StringBuilder();
        for (int[] s : g.strokes()) sb.append(s[0]).append('-').append(s[1]).append(',');
        return sb.append(g.notches()).append('/').append(g.dots()).toString();
    }

    @Test
    void sameIdGivesSameGlyph() {
        String id = "3f1c2b9e-1111-4c2a-9f00-123456789abc";
        assertEquals(signature(GlyphFactory.of(id)), signature(GlyphFactory.of(id)));
    }

    @Test
    void glyphsAreWellFormedAndVaried() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            GlyphFactory.Glyph g = GlyphFactory.of(new UUID(i * 7919L, i * 104729L).toString());
            assertTrue(g.strokes().size() >= 3 && g.strokes().size() <= 5);
            assertTrue(g.notches() > 0 && g.notches() < 64);
            assertTrue(g.dots() >= 1 && g.dots() <= 3);
            Set<String> strokes = new HashSet<>();
            for (int[] s : g.strokes()) {
                assertTrue(s[0] >= 0 && s[0] < 7 && s[1] >= 0 && s[1] < 7);
                assertTrue(strokes.add(s[0] + "-" + s[1]), "no duplicate strokes");
            }
            seen.add(signature(g));
        }
        assertTrue(seen.size() > 150, "stations should rarely share a sigil");
    }
}
