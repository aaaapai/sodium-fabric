package me.jellysquid.mods.sodium.client.gl.buffer;

import me.jellysquid.mods.sodium.client.gl.GlObject;
import me.jellysquid.mods.sodium.client.gl.util.EnumBitField;

public class GlBuffer extends GlObject {
    private GlBufferMapping activeMapping;

    private final int size;
    private final EnumBitField<GlBufferFlags> flags;

    public GlBuffer(int handle, int size, EnumBitField<GlBufferFlags> flags) {
        this.setHandle(handle);
        this.size = size;
        this.flags = flags;
    }

    public int getSize() {
        return this.size;
    }

    public EnumBitField<GlBufferFlags> getFlags() {
        return this.flags;
    }

    public GlBufferMapping getActiveMapping() {
        return this.activeMapping;
    }

    public void setActiveMapping(GlBufferMapping mapping) {
        this.activeMapping = mapping;
    }
}