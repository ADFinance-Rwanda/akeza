package com.tenant.management.repository;

import com.tenant.management.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            select distinct u from User u
            join u.memberships m
            where m.tenant.id in (
                select m2.tenant.id from Membership m2 where m2.user.id = :userId
            )
            """)
    List<User> findUsersSharingTenantsWith(@Param("userId") Long userId);
}
