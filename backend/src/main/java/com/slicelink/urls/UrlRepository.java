package com.slicelink.urls;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UrlRepository extends JpaRepository<Url, Long> {

    /** Returns {@code true} if the given short code already exists. */
    boolean existsByShortCode(String shortCode);

    /** Finds a URL record by its Base62 short code. */
    Optional<Url> findByShortCode(String shortCode);
}
