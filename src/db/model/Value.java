package db.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 Значение ячейки таблицы: тип + сами данные.
 Гарантируется, что тип поля (DataType) всегда соответствует
 одному из классов Java из поля data (INT -> Integer, STRING -> String)
 Именно этот объект сериализуется и хранится в файле.
 */
public class Value implements Serializable {
    private final DataType type;
    private final Object data;

    public Value(DataType type, Object data) {
        this.type = type;
        this.data = data;
    }

    public DataType getType() { return type; }
    public Object getData() { return data; }

    @Override
    public String toString() { return data == null ? "NULL" : data.toString(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Value)) return false;
        Value other = (Value) o;
        return type == other.type && Objects.equals(data, other.data);
    }

    @Override
    public int hashCode() { return Objects.hash(type, data); }
}