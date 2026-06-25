package com.mcp.tools.security;

import com.mcp.common.identity.CommandLevel;
import com.mcp.common.identity.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 权限校验切面 - 拦截所有 @RequirePermission 注解的方法
 * 程序负责权限，模型负责聊天
 */
@Slf4j
@Aspect
@Component
public class PermissionAspect {

    @Around("@annotation(com.mcp.tools.security.RequirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RequirePermission annotation = signature.getMethod().getAnnotation(RequirePermission.class);
        CommandLevel requiredLevel = annotation.value();

        UserProfile userProfile = Arrays.stream(joinPoint.getArgs())
                .filter(arg -> arg instanceof UserProfile)
                .map(arg -> (UserProfile) arg)
                .findFirst()
                .orElse(null);
        if (userProfile == null) {
            log.warn("[Permission] No UserProfile in context, denying access to: {}",
                    signature.getMethod().getName());
            throw new PermissionDeniedException("unknown", requiredLevel.name());
        }

        CommandLevel userLevel = mapRoleToCommandLevel(userProfile);

        if (!userLevel.canExecute(requiredLevel)) {
            log.warn("[Permission] Denied: User {} ({}) tried to access {} (requires {})",
                    userProfile.getUserId(), userLevel,
                    signature.getMethod().getName(), requiredLevel);
            throw new PermissionDeniedException(
                    userProfile.getUserId(), requiredLevel.name());
        }

        log.info("[Permission] Allowed: User {} ({}) accessing {}",
                userProfile.getUserId(), userLevel,
                signature.getMethod().getName());

        return joinPoint.proceed();
    }

    private CommandLevel mapRoleToCommandLevel(UserProfile profile) {
        return switch (profile.getRole()) {
            case OWNER -> CommandLevel.OWNER;
            case ADMIN -> CommandLevel.ADMIN;
            case MEMBER -> CommandLevel.USER;
        };
    }
}