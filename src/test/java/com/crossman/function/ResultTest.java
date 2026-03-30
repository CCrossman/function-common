package com.crossman.function;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class ResultTest {

    @Test
    void helloReturnsExpectedMessage() {
        String hello = Result.success("Hello, World!").get();
        assertEquals("Hello, World!", hello);
    }

    @Test
    void successFoldUsesOnSuccessPath() {
        Result<Integer> result = Result.success(3);
        String folded = result.fold(
            value -> "value=" + value,
            error -> "error"
        );

        assertEquals("value=3", folded);
    }

    @Test
    void failureFoldUsesOnFailurePath() {
        IllegalStateException failureCause = new IllegalStateException("boom");
        Result<String> result = Result.failure(failureCause);
        String folded = result.fold(
            value -> "value=" + value,
            error -> "error=" + error.getMessage()
        );

        assertEquals("error=boom", folded);
    }

    @Test
    void getThrowsWhenResultIsFailure() {
        IllegalStateException failureCause = new IllegalStateException("boom");
        Result<String> result = Result.failure(failureCause);

        NoSuchElementException thrown = assertThrows(NoSuchElementException.class, result::get);
        assertSame(failureCause, thrown.getCause());
        assertEquals("Failed to get value", thrown.getMessage());
    }

    @Test
    void foldMSuccessReturnsMappedSuccess() {
        Result<Integer> result = Result.success(7);

        Result<String> mapped = result.foldM(
            value -> Result.success("value=" + value),
            error -> Result.success("error")
        );

        assertEquals("value=7", mapped.get());
    }

    @Test
    void foldMFailureReturnsMappedFailure() {
        IllegalArgumentException cause = new IllegalArgumentException("oops");
        Result<Integer> result = Result.failure(cause);

        Result<String> mapped = result.foldM(
            value -> Result.success("value=" + value),
            error -> Result.success("error=" + error.getMessage())
        );

        assertEquals("error=oops", mapped.get());
    }

    @Test
    void foldMHandlesExceptionsInOnSuccess() {
        Result<Integer> result = Result.success(5);

        Result<String> mapped = result.foldM(
            value -> {
                throw new IllegalStateException("failed to map");
            },
            error -> Result.success("error")
        );

        NoSuchElementException thrown = assertThrows(NoSuchElementException.class, mapped::get);
        assertEquals("Failed to get value", thrown.getMessage());
        assertSame(IllegalStateException.class, thrown.getCause().getClass());
        assertEquals("failed to map", thrown.getCause().getMessage());
    }

    @Test
    void attemptReturnsSuccessWhenCallableDoesNotThrow() {
        Callable<String> callable = () -> "ok";
        Result<String> result = Result.attempt(callable);

        assertEquals("ok", result.get());
    }

    @Test
    void attemptReturnsFailureWhenCallableThrows() {
        Callable<String> callable = () -> {
            throw new IllegalStateException("boom");
        };
        Result<String> result = Result.attempt(callable);

        NoSuchElementException thrown = assertThrows(NoSuchElementException.class, result::get);
        assertSame(IllegalStateException.class, thrown.getCause().getClass());
        assertEquals("boom", thrown.getCause().getMessage());
    }

    @Test
    void failureFactoryRejectsNullError() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> Result.failure(null));
        assertEquals("Error cannot be null", thrown.getMessage());
    }

    @Test
    void flatMapStringToSuccess() {
        var strResult = Result.success("hello");
        var succLenghResult = strResult.flatMap(s -> {
            return Result.success(s.length());
        });
        assertEquals(5, succLenghResult.get());
    }

    @Test
    void flatMapStringToFailure() {
        var strResult = Result.success("hello");
        var failureResult = strResult.flatMap(s -> {
            return Result.failure(new IllegalArgumentException("oops"));
        });
        
        if (failureResult instanceof Result.Failure failure) {
            assertEquals("oops", failure.error().getMessage());
        } else {
            fail("Expected a Failure result");
        }
    }

    @Test
    void mapStringToLength() {
        var strResult = Result.success("hello");
        var lengthResult = strResult.map(String::length);
        assertEquals(5, lengthResult.get());
    }

    @Test
    void mapFailureDoesNotApplyFunction() {
        var failureResult = Result.<String>failure(new IllegalArgumentException("oops"));
        var mappedResult = failureResult.map(String::length);
        if (mappedResult instanceof Result.Failure failure) {
            assertEquals("oops", failure.error().getMessage());
        } else {
            fail("Expected a Failure result");
        }
    }
}
