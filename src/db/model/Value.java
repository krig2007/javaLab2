package db.model;

import java.io.Serializable;
import java.time.LocalDate;

public class Value implements Serializable {
    private final DataType type;
    private final Object data;

    public Value(DataType type, Object data) {
        this.type = type;
        this.data = data;
    }

    public DataType getType() {
        return type;
    }

    public Object getData() {
        return data;
    }

    public int asInt() {
        return (Integer) data;
    }

    public double asDouble() {
        return (Double) data;
    }

    public String asString() {
        return (String) data;
    }

    public LocalDate asDate() {
        return (LocalDate) data;
    }

    public boolean asBoolean() {
        return (Boolean) data;
    }

    @Override
    public String toString() {
        return data.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Value other = (Value) o;
        return type == other.type && data.equals(other.data);
    }

    @Override
    public int hashCode() {
        return data.hashCode();
    }
}
