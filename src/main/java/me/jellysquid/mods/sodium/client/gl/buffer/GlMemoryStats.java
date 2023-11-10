package me.jellysquid.mods.sodium.client.gl.buffer;

public class GlMemoryStats {
    public int memUsed;
    public int memAlloc;

    @Override
    public String toString() {
        return """
                Memory Used: %s
                Memory Alloc: %s
                """.formatted(this.memUsed >> 20, this.memAlloc >> 20);
    }
}