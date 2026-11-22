package net.fraeric.elnr.client;

/**
 * Exact port of the geometry of the original Electrical Age cable renderer
 * ({@code mods.eln.cable.CableRender.drawCable} and {@code drawNode}).
 *
 * <p>The class knows nothing about Minecraft: it only produces quads in the
 * <em>local cable space</em> of the original and hands them to a {@link QuadSink}.</p>
 *
 * <p>Local cable space (same as the original):</p>
 * <ul>
 *   <li>{@code x} is the depth above the mounting surface, {@code 0..height};</li>
 *   <li>{@code y} and {@code z} lie in the mounting plane, the centre of the block is {@code 0,0};</li>
 *   <li>the four arms are Left ({@code -z}), Right ({@code +z}), Down ({@code -y}) and Up ({@code +y}).</li>
 * </ul>
 *
 * <p>Texture coordinates are normalised ({@code 1.0} = the whole texture) and use the
 * original layout: the cable itself samples around {@code (0.25, 0.5)}, the
 * knobs ("bobíky") at junctions sample around {@code (0.75, 0.5)}. One block of
 * cable length is exactly one texture height, so the texture repeats along the cable.</p>
 */
final class CableGeometry {

    static final int LEFT = 0;
    static final int RIGHT = 1;
    static final int DOWN = 2;
    static final int UP = 3;

    /** The arm simply runs to the block edge. */
    static final int STANDARD = 0;
    /** The arm is shortened, a neighbouring cable in the same block covers the corner. */
    static final int INTERNAL = 1;
    /** The arm is lengthened past the block edge to close a corner. */
    static final int ETEND = 2;

    /** Receives finished quads. A vertex is {@code {x, y, z, u, v}} in local cable space. */
    interface QuadSink {
        void quad(boolean knob, float nx, float ny, float nz,
                  float[] v0, float[] v1, float[] v2, float[] v3);
    }

    private CableGeometry() {
    }

    private static float[] v(float x, float y, float z, float u, float tv) {
        return new float[]{x, y, z, u, tv};
    }

