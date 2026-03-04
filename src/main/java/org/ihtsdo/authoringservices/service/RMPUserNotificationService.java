package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.entity.RMPNotificationUser;
import org.ihtsdo.authoringservices.repository.RMPNotificationUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
public class RMPUserNotificationService {

    private final RMPNotificationUserRepository rmpNotificationUserRepository;

    public RMPUserNotificationService(RMPNotificationUserRepository rmpNotificationUserRepository) {
        this.rmpNotificationUserRepository = rmpNotificationUserRepository;
    }

    public List<RMPNotificationUser> getNotificationUsers(String country) {
        if (StringUtils.hasLength(country)) {
            return rmpNotificationUserRepository.findAllByCountry(country);
        }
        return rmpNotificationUserRepository.findAll();
    }

    public Optional<RMPNotificationUser> getNotificationUserById(long id) {
        return rmpNotificationUserRepository.findById(id);
    }

    public boolean existsByCountryAndUser(String country, String user) {
        return rmpNotificationUserRepository.existsByCountryAndUser(country, user);
    }

    public RMPNotificationUser createNotificationUser(RMPNotificationUser notification) {
        return rmpNotificationUserRepository.save(notification);
    }

    public boolean deleteNotificationUser(long id) {
        if (rmpNotificationUserRepository.existsById(id)) {
            rmpNotificationUserRepository.deleteById(id);
            return true;
        }
        return false;
    }
}

