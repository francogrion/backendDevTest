package com.nunegal.similarproducts.infrastructure.in.web;

import com.nunegal.similarproducts.domain.ProductNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    ResponseEntity<Void> handleProductNotFound(ProductNotFoundException exception) {
        return ResponseEntity.notFound().build();
    }
}
