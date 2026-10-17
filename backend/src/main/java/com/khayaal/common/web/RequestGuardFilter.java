package com.khayaal.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestGuardFilter extends OncePerRequestFilter {
    private final SensitiveRequestRateLimiter rateLimiter;

    public RequestGuardFilter(SensitiveRequestRateLimiter rateLimiter) { this.rateLimiter = rateLimiter; }

    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String requestId=validRequestId(request.getHeader("X-Request-ID"));
        response.setHeader("X-Request-ID",requestId);
        try(MDC.MDCCloseable ignored=MDC.putCloseable("requestId",requestId)){
            if(isSensitiveWrite(request)&&!rateLimiter.allow(request.getRemoteAddr()+":"+request.getRequestURI())){
                response.setStatus(429);response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.getWriter().write("{\"title\":\"Too many requests\",\"status\":429,\"detail\":\"Please wait before trying again.\"}");
                return;
            }
            chain.doFilter(request,response);
        }
    }

    private boolean isSensitiveWrite(HttpServletRequest request){
        if(!"POST".equals(request.getMethod()))return false;
        String path=request.getRequestURI();
        return path.startsWith("/api/auth/")||path.matches("/api/households/[^/]+/invites");
    }

    private String validRequestId(String candidate){
        if(candidate!=null&&candidate.matches("[A-Za-z0-9._-]{1,80}"))return candidate;
        return UUID.randomUUID().toString();
    }
}