    /**
     * Port of {@code CableRender.drawCable(..., deltaStart = widthDiv2 / 2, drawBottom = false)}.
     *
     * @param connected which of the four arms are connected (Left, Right, Down, Up)
     * @param method    per arm {@link #STANDARD}, {@link #INTERNAL} or {@link #ETEND}
     * @param endAtPx   per arm size of the Internal/Etend correction, in texture pixels
     */
    static void drawCable(QuadSink sink, float widthPx, float heightPx,
                          boolean[] connected, int[] method, float[] endAtPx) {
        float widthDiv2 = widthPx / 16.0F / 2.0F;
        float height = heightPx / 16.0F;
        float deltaStart = widthDiv2 / 2.0F;

        float endLeft = -deltaStart;
        float endRight = deltaStart;
        float endUp = deltaStart;
        float endDown = -deltaStart;
        // The original reads these from CableRenderType.startAt, which is always zero.
        float startLeft = 0.0F;
        float startRight = 0.0F;
        float startUp = 0.0F;
        float startDown = 0.0F;

        boolean any = connected[LEFT] || connected[RIGHT] || connected[DOWN] || connected[UP];
        if (!any) {
            // Isolated piece: a bigger plus shaped blob.
            float e = widthDiv2 + 3.0F / 16.0F;
            endLeft = -e;
            endRight = e;
            endDown = -e;
            endUp = e;
        } else {
            if (connected[LEFT]) {
                endLeft = -0.5F;
            }
            if (connected[RIGHT]) {
                endRight = 0.5F;
            }
            if (connected[DOWN]) {
                endDown = -0.5F;
            }
            if (connected[UP]) {
                endUp = 0.5F;
            }
        }

        switch (method[LEFT]) {
            case INTERNAL -> endLeft += endAtPx[LEFT] / 16.0F;
            case ETEND -> endLeft -= endAtPx[LEFT] / 16.0F;
            default -> {
            }
        }
        switch (method[RIGHT]) {
            case INTERNAL -> endRight -= endAtPx[RIGHT] / 16.0F;
            case ETEND -> endRight += endAtPx[RIGHT] / 16.0F;
            default -> {
            }
        }
        switch (method[DOWN]) {
            case INTERNAL -> endDown += endAtPx[DOWN] / 16.0F;
            case ETEND -> endDown -= endAtPx[DOWN] / 16.0F;
            default -> {
            }
        }
        switch (method[UP]) {
            case INTERNAL -> endUp -= endAtPx[UP] / 16.0F;
            case ETEND -> endUp += endAtPx[UP] / 16.0F;
            default -> {
            }
        }

        final float w = widthDiv2;
        final float h = height;
        final float tx = 0.25F;
        final float ty = 0.5F;

        if (endLeft < startLeft) {
            // Left arm: two side walls and the top, then the end cap.
            float[] p0 = v(0, w, endLeft, tx + (w + h) * 0.5F, ty + endLeft);
            float[] p1 = v(0, w, startLeft, tx + (w + h) * 0.5F, ty + startLeft);
            float[] p2 = v(h, w, endLeft, tx + w * 0.5F, ty + endLeft);
            float[] p3 = v(h, w, startLeft, tx + w * 0.5F, ty + startLeft);
            float[] p4 = v(h, -w, endLeft, tx - w * 0.5F, ty + endLeft);
            float[] p5 = v(h, -w, startLeft, tx - w * 0.5F, ty + startLeft);
            float[] p6 = v(0, -w, endLeft, tx - w * 0.5F - h, ty + endLeft);
            float[] p7 = v(0, -w, startLeft, tx - w * 0.5F - h, ty + startLeft);
            sink.quad(false, 0, 1, 0, p0, p1, p3, p2);
            sink.quad(false, 1, 0, 0, p2, p3, p5, p4);
            sink.quad(false, 0, -1, 0, p4, p5, p7, p6);
            sink.quad(false, 0, 0, -1,
                    v(0, -w, endLeft, tx - w * 0.5F, ty + endLeft - h),
                    v(0, w, endLeft, tx + w * 0.5F, ty + endLeft - h),
                    v(h, w, endLeft, tx + w * 0.5F, ty + endLeft),
                    v(h, -w, endLeft, tx - w * 0.5F, ty + endLeft));
            sink.quad(false, -1, 0, 0,
                    v(0, w, endLeft, tx + w * 0.5F, ty + endLeft),
                    v(0, -w, endLeft, tx - w * 0.5F, ty + endLeft),
                    v(0, -w, startLeft, tx - w * 0.5F, ty + startLeft),
                    v(0, w, startLeft, tx + w * 0.5F, ty + startLeft));
        }
        if (endRight > startRight) {
            float[] p0 = v(0, w, startRight, tx + (w + h) * 0.5F, ty + startRight);
            float[] p1 = v(0, w, endRight, tx + (w + h) * 0.5F, ty + endRight);
            float[] p2 = v(h, w, startRight, tx + w * 0.5F, ty + startRight);
            float[] p3 = v(h, w, endRight, tx + w * 0.5F, ty + endRight);
            float[] p4 = v(h, -w, startRight, tx - w * 0.5F, ty + startRight);
            float[] p5 = v(h, -w, endRight, tx - w * 0.5F, ty + endRight);
            float[] p6 = v(0, -w, startRight, tx - w * 0.5F - h, ty + startRight);
            float[] p7 = v(0, -w, endRight, tx - w * 0.5F - h, ty + endRight);
            sink.quad(false, 0, 1, 0, p0, p1, p3, p2);
            sink.quad(false, 1, 0, 0, p2, p3, p5, p4);
            sink.quad(false, 0, -1, 0, p4, p5, p7, p6);
            sink.quad(false, 0, 0, 1,
                    v(h, -w, endRight, tx - w * 0.5F, ty + endRight),
                    v(h, w, endRight, tx + w * 0.5F, ty + endRight),
                    v(0, w, endRight, tx + w * 0.5F, ty + endRight + h),
                    v(0, -w, endRight, tx - w * 0.5F, ty + endRight + h));
            sink.quad(false, -1, 0, 0,
                    v(0, w, startRight, tx + w * 0.5F, ty + startRight),
                    v(0, -w, startRight, tx - w * 0.5F, ty + startRight),
                    v(0, -w, endRight, tx - w * 0.5F, ty + endRight),
                    v(0, w, endRight, tx + w * 0.5F, ty + endRight));
        }
        if (endDown < startDown) {
            float[] p0 = v(0, endDown, -w, tx - (w - h) * 0.5F, ty + endDown);
            float[] p1 = v(0, startDown, -w, tx - (w - h) * 0.5F, ty + startDown);
            float[] p2 = v(h, endDown, -w, tx - w * 0.5F, ty + endDown);
            float[] p3 = v(h, startDown, -w, tx - w * 0.5F, ty + startDown);
            float[] p4 = v(h, endDown, w, tx + w * 0.5F, ty + endDown);
            float[] p5 = v(h, startDown, w, tx + w * 0.5F, ty + startDown);
            float[] p6 = v(0, endDown, w, tx + (w + h) * 0.5F, ty + endDown);
            float[] p7 = v(0, startDown, w, tx + (w + h) * 0.5F, ty + startDown);
            sink.quad(false, 0, 0, -1, p0, p1, p3, p2);
            sink.quad(false, 1, 0, 0, p2, p3, p5, p4);
            sink.quad(false, 0, 0, 1, p4, p5, p7, p6);
            sink.quad(false, 0, -1, 0,
                    v(h, endDown, -w, tx - w * 0.5F, ty + endDown),
                    v(h, endDown, w, tx + w * 0.5F, ty + endDown),
                    v(0, endDown, w, tx + w * 0.5F, ty + endDown - h),
                    v(0, endDown, -w, tx - w * 0.5F, ty + endDown - h));
            sink.quad(false, -1, 0, 0,
                    v(0, endDown, w, tx + w * 0.5F, ty + endDown),
                    v(0, endDown, -w, tx - w * 0.5F, ty + endDown),
                    v(0, startDown, -w, tx - w * 0.5F, ty + startDown),
                    v(0, startDown, w, tx + w * 0.5F, ty + startDown));
        }
        if (endUp > startUp) {
            float[] p0 = v(0, startUp, -w, tx - (w - h) * 0.5F, ty + startUp);
            float[] p1 = v(0, endUp, -w, tx - (w - h) * 0.5F, ty + endUp);
            float[] p2 = v(h, startUp, -w, tx - w * 0.5F, ty + startUp);
            float[] p3 = v(h, endUp, -w, tx - w * 0.5F, ty + endUp);
            float[] p4 = v(h, startUp, w, tx + w * 0.5F, ty + startUp);
            float[] p5 = v(h, endUp, w, tx + w * 0.5F, ty + endUp);
            float[] p6 = v(0, startUp, w, tx + (w + h) * 0.5F, ty + startUp);
            float[] p7 = v(0, endUp, w, tx + (w + h) * 0.5F, ty + endUp);
            sink.quad(false, 0, 0, -1, p0, p1, p3, p2);
            sink.quad(false, 1, 0, 0, p2, p3, p5, p4);
            sink.quad(false, 0, 0, 1, p4, p5, p7, p6);
            sink.quad(false, 0, 1, 0,
                    v(0, endUp, -w, tx - w * 0.5F, ty + endUp + h),
                    v(0, endUp, w, tx + w * 0.5F, ty + endUp + h),
                    v(h, endUp, w, tx + w * 0.5F, ty + endUp),
                    v(h, endUp, -w, tx - w * 0.5F, ty + endUp));
            sink.quad(false, -1, 0, 0,
                    v(0, startUp, w, tx + w * 0.5F, ty + startUp),
                    v(0, startUp, -w, tx - w * 0.5F, ty + startUp),
                    v(0, endUp, -w, tx - w * 0.5F, ty + endUp),
                    v(0, endUp, w, tx + w * 0.5F, ty + endUp));
        }
    }

