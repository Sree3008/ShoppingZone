package com.eshoppingzone.profile.config;

import com.eshoppingzone.profile.entity.UserProfile;
import com.eshoppingzone.profile.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserProfileRepository userProfileRepository;

    public DataInitializer(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @Override
    public void run(String... args) {
        seedProfile(1L, "admin", "admin@eshoppingzone.com", "System Administrator", "9999999991");
        seedProfile(2L, "merchant1", "merchant1@eshoppingzone.com", "Primary Merchant", "9999999992");
        seedProfile(3L, "delivery1", "delivery1@eshoppingzone.com", "Primary Delivery Agent", "9999999993");
        seedProfile(4L, "customer1", "customer1@eshoppingzone.com", "John Customer", "9999999994");
    }

    private void seedProfile(Long userId, String username, String email, String fullName, String phone) {
        if (!userProfileRepository.existsByUserId(userId)) {
            UserProfile profile = new UserProfile();
            profile.setUserId(userId);
            profile.setUsername(username);
            profile.setEmail(email);
            profile.setFullName(fullName);
            profile.setPhoneNumber(phone);
            userProfileRepository.save(profile);
            log.info("Seeded initial profile for user: {} (userId: {})", username, userId);
        }
    }
}
