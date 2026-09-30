package com.medflow.shared.exception;

/** Raised when a concurrent update conflict occurs (HTTP 409). */
public class OptimisticLockException extends RuntimeException {
  public OptimisticLockException(String message) {
    super(message);
  }
}
