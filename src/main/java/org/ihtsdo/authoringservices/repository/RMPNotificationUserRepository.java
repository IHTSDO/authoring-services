package org.ihtsdo.authoringservices.repository;

import org.ihtsdo.authoringservices.entity.RMPNotificationUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RMPNotificationUserRepository extends JpaRepository<RMPNotificationUser, Long> {

    List<RMPNotificationUser> findAllByCountry(String country);

    boolean existsByCountryAndUser(String country, String user);
}

