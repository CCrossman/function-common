package com.crossman.function;

import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Consumer;
import java.util.NoSuchElementException;

/**
 * A functional Result type that represents either a successful value (Success) or a failure (Failure).
 * This sealed interface provides composable error handling patterns with a focus on type safety
 * and functional composition.
 *
 * @param <T> the type of the successful value
 */
public sealed interface Result<T> {
    /**
     * The fundamental fold operation. Applies the appropriate function based on whether
     * this Result contains a success or failure.
     *
     * @param onSuccess function to apply if this is a Success
     * @param onFailure function to apply if this is a Failure
     * @param <X> the return type
     * @return the result of applying either function
     */
    <X> X fold(Function<? super T, ? extends X> onSuccess, Function<? super Throwable, ? extends X> onFailure);

    /**
     * Maps the success value using a function that returns a Result. Failures are propagated.
     * Exceptions thrown by the mapper are caught and converted to Failure.
     *
     * @param mapper function transforming the success value to a new Result
     * @param <U> the type of the new success value
     * @return a new Result
     */
    default <U> Result<U> flatMap(Function<? super T, ? extends Result<U>> mapper) {
        return fold(
            value -> {
                try {
                    return mapper.apply(value);
                } catch (Exception e) {
                    return failure(e);
                }
            },
            Result::failure
        );
    }

    /**
     * Monadic fold operation. Transforms both success and failure paths into Results,
     * then flattens the result. Exceptions in either handler are captured.
     *
     * @param onSuccess function transforming success to Result
     * @param onFailure function transforming failure to Result
     * @param <X> the type of the new success value
     * @return a new Result
     */
    default <X> Result<X> foldM(Function<? super T, ? extends Result<X>> onSuccess, Function<? super Throwable, ? extends Result<X>> onFailure) {
        return fold(
            value -> {
                try {
                    return onSuccess.apply(value);
                } catch (Exception e) {
                    return failure(e);
                }
            },
            error -> {
                try {
                    return onFailure.apply(error);
                } catch (Exception e) {
                    return failure(e);
                }
            }
        );
    }

    /**
     * Extracts the success value or throws NoSuchElementException if this is a Failure.
     * The exception includes the original error as its cause for debugging.
     *
     * @return the success value
     * @throws NoSuchElementException if this is a Failure
     */
    default T get() {
        return fold(
            Function.identity(),
            error -> {
                throw new NoSuchElementException("Result contained failure: " + error.getClass().getSimpleName(), error);
            }
        );
    }

    /**
     * Extracts the success value or returns a default value if this is a Failure.
     * This is the recommended safe alternative to get() when a default is acceptable.
     *
     * @param defaultValue the value to return if this is a Failure
     * @return the success value or defaultValue
     */
    default T getOrElse(T defaultValue) {
        return fold(
            Function.identity(),
            error -> defaultValue
        );
    }

    /**
     * Extracts the success value or throws a custom exception if this is a Failure.
     * Allows mapping the error to a specific exception type.
     *
     * @param exceptionMapper function to convert Throwable to an exception to throw
     * @param <E> the exception type to throw
     * @return the success value
     * @throws E if this is a Failure
     */
    default <E extends Throwable> T getOrThrow(Function<? super Throwable, ? extends E> exceptionMapper) throws E {
        return fold(
            Function.identity(),
            error -> {
                throw exceptionMapper.apply(error);
            }
        );
    }

    /**
     * Maps the success value using a function. Failures are propagated.
     * Exceptions thrown by the mapper are caught and converted to Failure.
     *
     * @param mapper function transforming the success value
     * @param <U> the type of the new success value
     * @return a new Result
     */
    default <U> Result<U> map(Function<? super T, ? extends U> mapper) {
        return fold(
            value -> {
                try {
                    return success(mapper.apply(value));
                } catch (Exception e) {
                    return failure(e);
                }
            },
            Result::failure
        );
    }

    /**
     * Recovers from a Failure by applying a function to the error and returning a success value.
     * The type of the success value is preserved. If the recovery function throws an exception,
     * the Result remains a Failure with the new exception.
     *
     * @param handler function to handle the error and produce a recovery value
     * @return a new Result with recovered value or the original failure
     */
    default Result<T> recover(Function<? super Throwable, ? extends T> handler) {
        return fold(
            Result::success,
            error -> {
                try {
                    return success(handler.apply(error));
                } catch (Exception e) {
                    return failure(e);
                }
            }
        );
    }

