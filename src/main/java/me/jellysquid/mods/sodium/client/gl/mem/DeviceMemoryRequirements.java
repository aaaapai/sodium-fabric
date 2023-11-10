package me.jellysquid.mods.sodium.client.gl.mem;

//TODO: Bit-flags for supporting available flags
public class DeviceMemoryRequirements {
    public final int memSize;
    public final int memAlign;

    public DeviceMemoryRequirements(int memSize, int memAlign) {
        this.memSize = memSize;
        this.memAlign = memAlign;
    }
}