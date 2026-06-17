package com.sprint.mission.discodeit.security.jwt;

import com.sprint.mission.discodeit.dto.user.UserDto;
import com.sprint.mission.discodeit.mapper.UserMapper;
import com.sprint.mission.discodeit.repository.UserRepository;
import com.sprint.mission.discodeit.service.basic.SseService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class JwtLogoutHandler implements LogoutHandler {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "REFRESH_TOKEN";
    private static final String USER_UPDATED_EVENT = "users.updated";

    private final JwtRegistry jwtRegistry;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final SseService sseService;

    @Override
    @CacheEvict(value = "users", allEntries = true)
    public void logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        Cookie[] cookies = request.getCookies();

        if (cookies != null) {
            Arrays.stream(cookies)
                    .filter(cookie -> REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName()))
                    .findFirst()
                    .ifPresent(cookie -> logout(cookie.getValue()));
        }

        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);

        response.addCookie(cookie);
    }

    private void logout(String refreshToken) {
        UserDto userDto = findLogoutUserDto(refreshToken);

        jwtRegistry.invalidateJwtInformationByRefreshToken(refreshToken);

        if (userDto != null) {
            sseService.broadcast(USER_UPDATED_EVENT, userDto);
        }
    }

    private UserDto findLogoutUserDto(String refreshToken) {
        if (!jwtTokenProvider.validationToken(refreshToken)
                || !jwtRegistry.hasActiveJwtInformationByRefreshToken(refreshToken)) {
            return null;
        }

        String username = jwtTokenProvider.getSubject(refreshToken);

        return userRepository.findByUsername(username)
                .map(user -> userMapper.toDto(user, false))
                .orElse(null);
    }
}
