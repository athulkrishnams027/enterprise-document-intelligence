package com.athul.documentintelligence.mapper;

import com.athul.documentintelligence.dto.response.UserProfileResponse;
import com.athul.documentintelligence.entity.Role;
import com.athul.documentintelligence.entity.User;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserMapper {

    public UserProfileResponse toProfileResponse(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toUnmodifiableSet());
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                roleNames,
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}
