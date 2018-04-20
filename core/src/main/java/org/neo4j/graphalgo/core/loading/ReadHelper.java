package org.neo4j.graphalgo.core.loading;

import org.neo4j.internal.kernel.api.CursorFactory;
import org.neo4j.internal.kernel.api.NodeCursor;
import org.neo4j.internal.kernel.api.NodeLabelIndexCursor;
import org.neo4j.internal.kernel.api.PropertyCursor;
import org.neo4j.internal.kernel.api.Read;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.values.storable.FloatingPointValue;
import org.neo4j.values.storable.IntegralValue;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.ValueGroup;

import java.util.function.LongConsumer;

public final class ReadHelper {
    private ReadHelper() {
        throw new UnsupportedOperationException("No instances");
    }

    public static double readProperty(PropertyCursor pc, int propertyId, double defaultValue) {
        while (pc.next()) {
            if (pc.propertyKey() == propertyId) {
                Value value = pc.propertyValue();
                return extractValue(value, defaultValue);
            }
        }
        return defaultValue;
    }

    public static void readNodes(KernelTransaction transaction, int labelId, LongConsumer action) {
        readNodes(transaction.cursors(), transaction.dataRead(), labelId, action);
    }

    public static void readNodes(CursorFactory cursors, Read dataRead, int labelId, LongConsumer action) {
        if (labelId == Read.ANY_LABEL) {
            try (NodeCursor nodeCursor = cursors.allocateNodeCursor()) {
                dataRead.allNodesScan(nodeCursor);
                while (nodeCursor.next()) {
                    action.accept(nodeCursor.nodeReference());
                }
            }
        } else {
            try (NodeLabelIndexCursor nodeCursor = cursors.allocateNodeLabelIndexCursor()) {
                dataRead.nodeLabelScan(labelId, nodeCursor);
                while (nodeCursor.next()) {
                    action.accept(nodeCursor.nodeReference());
                }
            }
        }
    }

    public static double extractValue(Value value, double defaultValue) {
        // slightly different logic than org.neo4j.values.storable.Values#coerceToDouble
        // b/c we want to fallback to the default weight if the value is empty
        if (value instanceof FloatingPointValue) {
            return ((FloatingPointValue) value).doubleValue();
        } else if (value instanceof IntegralValue) {
            return (double) ((IntegralValue) value).longValue();
        } else if (value.valueGroup() == ValueGroup.NO_VALUE) {
            return defaultValue;
        } else {
            // TODO: We used to do be lenient and parse strings/booleans into doubles.
            //       Do we want to do so or is failing on non numeric properties ok?
            throw new IllegalArgumentException(String.format(
                    "Unsupported type [%s] of value %s. Please use a numeric property.",
                    value.valueGroup(),
                    value));
        }
    }
}
