package io.github.brooswitminecraft.dynamicskateboards;

import net.minecraft.util.StringRepresentable;

/** Which corner of a curved piece's leading (facing) edge carries the turn. */
public enum ChamferSide implements StringRepresentable {
    LEFT("left"),
    RIGHT("right");

    private final String name;

    ChamferSide(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
