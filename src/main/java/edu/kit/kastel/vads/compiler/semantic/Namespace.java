package edu.kit.kastel.vads.compiler.semantic;

import edu.kit.kastel.vads.compiler.parser.ast.NameTree;
import edu.kit.kastel.vads.compiler.parser.symbol.Name;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BinaryOperator;

public class Namespace<T> {
    //当前block的变量放在content
    //外层block的变量放在parent
    private final Map<Name, T> content;
    private final @Nullable Namespace<T> parent;

    public Namespace() {
        this.content = new HashMap<>();
        this.parent = null;
    }

    public Namespace(Namespace<T> parent) {
        this.content = new HashMap<>();
        this.parent = parent;
    }

    public void put(NameTree name, T value, BinaryOperator<T> merger) {
        this.content.merge(name.name(), value, merger);
    }

    //逐层向外查找
    public @Nullable T get(NameTree name) {
        T value = this.content.get(name.name());
        if (value == null && parent != null) {
            return parent.get(name);
        }
        return value;
    }
    //进入一个新的嵌套作用域
    public Namespace<T> enter() {
        return new Namespace<>(this);
    }

    //java.util.Map.getOrDefault
    public T getOrDefault(NameTree name, T defaultVal) {
        T v = get(name);
        return v == null ? defaultVal : v;
    }
    //将当前作用域所有变量一键设为指定值
    public void setAll(T value) {
        content.replaceAll((k, v) -> value);
    }
    
}
