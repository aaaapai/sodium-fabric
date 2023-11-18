package me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting;

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
    boolean directTrigger = false;
    boolean turnGFNITriggerOff = false;
    boolean turnDirectTriggerOn = false;
    boolean turnDirectTriggerOff = false;
    double directTriggerKey = -1;
    private int consecutiveTopoSortFailures = 0;
    private boolean pendingTriggerIsAngle;
    private TranslucentGeometryCollector collector;
    private Object2ReferenceOpenHashMap<Vector3fc, double[]> distancesByNormal;
    private int[] distanceSortIndexes;

    private static final int MAX_TOPO_SORT_QUADS = 1000;
    private static final int MAX_TOPO_SORT_TIME_NS = 1_000_000;
    private static final int MAX_FAILING_TOPO_SORT_TIME_NS = 750_000;
    private static final int MAX_TOPO_SORT_PATIENT_TIME_NS = 250_000;
    private static final int PATIENT_TOPO_ATTEMPTS = 5;
    private static final int REGULAR_TOPO_ATTEMPTS = 2;

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
    public void prepareTrigger(boolean isAngleTrigger) {
        this.pendingTriggerIsAngle = isAngleTrigger;
    }

    @Override
    public void sortOnTrigger(Vector3fc cameraPos) {
        this.sort(cameraPos, this.pendingTriggerIsAngle, false);
    }

    private void turnGFNITriggerOff() {
        if (this.GFNITrigger) {
            this.GFNITrigger = false;
            this.turnGFNITriggerOff = true;
        }
    }

    private void turnDirectTriggerOn() {
        if (!this.directTrigger) {
            this.directTrigger = true;
            this.turnDirectTriggerOn = true;
        }
    }

    private void turnDirectTriggerOff() {
        if (this.directTrigger) {
            this.directTrigger = false;
            this.turnDirectTriggerOff = true;
        }
        this.distanceSortIndexes = null;
    }

    private static int getAttemptsForTime(long ns) {
        return ns <= MAX_TOPO_SORT_PATIENT_TIME_NS ? PATIENT_TOPO_ATTEMPTS : REGULAR_TOPO_ATTEMPTS;
    }

    private void sort(Vector3fc cameraPos, boolean isAngleTrigger, boolean initial) {
        // mark as not being reused to ensure the updated buffer is actually uploaded
        this.unsetReuseUploadedData();

        // uses a topo sort or a distance sort depending on what is enabled
        IntBuffer indexBuffer = this.getBuffer().getDirectBuffer().asIntBuffer();

        if (this.quads.length > MAX_TOPO_SORT_QUADS) {
            this.turnGFNITriggerOff();
            this.turnDirectTriggerOn();
        }

        if (this.GFNITrigger && !isAngleTrigger) {
            var sortStart = initial ? 0 : System.nanoTime();

            var result = ComplexSorting.topoSortDepthFirstCyclic(
                    indexBuffer, this.quads, this.distancesByNormal, cameraPos);

            var sortTime = initial ? 0 : System.nanoTime() - sortStart;

            // if we've already failed, there's reduced patience for sorting since the
            // probability of failure and wasted compute time is higher. Initial sorting is
            // often very slow when the cpu is loaded and the JIT isn't ready yet, so it's
            // ignored here.
            if (!initial && sortTime > (this.consecutiveTopoSortFailures > 0
                    ? MAX_FAILING_TOPO_SORT_TIME_NS
                    : MAX_TOPO_SORT_TIME_NS)) {
                this.turnGFNITriggerOff();
                this.turnDirectTriggerOn();
                System.out.println("topo sort took too long");
            } else if (result) {
                // disable distance sorting because topo sort seems to be possible.
                this.turnDirectTriggerOff();
                this.consecutiveTopoSortFailures = 0;
                return;
            } else {
                // topo sort failure, the topo sort algorithm doesn't work on all cases

                // gives up after a certain number of failures. it keeps GFNI triggering with
                // topo sort on while the angle triggering is also active to maybe get a topo
                // sort success from a different angle.
                this.consecutiveTopoSortFailures++;
                if (this.consecutiveTopoSortFailures >= getAttemptsForTime(sortTime)) {
                    this.turnGFNITriggerOff();
                }
                this.turnDirectTriggerOn();
            }
        }

        if (this.directTrigger) {
            indexBuffer.rewind();
            this.distanceSortIndexes = ComplexSorting.distanceSortDirect(
                    this.distanceSortIndexes, indexBuffer, this.quads, cameraPos);
            return;
        }
    }

    public void clearTriggerChanges() {
        this.turnGFNITriggerOff = false;
        this.turnDirectTriggerOn = false;
        this.turnDirectTriggerOff = false;
    }

    public boolean hasTriggerChanges() {
        return this.turnGFNITriggerOff || this.turnDirectTriggerOn || this.turnDirectTriggerOff;
    }

    TranslucentGeometryCollector getCollector() {
        return this.collector;
    }

    void deleteCollector() {
        this.collector = null;
    }

    static DynamicData fromMesh(BuiltSectionMeshParts translucentMesh,
            Vector3fc cameraPos, TQuad[] quads, ChunkSectionPos sectionPos, TranslucentGeometryCollector collector,
            NativeBuffer buffer) {
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
        if (buffer == null) {
            buffer = PresentTranslucentData.nativeBufferForQuads(quads);
        }

        var dynamicData = new DynamicData(sectionPos, buffer, range, quads, collector, distancesByNormal);

        dynamicData.sort(cameraPos, false, true);

        return dynamicData;
    }
}
