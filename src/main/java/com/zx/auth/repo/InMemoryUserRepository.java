package com.zx.auth.repo;

import com.zx.auth.model.User;
import com.zx.auth.service.AuthService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryUserRepository {
    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    @PostConstruct
    public void init() {
        // create demo admin with username=admin and password=admin (hashed)
        String hash = AuthService.sha256Hex("admin");
        User admin = new User(seq.getAndIncrement(), "admin", hash);
        users.put(admin.getId(), admin);
    }

    public Optional<User> findByUsername(String username) {
        return users.values().stream().filter(u -> u.getUsername().equals(username)).findFirst();
    }

    public User save(User user) {
        if (user.getId() == null) user.setId(seq.getAndIncrement());
        users.put(user.getId(), user);
        return user;
    }
}

