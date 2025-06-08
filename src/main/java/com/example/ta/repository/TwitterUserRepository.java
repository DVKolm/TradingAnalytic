package com.example.ta.repository;

import com.example.ta.domain.news.TwitterUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TwitterUserRepository extends JpaRepository<TwitterUser, Long> {
    Optional<TwitterUser> findByUsername(String username);
    Optional<TwitterUser> findByUserId(String userId);
    List<TwitterUser> findByIsActiveTrue();
    
    @Query("SELECT COUNT(tu) FROM TwitterUser tu WHERE tu.isActive = true")
    long countActiveUsers();
}