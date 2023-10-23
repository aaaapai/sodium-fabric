package me.jellysquid.mods.sodium.client.render.chunk.gfni;

import java.nio.IntBuffer;

import org.joml.Vector3fc;

import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import me.jellysquid.mods.sodium.client.gl.util.VertexRange;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import me.jellysquid.mods.sodium.client.util.NativeBuffer;
import net.minecraft.util.math.ChunkSectionPos;

public class DynamicData extends MixedDirectionData {
    private final TQuad[] quads;
    boolean GFNITrigger = true;
    boolean angleTrigger = false;
    boolean turnAngleTriggerOn = false;
    boolean turnGFNITriggerOff = false;
    private int consecutiveTopoSortFailures = 0;
    private boolean pendingTriggerIsAngle;
    private TranslucentGeometryCollector collector;
    private Object2ReferenceOpenHashMap<Vector3fc, double[]> distancesByNormal;

    private static final int MAX_TOPO_SORT_QUADS = 1000;
    private static final int MAX_TOPO_SORT_TIME_NS = 1_000_000;
    private static final int MAX_FAILING_TOPO_SORT_TIME_NS = 750_000;
    private static final int MAX_TOPO_SORT_PATIENT_TIME_NS = 250_000;

    DynamicData(ChunkSectionPos sectionPos,
            NativeBuffer buffer, VertexRange range, TQuad[] quads,
            TranslucentGeometryCollector collector,
            Object2ReferenceOpenHashMap<Vector3fc, double[]> distancesByNormal) {
        super(sectionPos, buffer, range);
        this.quads = quads;
        this.collector = collector;
        this.distancesByNormal = distancesByNormal;
    }

    @Override
    public SortType getSortType() {
        return SortType.DYNAMIC_ALL;
    }

    @Override
    public boolean prepareTrigger(boolean isAngleTrigger) {
        // if an angle trigger was scheduled but isn't needed, return true to signal
        // removal from angle triggering
        if (isAngleTrigger && !this.angleTrigger) {
            return true;
        }

        this.pendingTriggerIsAngle = isAngleTrigger;
        return false;
    }

    @Override
    public void sortOnTrigger(Vector3fc cameraPos) {
        this.sort(cameraPos, this.pendingTriggerIsAngle);
    }

    private void turnGFNITriggerOff() {
        if (this.GFNITrigger) {
            this.GFNITrigger = false;
            this.turnGFNITriggerOff = true;
        }
    }

    private void turnAngleTriggerOn() {
        if (!this.angleTrigger) {
            this.angleTrigger = true;
            this.turnAngleTriggerOn = true;
        }
    }

    private void sort(Vector3fc cameraPos, boolean isAngleTrigger) {
        // uses a topo sort or a distance sort depending on what is enabled
        IntBuffer indexBuffer = this.buffer.getDirectBuffer().asIntBuffer();

        if (this.quads.length > MAX_TOPO_SORT_QUADS) {
            turnGFNITriggerOff();
            turnAngleTriggerOn();
        }

        if (this.GFNITrigger && !isAngleTrigger) {
            var sortStart = System.nanoTime();

            var result = ComplexSorting.topoSortDepthFirstCyclic(
                    indexBuffer, this.quads, this.distancesByNormal, cameraPos);

            var sortTime = System.nanoTime() - sortStart;

            // if we've already failed, there's reduced patience for sorting since the
            // probability of failure and wasted compute time is higher
            if (sortTime > (this.consecutiveTopoSortFailures > 0
                    ? MAX_FAILING_TOPO_SORT_TIME_NS
                    : MAX_TOPO_SORT_TIME_NS)) {
                turnGFNITriggerOff();
                turnAngleTriggerOn();
            }

            if (result) {
                // disable distance sorting because topo sort seems to be possible.
                // removal from angle triggering happens automatically by setting this to false.
                this.angleTrigger = false;
                this.consecutiveTopoSortFailures = 0;
                return;
            } else {
                // topo sort failure, the topo sort algorithm doesn't work on all cases

                // gives up after a certain number of failures. it keeps GFNI triggering with
                // topo sort on while the angle triggering is also active to maybe get a topo
                // sort success from a different angle.
                this.consecutiveTopoSortFailures++;
                if (this.consecutiveTopoSortFailures >= (sortTime <= MAX_TOPO_SORT_PATIENT_TIME_NS ? 5 : 2)) {
                    turnGFNITriggerOff();
                }
                turnAngleTriggerOn();
            }
        }
        if (this.angleTrigger) {
            indexBuffer.rewind();
            ComplexSorting.distanceSortDirect(indexBuffer, this.quads, cameraPos);
            return;
        }
    }

    public void clearTriggerChanges() {
        this.turnAngleTriggerOn = false;
        this.turnGFNITriggerOff = false;
    }

    public boolean hasTriggerChanges() {
        return this.turnAngleTriggerOn || this.turnGFNITriggerOff;
    }

    TranslucentGeometryCollector getCollector() {
        return this.collector;
    }

    void deleteCollector() {
        this.collector = null;
    }

    static DynamicData fromMesh(BuiltSectionMeshParts translucentMesh,
            Vector3fc cameraPos, TQuad[] quads, ChunkSectionPos sectionPos, TranslucentGeometryCollector collector) {
        // prepare accumulation groups for GFNI integration and copy
        var size = 0;
        if (collector.axisAlignedDistances != null) {
            size += Integer.bitCount(collector.alignedNormalBitmap);
        }
        if (collector.unalignedDistances != null) {
            size += collector.unalignedDistances.size();
        }
        var distancesByNormal = new Object2ReferenceOpenHashMap<Vector3fc, double[]>(size);
        if (collector.axisAlignedDistances != null) {
            for (int direction = 0; direction < ModelQuadFacing.DIRECTIONS; direction++) {
                var accGroup = collector.axisAlignedDistances[direction];
                if (accGroup != null) {
                    accGroup.prepareIntegration();
                    distancesByNormal.put(accGroup.normal, accGroup.facePlaneDistances);
                }
            }
        }
        if (collector.unalignedDistances != null) {
            for (var accGroup : collector.unalignedDistances.values()) {
                // TODO: get rid of collector key and just use the normal vector's hash code
                accGroup.prepareIntegration();
                distancesByNormal.put(accGroup.normal, accGroup.facePlaneDistances);
            }
        }

        VertexRange range = TranslucentData.getUnassignedVertexRange(translucentMesh);
        var buffer = new NativeBuffer(TranslucentData.quadCountToIndexBytes(quads.length));

        var dynamicData = new DynamicData(sectionPos, buffer, range, quads, collector, distancesByNormal);

        dynamicData.sort(cameraPos, false);

        return dynamicData;
    }
}
