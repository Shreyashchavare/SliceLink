package com.slicelink.urls;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UrlRepository extends JpaRepository<Url, Long> {

    /** Returns {@code true} if the given short code already exists. */
    boolean existsByShortCode(String shortCode);
}
