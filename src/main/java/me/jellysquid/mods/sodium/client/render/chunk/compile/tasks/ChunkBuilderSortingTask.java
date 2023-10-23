package me.jellysquid.mods.sodium.client.render.chunk.compile.tasks;

import org.joml.Vector3dc;

import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkSortOutput;
import me.jellysquid.mods.sodium.client.util.task.CancellationToken;

public class ChunkBuilderSortingTask extends ChunkBuilderTask<ChunkSortOutput> {
    public ChunkBuilderSortingTask(RenderSection render, int frame, Vector3dc absoluteCameraPos) {
        super(render, frame, absoluteCameraPos);
    }

    @Override
    public ChunkSortOutput execute(ChunkBuildContext context, CancellationToken cancellationToken) {
        this.render.getTranslucentData().sortOnTrigger(this.cameraPos);
        return new ChunkSortOutput(this.render, this.submitTime, this.render.getTranslucentData());
    }
}
