package io.github.q07mirror.shared;

import org.junit.Test;
import static org.junit.Assert.*;

public class FitTest {
    @Test public void portraitInLandscapeHasUntouchableSideBars() {
        Fit f=new Fit(1000,600,720,1280);
        assertEquals(331.25,f.left,.001); assertEquals(0,f.top,.001);
        assertFalse(f.contains(10,300)); assertTrue(f.contains(500,300));
        assertEquals(360,f.x(500)); assertEquals(640,f.y(300));
    }
    @Test public void landscapeInPortraitAndEdges() {
        Fit f=new Fit(600,1000,1280,720);
        assertEquals(331.25,f.top,.001); assertEquals(640,f.x(300)); assertEquals(360,f.y(500));
        assertFalse(f.contains(300,10)); assertEquals(0,f.y(-100)); assertEquals(719,f.y(2000));
    }
    @Test public void changingOrientationChangesCoordinateSpace() {
        Fit p=new Fit(1000,600,720,1280), l=new Fit(1000,600,1280,720);
        assertEquals(360,p.x(500)); assertEquals(640,l.x(500)); assertEquals(640,p.y(300)); assertEquals(360,l.y(300));
    }
    @Test public void rejectsZeroSizes() { assertThrows(IllegalArgumentException.class,()->new Fit(0,600,720,1280)); }
}
