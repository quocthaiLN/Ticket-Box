package com.ticketbox.api.infrastructure.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.springframework.http.MediaType;

public final class AdminProblemWriter {
    private AdminProblemWriter() {}
    public static boolean write(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
                                int status, String code, String detail) throws IOException {
        if (!request.getRequestURI().substring(request.getContextPath().length()).startsWith("/admin/")) return false;
        LinkedHashMap<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "https://api.ticketbox.vn/errors/" + code.toLowerCase().replace('_', '-'));
        problem.put("title", code);
        problem.put("status", status);
        problem.put("code", code);
        problem.put("detail", detail);
        problem.put("instance", request.getRequestURI());
        problem.put("request_id", "req_" + UUID.randomUUID().toString().substring(0, 8));
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), problem);
        return true;
    }
}
