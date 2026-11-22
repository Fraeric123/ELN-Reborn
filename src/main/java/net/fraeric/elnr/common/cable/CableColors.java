package net.fraeric.elnr.common.cable;

/** Insulation colours of the cables (the 16 dye colours). */
public final class CableColors {

    public static final int COUNT = 16;
    public static final String[] NAMES = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    public static final int[] RGB = {0xf9fffe, 0xf9801d, 0xc74ebd, 0x3ab3da, 0xfed83d, 0x80c71f, 0xf38baa, 0x7a7a7a, 0xbdbdb5, 0x169c9c, 0x8932b8, 0x3c44aa, 0x835432, 0x5e7c16, 0xb02e26, 0x555555};

    private CableColors() {
    }
}
