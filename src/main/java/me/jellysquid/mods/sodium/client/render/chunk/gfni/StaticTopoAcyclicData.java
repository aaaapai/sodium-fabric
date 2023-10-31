package me.jellysquid.mods.sodium.client.render.chunk.gfni;

import me.jellysquid.mods.sodium.client.gl.util.VertexRange;
import me.jellysquid.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import me.jellysquid.mods.sodium.client.util.NativeBuffer;
import net.minecraft.util.math.ChunkSectionPos;

public class StaticTopoAcyclicData extends MixedDirectionData {
    private static final int MAX_STATIC_TOPO_SORT_QUADS = 1000;

    StaticTopoAcyclicData(ChunkSectionPos sectionPos, NativeBuffer buffer, VertexRange range) {
        super(sectionPos, buffer, range);
    }

    @Override
    public SortType getSortType() {
        return SortType.STATIC_TOPO_ACYCLIC;
    }

    static StaticTopoAcyclicData fromMesh(BuiltSectionMeshParts translucentMesh,
            TQuad[] quads, ChunkSectionPos sectionPos, NativeBuffer buffer) {
        if (quads.length > MAX_STATIC_TOPO_SORT_QUADS) {
            return null;
        }

        VertexRange range = TranslucentData.getUnassignedVertexRange(translucentMesh);
        var indexBuffer = buffer.getDirectBuffer().asIntBuffer();

        if (!ComplexSorting.topoSortDepthFirstCyclic(indexBuffer, quads, null, null)) {
            return null;
        }

        return new StaticTopoAcyclicData(sectionPos, buffer, range);
    }
}
