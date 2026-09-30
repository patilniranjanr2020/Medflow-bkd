package com.medflow.modules.settings.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.medflow.modules.settings.api.response.ProfileResponse;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Caches active patient registration profiles per hospital.
 * Uses Caffeine with 5-minute TTL.
 */
@Component
class ProfileCacheManager {

  private static final long CACHE_TTL_MINUTES = 5;
  private final Cache<Long, ProfileResponse> cache;

  ProfileCacheManager() {
    this.cache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(CACHE_TTL_MINUTES, TimeUnit.MINUTES)
        .build();
  }

  Optional<ProfileResponse> get(Long hospitalId) {
    return Optional.ofNullable(cache.getIfPresent(hospitalId));
  }

  void put(Long hospitalId, ProfileResponse profile) {
    cache.put(hospitalId, profile);
  }

  void invalidate(Long hospitalId) {
    cache.invalidate(hospitalId);
  }

  void invalidateAll() {
    cache.invalidateAll();
  }
}
