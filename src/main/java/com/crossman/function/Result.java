package com.crossman.function;

import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.NoSuchElementException;

public sealed interface Result<T> {
    <X> X fold(Function<? super T, ? extends X> onSuccess, Function<? super Throwable, ? extends X> onFailure);

    default <U> Result<U> flatMap(Function<? super T, ? extends Result<U>> mapper) {
        return fold(
            value -> {
                try {
                    return mapper.apply(value);
                } catch (Throwable e) {
                    return failure(e);
                }
            },
            Result::failure
        );
    }

    default <X> Result<X> foldM(Function<? super T, ? extends Result<X>> onSuccess, Function<? super Throwable, ? extends Result<X>> onFailure) {
        return fold(
            value -> {
                try {
                    return onSuccess.apply(value);
                } catch (Throwable e) {
                    return failure(e);
                }
            },
            error -> {
                try {
                    return onFailure.apply(error);
                } catch (Throwable e) {
                    return failure(e);
                }
            }
        );
    }

    default T get() {
        return fold(
            Function.identity(),
            error -> {
                throw new NoSuchElementException("Failed to get value", error);
            }
        );
    }

    default <U> Result<U> map(Function<? super T, ? extends U> mapper) {
        return fold(
            value -> {
                try {
                    return success(mapper.apply(value));
                } catch (Throwable e) {
                    return failure(e);
                }
            },
            Result::failure
        );
    }

    record Success<T>(T value) implements Result<T> {
        @Override
        public <X> X fold(Function<? super T, ? extends X> onSuccess, Function<? super Throwable, ? extends X> onFailure) {
            return onSuccess.apply(value);
        }
    }

    record Failure<T>(Throwable error) implements Result<T> {
        public Failure {
            if (error == null) {
                throw new IllegalArgumentException("Error cannot be null");
            }
        }

        @Override
        public <X> X fold(Function<? super T, ? extends X> onSuccess, Function<? super Throwable, ? extends X> onFailure) {
            return onFailure.apply(error);
        }
    }

    public static <A> Result<A> success(A value) {
        return new Success<>(value);
    }

    public static <A> Result<A> failure(Throwable error) {
        return new Failure<>(error);
    }

    public static <A> Result<A> attempt(Callable<A> callable) {
        try {
            return success(callable.call());
        } catch (Throwable e) {
            return failure(e);
        }
    }
}