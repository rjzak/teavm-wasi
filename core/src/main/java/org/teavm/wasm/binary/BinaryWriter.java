/*
 *  Copyright 2016 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.wasm.binary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BinaryWriter {
    private int address;
    private List<DataValue> values = new ArrayList<>();

    public BinaryWriter(int start) {
        this.address = start;
    }

    public int append(DataValue value) {
        int result = align(address, getAlignment(value.getType()));
        values.add(value);
        address = offset(value.getType(), result);
        return result;
    }

    public int getAddress() {
        return address;
    }

    private int offset(DataType type, int index) {
        if (type == DataPrimitives.BYTE) {
            return index + 1;
        } else if (type == DataPrimitives.SHORT) {
            return align(index, 2) + 2;
        } else if (type == DataPrimitives.INT) {
            return align(index, 4) + 4;
        } else if (type == DataPrimitives.LONG) {
            return align(index, 8) + 8;
        } else if (type == DataPrimitives.FLOAT) {
            return align(index, 4) + 4;
        } else if (type == DataPrimitives.DOUBLE) {
            return align(index, 8) + 8;
        } else if (type == DataPrimitives.ADDRESS) {
            return align(index, 4) + 4;
        } else if (type instanceof DataArray) {
            DataArray array = (DataArray) type;
            int next = offset(array.getComponentType(), index);
            return index + (next - index) * array.getSize();
        } else if (type instanceof DataStructure) {
            DataStructure structure = (DataStructure) type;
            if (structure.getAlignment() > 0) {
                index = align(index, structure.getAlignment());
            }
            DataType[] components = structure.getComponents();
            for (DataType component : components) {
                index = offset(component, index);
            }
            return index;
        } else {
            return index;
        }
    }

    private static int align(int address, int alignment) {
        if (address == 0) {
            return 0;
        }
        return ((address - 1) / alignment + 1) * alignment;
    }

    private int getAlignment(DataType type) {
        if (type == DataPrimitives.BYTE) {
            return 1;
        } else if (type == DataPrimitives.SHORT) {
            return 2;
        } else if (type == DataPrimitives.INT) {
            return 4;
        } else if (type == DataPrimitives.LONG) {
            return 8;
        } else if (type == DataPrimitives.FLOAT) {
            return 4;
        } else if (type == DataPrimitives.DOUBLE) {
            return 8;
        } else if (type == DataPrimitives.ADDRESS) {
            return 4;
        } else if (type instanceof DataArray) {
            return getAlignment(((DataArray) type).getComponentType());
        } else if (type instanceof DataStructure) {
            DataStructure structure = (DataStructure) type;
            return Math.max(structure.getAlignment(), getAlignment(structure.getComponents()[0]));
        } else {
            return 1;
        }
    }

    public byte[] getData() {
        byte[] result = new byte[address];
        int offset = 0;
        for (DataValue value : values) {
            offset = writeData(result, offset, value);
        }
        return Arrays.copyOf(result, offset);
    }

    private int writeData(byte[] result, int offset, DataValue value) {
        DataType type = value.getType();
        if (type == DataPrimitives.BYTE) {
            result[offset++] = value.getByte(0);
        } else if (type == DataPrimitives.SHORT) {
            offset = align(offset, 2);
            short v = value.getShort(0);
            result[offset++] = (byte) (v >> 8);
            result[offset++] = (byte) v;
        } else if (type == DataPrimitives.INT) {
            offset = writeInt(offset, result, value.getInt(0));
        } else if (type == DataPrimitives.LONG) {
            offset = writeLong(offset, result, value.getLong(0));
        } else if (type == DataPrimitives.FLOAT) {
            int v = Float.floatToRawIntBits(value.getFloat(0));
            offset = writeInt(offset, result, v);
        } else if (type == DataPrimitives.DOUBLE) {
            long v = Double.doubleToRawLongBits(value.getDouble(0));
            offset = writeLong(offset, result, v);
        } else if (type == DataPrimitives.ADDRESS) {
            offset = writeInt(offset, result, (int) value.getAddress(0));
        } else if (type instanceof DataArray) {
            DataArray array = (DataArray) type;
            for (int i = 0; i < array.getSize(); ++i) {
                offset = writeData(result, offset, value.getValue(i));
            }
        } else if (type instanceof DataStructure) {
            DataStructure structure = (DataStructure) type;
            if (structure.getAlignment() > 0) {
                offset = align(offset, structure.getAlignment());
            }
            int componentCount = structure.getComponents().length;
            for (int i = 0; i < componentCount; ++i) {
                offset = writeData(result, offset, value.getValue(i));
            }
        }
        return offset;
    }

    private int writeInt(int offset, byte[] result, int v) {
        offset = align(offset, 4);
        result[offset++] = (byte) (v >> 24);
        result[offset++] = (byte) (v >> 16);
        result[offset++] = (byte) (v >> 8);
        result[offset++] = (byte) v;
        return offset;
    }

    private int writeLong(int offset, byte[] result, long v) {
        offset = align(offset, 8);
        result[offset++] = (byte) (v >> 56);
        result[offset++] = (byte) (v >> 48);
        result[offset++] = (byte) (v >> 40);
        result[offset++] = (byte) (v >> 32);
        result[offset++] = (byte) (v >> 24);
        result[offset++] = (byte) (v >> 16);
        result[offset++] = (byte) (v >> 8);
        result[offset++] = (byte) v;
        return offset;
    }

    public byte[] getMetadata() {
        MetadataWriter writer = new MetadataWriter();
        writer.write(values.stream().map(value -> value.getType()).toArray(DataType[]::new));
        return writer.getData();
    }
}
