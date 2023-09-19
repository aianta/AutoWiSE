package ca.ualberta.autowise.utils;

import java.util.function.Function;

//https://dzone.com/articles/exception-handling-in-java-streams
@FunctionalInterface
public interface CheckedFunction<T,R> {
    R apply(T t) throws Exception;

    static <T,R> Function<T,R> wrap(CheckedFunction<T,R> checkedFunction) {
        return t -> {
            try {
                return checkedFunction.apply(t);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
}