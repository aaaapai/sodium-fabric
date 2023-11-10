package me.jellysquid.mods.sodium.client.gl.arena.staging;

import me.jellysquid.mods.sodium.client.gl.buffer.GlBuffer;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBufferFlags;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBufferUsage;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.util.EnumBitField;
import org.lwjgl.opengl.GL20C;

import java.nio.ByteBuffer;
import java.util.Set;

public class FallbackStagingBuffer implements StagingBuffer {
    private final GlBuffer fallbackBufferObject;

    public FallbackStagingBuffer(CommandList commandList, int size) {
        this.fallbackBufferObject = commandList.createBuffer(size, EnumBitField.of(GlBufferFlags.READ, GlBufferFlags.WRITE));
    }

    @Override
    public void enqueueCopy(CommandList commandList, ByteBuffer data, GlBuffer dst, long writeOffset) {
        commandList.uploadData(this.fallbackBufferObject, data, GlBufferUsage.STREAM_COPY);
        commandList.copyBufferSubData(this.fallbackBufferObject, dst, 0, writeOffset, data.remaining());
    }

    @Override
    public void flush(CommandList commandList) {
        commandList.allocateStorage(this.fallbackBufferObject, 0L, GlBufferUsage.STREAM_COPY);
    }

    @Override
    public void delete(CommandList commandList) {
        commandList.deleteBuffer(this.fallbackBufferObject);
    }

    @Override
    public void flip() {

    }

    @Override
    public String toString() {
        return "Fallback";
    }
}