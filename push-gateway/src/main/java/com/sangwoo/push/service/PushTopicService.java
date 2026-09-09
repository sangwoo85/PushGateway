package com.sangwoo.push.service;

import com.sangwoo.push.domain.PushTargetType;
import com.sangwoo.push.repository.PushTopicBindingRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class PushTopicService {
    public static final String NOTICE_TARGET_ID = "ALL";
    private final PushTopicBindingRepository repository;
    private final SecureRandom random;

    @Autowired
    public PushTopicService(PushTopicBindingRepository repository) {
        this(repository, new SecureRandom());
    }

    PushTopicService(PushTopicBindingRepository repository, SecureRandom random) {
        this.repository = repository;
        this.random = random;
    }

    public Optional<String> resolve(PushTargetType type, String targetId) {
        return repository.findEnabled(type, normalizedTarget(type, targetId));
    }

    @Transactional
    public String getOrCreate(PushTargetType type, String targetId) {
        String normalized = normalizedTarget(type, targetId);
        Optional<String> current = repository.findEnabled(type, normalized);
        if (current.isPresent()) return current.get();
        String prefix = type == PushTargetType.USER ? "usr_" : "dept_";
        for (int attempt = 0; attempt < 4; attempt++) {
            String topic;
            if (type == PushTargetType.NOTICE) {
                topic = "notice_all";
            } else {
                byte[] bytes = new byte[24];
                random.nextBytes(bytes);
                topic = prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            }
            try {
                repository.insert(type, normalized, topic);
                return topic;
            } catch (DuplicateKeyException collision) {
                Optional<String> winner = repository.findEnabledForUpdate(type, normalized);
                if (winner.isPresent()) return winner.get();
            }
        }
        throw new IllegalStateException("Unable to allocate push topic");
    }

    public static String normalizedTarget(PushTargetType type, String targetId) {
        if (type == null) throw new IllegalArgumentException("targetType is required");
        if (type == PushTargetType.NOTICE) return NOTICE_TARGET_ID;
        if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException("targetId is required");
        String value = targetId.trim();
        if (value.length() > 128) throw new IllegalArgumentException("targetId is too long");
        return value;
    }
}