    /**
     * Recovers from a Failure by applying a function to the error and returning a new Result.
     * Enables monadic recovery with potential for chaining multiple recovery strategies.
     *
     * @param handler function to handle the error and produce a new Result
     * @return the result of recovery or the original failure
     */
    default Result<T> recoverWith(Function<? super Throwable, ? extends Result<T>> handler) {
        return fold(
            Result::success,
            error -> {
                try {
                    return handler.apply(error);
                } catch (Exception e) {
                    return failure(e);
                }
            }
        );
    }

    /**
     * Executes a side-effect function if this is a Failure, then returns this Result unchanged.
     * Useful for logging errors or cleanup without affecting the Result chain.
     *
     * @param handler consumer to handle the error
     * @return this Result
     */
    default Result<T> onFailure(Consumer<? super Throwable> handler) {
        fold(
            value -> null,
            error -> {
                handler.accept(error);
                return null;
            }
        );
        return this;
    }

    /**
     * Executes a side-effect function if this is a Success, then returns this Result unchanged.
     * Useful for logging success cases or triggering side effects without affecting the Result chain.
     *
     * @param handler consumer to handle the success value
     * @return this Result
     */
    default Result<T> onSuccess(Consumer<? super T> handler) {
        fold(
            value -> {
                handler.accept(value);
                return null;
            },
            error -> null
        );
        return this;
    }

    /**
     * Checks if this Result is a Success.
     *
     * @return true if this is a Success, false otherwise
     */
    default boolean isSuccess() {
        return fold(value -> true, error -> false);
    }

    /**
     * Checks if this Result is a Failure.
     *
     * @return true if this is a Failure, false otherwise
     */
    default boolean isFailure() {
        return fold(value -> false, error -> true);
    }

    /**
     * Filters the success value based on a predicate. If the predicate returns false,
     * returns a Failure with the provided error message wrapped in an exception.
     *
     * @param predicate the condition to check
     * @param errorMessage message for the exception if filter fails
     * @return this Result if success and predicate is true, otherwise a Failure
     */
    default Result<T> filterOrElse(Predicate<? super T> predicate, String errorMessage) {
        return fold(
            value -> predicate.test(value)
                ? success(value)
                : failure(new IllegalArgumentException(errorMessage)),
            Result::failure
        );
    }

    /**
     * Successful result implementation. Immutable record containing a success value.
     */
    record Success<T>(T value) implements Result<T> {
        @Override
        public <X> X fold(Function<? super T, ? extends X> onSuccess, Function<? super Throwable, ? extends X> onFailure) {
            return onSuccess.apply(value);
        }
    }

    /**
     * Failed result implementation. Immutable record containing an error.
     * Guarantees that error is never null through the compact constructor validation.
     */
    record Failure<T>(Throwable error) implements Result<T> {
        /**
         * Compact constructor that validates the error is not null.
         *
         * @throws IllegalArgumentException if error is null
         */
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

    /**
     * Constructs a successful Result with the given value.
     *
     * @param value the success value
     * @param <A> the type of the value
     * @return a Result containing the value
     */
    public static <A> Result<A> success(A value) {
        return new Success<>(value);
    }

    /**
     * Constructs a failed Result with the given error.
     *
     * @param error the failure reason
     * @param <A> the type parameter for the Result
     * @return a Result containing the error
     * @throws IllegalArgumentException if error is null
     */
    public static <A> Result<A> failure(Throwable error) {
        return new Failure<>(error);
    }

    /**
     * Attempts to execute a Callable and wraps the result in a Result.
     * Any checked or unchecked exception is converted to a Failure.
     *
     * @param callable the operation to attempt
     * @param <A> the type of the result value
     * @return a Result containing either the successful value or the exception
     */
    public static <A> Result<A> attempt(Callable<A> callable) {
        try {
            return success(callable.call());
        } catch (Exception e) {
            return failure(e);
        }
    }
}