    /**
     * @return whether the original draws its knob for this connection pattern: on an
     * isolated piece, on a dead end and on every corner, T junction or crossing.
     */
    static boolean hasKnob(boolean[] connected) {
        int mask = (connected[LEFT] ? 1 : 0) | (connected[RIGHT] ? 2 : 0)
                | (connected[DOWN] ? 4 : 0) | (connected[UP] ? 8 : 0);
        return mask == 0
                || ((connected[LEFT] || connected[RIGHT]) && (connected[DOWN] || connected[UP]))
                || mask == 1 || mask == 2 || mask == 4 || mask == 8;
    }

    /**
     * Port of {@code CableRender.drawNode}: the slightly bigger, lighter knob at
     * junctions and cable ends. It samples the right half of the cable texture.
     */
    static void drawNode(QuadSink sink, float widthPx, float heightPx, boolean[] connected) {
        if (!hasKnob(connected)) {
            return;
        }
        float cableWidthDiv2 = widthPx / 16.0F / 2.0F;
        float cableHeight = heightPx / 16.0F;
        float w = cableWidthDiv2 + 1.0F / 16.0F;
        float h = cableHeight + 1.0F / 16.0F;
        final float tx = 0.75F;
        final float ty = 0.5F;

        float[] p0 = v(0, w, -w, tx + (w + cableHeight + 1.0F / 16.0F) * 0.5F, ty - w);
        float[] p1 = v(0, w, w, tx + (w + cableHeight + 1.0F / 16.0F) * 0.5F, ty + w);
        float[] p2 = v(h, w, -w, tx + w * 0.5F, ty - w);
        float[] p3 = v(h, w, w, tx + w * 0.5F, ty + w);
        float[] p4 = v(h, -w, -w, tx - w * 0.5F, ty - w);
        float[] p5 = v(h, -w, w, tx - w * 0.5F, ty + w);
        float[] p6 = v(0, -w, -w, tx - (w + cableHeight + 1.0F / 16.0F) * 0.5F, ty - w);
        float[] p7 = v(0, -w, w, tx - (w + cableHeight + 1.0F / 16.0F) * 0.5F, ty + w);
        sink.quad(true, 0, 1, 0, p0, p1, p3, p2);
        sink.quad(true, 1, 0, 0, p2, p3, p5, p4);
        sink.quad(true, 0, -1, 0, p4, p5, p7, p6);

        sink.quad(true, 0, 0, -1,
                v(0, -w, -w, tx - w * 0.5F, ty - w - cableHeight - 1.0F / 16.0F),
                v(0, w, -w, tx + w * 0.5F, ty - w - cableHeight - 1.0F / 16.0F),
                v(h, w, -w, tx + w * 0.5F, ty - w),
                v(h, -w, -w, tx - w * 0.5F, ty - w));
        sink.quad(true, -1, 0, 0,
                v(0, w, -w, tx + w * 0.5F, ty - w),
                v(0, -w, -w, tx - w * 0.5F, ty - w),
                v(0, -w, w, tx - w * 0.5F, ty + w),
                v(0, w, w, tx + w * 0.5F, ty + w));
        sink.quad(true, 0, 0, 1,
                v(h, -w, w, tx - w * 0.5F, ty + w),
                v(h, w, w, tx + w * 0.5F, ty + w),
                v(0, w, w, tx + w * 0.5F, ty + w + cableHeight + 1.0F / 16.0F),
                v(0, -w, w, tx - w * 0.5F, ty + w + cableHeight + 1.0F / 16.0F));
    }
}
