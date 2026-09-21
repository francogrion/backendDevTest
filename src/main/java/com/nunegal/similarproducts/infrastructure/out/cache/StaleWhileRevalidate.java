package com.nunegal.similarproducts.infrastructure.out.cache;

import com.github.benmanes.caffeine.cache.CacheLoader;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Caffeine gobierna la frescura del valor, así que el Mono sólo tiene que recordarlo.
 */
record StaleWhileRevalidate<V>(Function<String, Mono<V>> fetch, Duration errorTtl)
        implements CacheLoader<String, Mono<V>> {

    private static final Duration GOVERNED_BY_CAFFEINE = Duration.ofDays(1);

    @Override
    public Mono<V> load(String key) {
        return coalesced(key);
    }

    @Override
    public CompletableFuture<Mono<V>> asyncReload(String key, Mono<V> stale, Executor executor) {
        Mono<V> fresh = coalesced(key);
        return fresh.then(Mono.just(fresh)).toFuture();
    }

    private Mono<V> coalesced(String key) {
        return fetch.apply(key)
                .cache(value -> GOVERNED_BY_CAFFEINE, error -> errorTtl, () -> errorTtl);
    }
}
